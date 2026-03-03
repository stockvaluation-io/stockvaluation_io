package io.stockvaluation.service;

import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.dto.GrowthDto;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.GaussianRandomGenerator;
import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.special.Erf;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;

public class GrowthCalculatorService {

    private static final Random rand = new SecureRandom();

    public static Double generateRevenueGrowth(double baseGrowth, double stdDev) {
        return rand.nextGaussian() * stdDev + baseGrowth;
    }

    public static Double generateOperatingMargin(double baseMargin, double volatility) {
        double margin = rand.nextGaussian() * volatility + baseMargin;
        return Math.max(margin, 0); // Prevent negative margins
    }

    public static double generateOperatingMargin(double min, double mode, double max) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double F = (mode - min) / (max - min);
        double U = rand.nextDouble();

        return (U <= F)
                ? min + Math.sqrt(U * (max - min) * (mode - min))
                : max - Math.sqrt((1 - U) * (max - min) * (max - mode));
    }

    /*
    public static double[] generateCorrelatedVariables(double meanRev, double stdRev,
                                                       double minMargin, double modeMargin,
                                                       double maxMargin, double correlation) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        // Generate correlated normals using Cholesky
        double[] normals = {rand.nextGaussian(), rand.nextGaussian()};
        double[][] chol = {{1, 0}, {correlation, Math.sqrt(1 - correlation*correlation)}};

        double revZ = chol[0][0] * normals[0];
        double marginZ = chol[1][0] * normals[0] + chol[1][1] * normals[1];

        // Convert marginZ to triangular distribution
        double uniform = Phi(marginZ); // Convert to [0,1] using normal CDF
        double margin = triangularFromUniform(uniform, minMargin, modeMargin, maxMargin);

        return new double[] {
                clamp(meanRev + revZ * stdRev, -0.5, 2.0),  // Revenue growth
                clamp(margin, 0.01, 0.60)                   // Operating margin
        };
    }  */

    public static double[] generateCorrelatedVariables(
            double revenueMu,
            double revenueStdDev,
            double marginMu,
            double marginStdDev,
            double correlation
    ) {
        // Validate input parameters upfront
        if (revenueStdDev < 0) {
            throw new IllegalArgumentException("Revenue standard deviation must be non-negative. Received: " + revenueStdDev);
        }

        final double gamma = -1.0;
        final double adjustedMean = revenueMu - gamma;

        if (adjustedMean <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Invalid revenueMu (%.4f) for gamma=%.2f. Revenue mean must exceed gamma by >0.",
                    revenueMu, gamma
            ));
        }

        GaussianRandomGenerator rng = new GaussianRandomGenerator(new MersenneTwister());

        // Generate correlated normals using Cholesky decomposition
        double z1 = rng.nextNormalizedDouble();
        double z2 = correlation * z1 + Math.sqrt(1 - correlation*correlation) * rng.nextNormalizedDouble();

        // 1. Numerically stable lognormal parameter calculation
        double relativeVariance = Math.pow(revenueStdDev/adjustedMean, 2);
        double sigmaLogSq = Math.log1p(relativeVariance);
        double sigmaLog = Math.sqrt(sigmaLogSq);
        double muLog = Math.log(adjustedMean) - sigmaLogSq/2;

        // Final validation of computed parameters
        if (Double.isNaN(muLog) || Double.isNaN(sigmaLog)) {
            throw new ArithmeticException(String.format(
                    "Numerical instability in lognormal params. mu=%.4f, sigma=%.4f, gamma=%.2f",
                    revenueMu, revenueStdDev, gamma
            ));
        }

        double revenueGrowth = gamma + Math.exp(muLog + sigmaLog*z1);

        // 2. Beta distribution with improved parameter handling
        double mu = marginMu;
        double var = Math.pow(marginStdDev, 2);

        if (var <= 0) {
            throw new IllegalArgumentException("Margin variance must be positive. Received: " + var);
        }

        double t = (mu*(1 - mu)/var) - 1;
        double alpha = mu * t;
        double beta = (1 - mu) * t;

        // Handle edge cases for beta parameters
        if (alpha <= 0 || beta <= 0) {
            alpha = Math.max(mu * 100, 1e-4);
            beta = Math.max((1 - mu) * 100, 1e-4);
        }

        BetaDistribution betaDist = new BetaDistribution(alpha, beta);
        double margin = betaDist.inverseCumulativeProbability(
                new NormalDistribution().cumulativeProbability(z2)
        );

        return new double[] {
                revenueGrowth * 100,  // Convert to percentage
                margin * 100          // Convert to percentage
        };
    }


    // Helper method to calculate distribution parameters
    public static Map<String, Double> calculateLogNormalParams(double mu, double sigma, double gamma) {
        Map<String, Double> params = new HashMap<>();

        // 1. Input validation
        if (sigma <= 0) {
            throw new IllegalArgumentException("Sigma must be positive for lognormal distribution. Received: " + sigma);
        }

        final double adjustedMean = mu - gamma;
        if (adjustedMean <= 0) {
            throw new IllegalArgumentException(
                    String.format("Invalid mean-gamma combination: μ=%.4f, γ=%.4f. μ must exceed γ by >0.", mu, gamma)
            );
        }

        // 2. Numerically stable calculation
        final double relativeVariance = Math.pow(sigma/adjustedMean, 2);
        final double epsilon = 1e-8; // Minimum threshold for valid variance

        if (relativeVariance < epsilon) {
            // Handle near-zero variance case
            params.put("muLog", Math.log(adjustedMean));
            params.put("sigmaLog", Math.sqrt(relativeVariance));
        } else {
            final double sigmaLogSq = Math.log1p(relativeVariance);
            params.put("muLog", Math.log(adjustedMean) - sigmaLogSq/2);
            params.put("sigmaLog", Math.sqrt(sigmaLogSq));
        }

        params.put("gamma", gamma);

        // 3. Post-calculation validation
        if (Double.isNaN(params.get("muLog")) || Double.isNaN(params.get("sigmaLog"))) {
            throw new ArithmeticException(
                    String.format("Numerical instability detected in lognormal params. μ=%.4f, σ=%.4f, γ=%.4f",
                            mu, sigma, gamma)
            );
        }

        return params;
    }

    // Helper methods
    private static double Phi(double z) {
        return 0.5 * (1.0 + Erf.erf(z / Math.sqrt(2.0)));
    }

    private static double triangularFromUniform(double u, double a, double b, double c) {
        double fc = (b - a) / (c - a);
        return (u <= fc)
                ? a + Math.sqrt(u * (c - a) * (b - a))
                : c - Math.sqrt((1 - u) * (c - a) * (c - b));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double calculateSD(List<Double> numArray) {
        double mean = DoubleStream.of(numArray.stream().mapToDouble(a -> a).toArray()).average().orElse(Double.NaN);
        double sumOfSquaredDiffs = DoubleStream.of(numArray.stream().mapToDouble(a -> a).toArray())
                .map(x -> (x - mean) * (x - mean))
                .sum();
        return Math.sqrt(sumOfSquaredDiffs / numArray.size());
    }

    public static double[] generateGBMPath(double initialValue, double mu, double sigma, int years) {
        double[] path = new double[years];
        path[0] = initialValue;
        Random rand = new Random();

        for(int i=1; i<years; i++) {
            double drift = mu - 0.5 * sigma * sigma;
            double shock = sigma * rand.nextGaussian();
            path[i] = path[i-1] * Math.exp(drift + shock);
        }
        return path;
    }

    public static GrowthDto calculateGrowth(List<Double> historicalRevenues , List<Double> historicalMargins, double marginMin, double marginMax) {
        List<Double> revenueGrowthRates = new ArrayList<>();
        for(int i=1; i < historicalRevenues.size(); i++) {
            double growth = (historicalRevenues.get(i) - historicalRevenues.get(i-1))
                    / historicalRevenues.get(i-1);
            revenueGrowthRates.add(growth);
        }

        DoubleSummaryStatistics stats = revenueGrowthRates.stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        double revenueMu = stats.getAverage();
        double revenueSigma = Math.sqrt(revenueGrowthRates.stream()
                .mapToDouble(r -> Math.pow(r - revenueMu, 2))
                .average().orElse(0));

        List<Double> marginChanges = new ArrayList<>();
        for(int i=1; i < historicalMargins.size(); i++) {
            double change = historicalMargins.get(i) - historicalMargins.get(i-1);
            marginChanges.add(change);
        }

        double correlation = new PearsonsCorrelation()
                .correlation(
                        ArrayUtils.toPrimitive(revenueGrowthRates.toArray(new Double[0])),
                        ArrayUtils.toPrimitive(marginChanges.toArray(new Double[0]))
                );

        double marginMu = marginChanges.stream()
                .mapToDouble(Double::doubleValue)
                .average().orElse(0);

        double marginSigma = Math.sqrt(marginChanges.stream()
                .mapToDouble(m -> Math.pow(m - marginMu, 2))
                .average().orElse(0));

        return new GrowthDto(
                revenueMu,
                revenueSigma,
                calculateSD(revenueGrowthRates),
                marginMu,
                marginSigma,
                calculateSD(marginChanges),
                marginMin,
                marginMax,
                correlation,
                revenueGrowthRates,
                marginChanges
        );
    }

    public static double adjustAnnualGrowth2_5years(double revenueGrowthNext, double industryAvg, final Optional<InputStatDistribution> optionalInputStatDistribution) {

        // Check if distribution data is available
        if (!optionalInputStatDistribution.isPresent()) {
            return applyGrowthBounds(revenueGrowthNext * 0.7 + industryAvg * 0.3);
        }

        InputStatDistribution distribution = optionalInputStatDistribution.get();
        Double q1 = distribution.getRevenueGrowthRateFirstQuartile() / 100;
        Double median = distribution.getRevenueGrowthRateMedian() / 100;
        Double q3 = distribution.getRevenueGrowthRateThirdQuartile() / 100;

        // Validate distribution data
        if (q1 == null || median == null || q3 == null) {
            return applyGrowthBounds(revenueGrowthNext * 0.5 + industryAvg * 0.5);
        }

        double adjustedGrowth;
        if (revenueGrowthNext > q3) {
            // Apply conditional capping based on ratio to industry average
            if (revenueGrowthNext >= 2.0 * industryAvg) {
                // Strong cap
                adjustedGrowth = (revenueGrowthNext * 0.5) + (q3 * 0.3) + (industryAvg * 0.2);
            } else if (revenueGrowthNext >= 1.5 * industryAvg) {
                // Gentle cap — respect stronger forecasts
                adjustedGrowth = (revenueGrowthNext * 0.75) + (q3 * 0.15) + (industryAvg * 0.10);
            } else {
                // Slightly above Q3 — treat more normally
                adjustedGrowth = (revenueGrowthNext * 0.5) + (median * 0.2) + (industryAvg * 0.3);
            }
        } else if (revenueGrowthNext < q1) {
            // Boost pessimistic forecasts
            adjustedGrowth = (revenueGrowthNext * 0.2) + (q1 * 0.5) + (median * 0.3);
        } else {
            // Moderate forecast
            adjustedGrowth = (revenueGrowthNext * 0.5) + (median * 0.2) + (industryAvg * 0.3);
        }

        return applyGrowthBounds(adjustedGrowth);
    }

    private static double applyGrowthBounds(double growthRate) {
        if (growthRate < -100) return -100;
        if (growthRate > 300) return 300;
        return growthRate;
    }
}
