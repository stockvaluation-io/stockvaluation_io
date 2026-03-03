package io.stockvaluation.utils;

import io.stockvaluation.dto.SegmentWeightedParameters;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread-safe context manager for segment-weighted parameters
 * Uses ThreadLocal to ensure each thread has its own copy of parameters
 * This prevents race conditions and ensures thread safety
 */
@Slf4j
public class SegmentParameterContext {
    
    private static final ThreadLocal<SegmentWeightedParameters> CONTEXT = new ThreadLocal<>();
    
    /**
     * Set segment-weighted parameters for the current thread
     * @param parameters The calculated segment-weighted parameters
     */
    public static void setParameters(SegmentWeightedParameters parameters) {
        if (parameters != null) {
            // Create a defensive copy to ensure thread safety
            CONTEXT.set(parameters.copy());
            log.debug("Set segment parameters for thread {}: {}", 
                     Thread.currentThread().getName(), parameters);
        } else {
            CONTEXT.remove();
            log.debug("Cleared segment parameters for thread {}", Thread.currentThread().getName());
        }
    }
    
    /**
     * Get segment-weighted parameters for the current thread
     * @return A copy of the parameters, or null if not set
     */
    public static SegmentWeightedParameters getParameters() {
        SegmentWeightedParameters parameters = CONTEXT.get();
        if (parameters != null) {
            // Return a defensive copy to prevent external modification
            return parameters.copy();
        }
        return null;
    }
    
    /**
     * Check if segment-weighted parameters are available for the current thread
     * @return true if parameters are set and valid
     */
    public static boolean hasValidParameters() {
        SegmentWeightedParameters parameters = CONTEXT.get();
        return parameters != null && parameters.hasValidParameters();
    }
    
    /**
     * Clear segment-weighted parameters for the current thread
     * This should be called at the end of request processing to prevent memory leaks
     */
    public static void clear() {
        CONTEXT.remove();
        log.debug("Cleared segment parameters for thread {}", Thread.currentThread().getName());
    }
    
    /**
     * Get a specific parameter value with fallback
     * @param parameterGetter Function to get the parameter value
     * @param fallbackValue Fallback value if parameter is not available
     * @return The parameter value or fallback
     */
    public static <T> T getParameterOrDefault(java.util.function.Function<SegmentWeightedParameters, T> parameterGetter, T fallbackValue) {
        SegmentWeightedParameters parameters = CONTEXT.get();
        if (parameters != null && parameters.hasValidParameters()) {
            try {
                T value = parameterGetter.apply(parameters);
                return value != null ? value : fallbackValue;
            } catch (Exception e) {
                log.warn("Error getting parameter from context: {}", e.getMessage());
                return fallbackValue;
            }
        }
        return fallbackValue;
    }
    
    /**
     * Get sector-specific parameters for a given sector
     * @param sectorName The name of the sector
     * @return SectorParameters for the sector, or null if not found
     */
    public static SegmentWeightedParameters.SectorParameters getSectorParameters(String sectorName) {
        SegmentWeightedParameters parameters = CONTEXT.get();
        if (parameters != null && parameters.hasValidParameters()) {
            return parameters.getSectorParameters(sectorName);
        }
        return null;
    }
    
    /**
     * Get a sector-specific parameter value with fallback
     * @param sectorName The name of the sector
     * @param parameterGetter Function to get the parameter value from SectorParameters
     * @param fallbackValue Fallback value if parameter is not available
     * @return The parameter value or fallback
     */
    public static <T> T getSectorParameterOrDefault(String sectorName, 
                                                   java.util.function.Function<SegmentWeightedParameters.SectorParameters, T> parameterGetter, 
                                                   T fallbackValue) {
        SegmentWeightedParameters.SectorParameters sectorParams = getSectorParameters(sectorName);
        if (sectorParams != null) {
            try {
                T value = parameterGetter.apply(sectorParams);
                return value != null ? value : fallbackValue;
            } catch (Exception e) {
                log.warn("Error getting sector parameter for {} from context: {}", sectorName, e.getMessage());
                return fallbackValue;
            }
        }
        return fallbackValue;
    }
    
    /**
     * Check if sector-specific parameters are available
     * @return true if sector parameters exist
     */
    public static boolean hasSectorParameters() {
        SegmentWeightedParameters parameters = CONTEXT.get();
        return parameters != null && parameters.hasSectorParameters();
    }
    
    /**
     * Get all sector names
     * @return Set of sector names, or empty set if not available
     */
    public static java.util.Set<String> getSectorNames() {
        SegmentWeightedParameters parameters = CONTEXT.get();
        if (parameters != null && parameters.hasValidParameters()) {
            return parameters.getSectorNames();
        }
        return java.util.Collections.emptySet();
    }
}
