package io.stockvaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.constant.YearlyCalculation;
import io.stockvaluation.domain.*;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationoutput.Story;
import io.stockvaluation.enums.InputDetails;
import io.stockvaluation.exception.BadRequestException;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.provider.DataProvider;
import io.stockvaluation.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.stockvaluation.service.GrowthCalculatorService.adjustAnnualGrowth2_5years;
import static io.stockvaluation.utils.Helper.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonService {

    private final DecimalFormat df = new DecimalFormat("0.00");

    private final Map<String, Object> basicAndFinancialMap = new ConcurrentHashMap<>();

    private final CountryEquityRepository countryEquityRepository;

    private final SectorMappingRepository sectorMappingRepository;

    private final RestTemplate restTemplate;

    private final DataProvider dataProvider;

    private final RiskFreeRateRepository riskFreeRateRepository;

    private final IndustryAveragesUSRepository industryAvgUSRepository;

    private final IndustryAveragesGlobalRepository industryAvgGloRepository;

    private final InputStatRepository inputStatRepository;

    private final RDConverterRepository rdConverterRepository;

    private final RegionEquityRepository regionEquityRepository;

    private final CostOfCapitalRepository costOfCapitalRepository;

    private final LargeSpreadRepository largeSpreadRepository;

    private final SmallSpreadRepository smallSpreadRepository;

    private final FailureRateRepository failureRateRepository;

    private final BondRatingRepository bondRatingRepository;

    private final CurrencyRateService currencyRateService;

    private final InputRepository inputRepository;

    private final CompanyDataMapper companyDataMapper;

    private final CompanyFinancialIngestionService companyFinancialIngestionService;

    private final ValuationAssumptionProperties valuationAssumptionProperties;

    private final CompanyDataAssemblyService companyDataAssemblyService;

    private final SegmentWeightedParameterService segmentWeightedParameterService;

    public CompanyDataDTO getCompanyDataFromProvider(String ticker) {

        if (Objects.nonNull(basicAndFinancialMap) && !basicAndFinancialMap.isEmpty()) {
            basicAndFinancialMap.clear();
        }
        CompanyDataDTO companyDataDTO = companyDataAssemblyService.assembleCompanyData(ticker);
        BasicInfoDataDTO basicInfoDataDTO = companyDataDTO.getBasicInfoDataDTO();
        FinancialDataDTO financialDataDTO = companyDataDTO.getFinancialDataDTO();

        // Fetch dividend data for DDM calculations
        try {
            DividendDataDTO dividendDataDTO = fetchDividendData(ticker);
            companyDataDTO.setDividendDataDTO(dividendDataDTO);
            if (dividendDataDTO != null && dividendDataDTO.isDividendPaying()) {
                log.info("Dividend data loaded for {}: yield={}, payout={}",
                        ticker, dividendDataDTO.getDividendYield(), dividendDataDTO.getPayoutRatio());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch dividend data for {}: {}", ticker, e.getMessage());
            // Continue without dividend data - FCFF will still work
        }

        basicAndFinancialMap.put("basicInfoDTO", basicInfoDataDTO);
        basicAndFinancialMap.put("financialDTO", financialDataDTO);
        basicAndFinancialMap.put("ticker", ticker);
        return companyDataDTO;
    }

    public void applySegmentWeightedParameters(FinancialDataInput financialDataInput, CompanyDataDTO companyDataDTO,
            List<String> adjustedParameters) {
        segmentWeightedParameterService.applySegmentWeightedParameters(
                financialDataInput,
                companyDataDTO,
                adjustedParameters,
                resolveBaselineRiskFreeRate());
    }

    public double resolveBaselineRiskFreeRate() {
        String baselineCurrencyCode = valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode();
        if (baselineCurrencyCode == null || baselineCurrencyCode.isBlank()) {
            return valuationAssumptionProperties.getBaselineRiskFreeRate();
        }
        return riskFreeRateRepository.findRiskFreeRateByCurrency(baselineCurrencyCode.toUpperCase(Locale.ROOT))
                .orElse(valuationAssumptionProperties.getBaselineRiskFreeRate());
    }

    public double resolveMatureMarketPremium() {
        String matureMarketCountry = valuationAssumptionProperties.getMatureMarketCountry();
        if (matureMarketCountry == null || matureMarketCountry.isBlank()) {
            return valuationAssumptionProperties.getMatureMarketPremium();
        }
        return countryEquityRepository.findMatureMarketPremiumByCountry(matureMarketCountry)
                .orElse(valuationAssumptionProperties.getMatureMarketPremium());
    }

    public double resolveRiskFreeRateForCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return resolveBaselineRiskFreeRate();
        }
        return riskFreeRateRepository.findRiskFreeRateByCurrency(currencyCode.toUpperCase(Locale.ROOT))
                .orElse(resolveBaselineRiskFreeRate());
    }

    public Object getCompanyDetails(InputDetails inputDetails) {
        if (Objects.isNull(basicAndFinancialMap) || basicAndFinancialMap.isEmpty()) {
            throw new BadRequestException("Enter the ticker name in the company search bar.");
        }
        if (InputDetails.INCOME_STATEMENT.equals(inputDetails)) {
            IncomeStatementDTO incomeStatementDTO = new IncomeStatementDTO();
            BasicInfoDataDTO basicInfoDTO = (BasicInfoDataDTO) basicAndFinancialMap.get("basicInfoDTO");
            incomeStatementDTO.setTicker((String) basicAndFinancialMap.get("ticker"));
            incomeStatementDTO.setCompanyName(basicInfoDTO.getCompanyName());
            FinancialDataDTO financialDataDTO = (FinancialDataDTO) basicAndFinancialMap.get("financialDTO");
            incomeStatementDTO.setRevenueTTM(financialDataDTO.getRevenueTTM());
            incomeStatementDTO.setRevenueLTM(financialDataDTO.getRevenueLTM());
            incomeStatementDTO.setStockPrice(financialDataDTO.getStockPrice());
            incomeStatementDTO.setOperatingIncomeTTM(financialDataDTO.getOperatingIncomeTTM());
            incomeStatementDTO.setOperatingIncomeLTM(financialDataDTO.getOperatingIncomeLTM());
            incomeStatementDTO.setEffectiveTaxRate(financialDataDTO.getEffectiveTaxRate());
            incomeStatementDTO.setHighestStockPrice(financialDataDTO.getHighestStockPrice());
            incomeStatementDTO.setLowestStockPrice(financialDataDTO.getLowestStockPrice());
            incomeStatementDTO.setPriceChangeFromLastStock(
                    financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice());
            incomeStatementDTO.setPercentageChangeFromLastStock(
                    (financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice()) * 100
                            / financialDataDTO.getStockPrice());
            incomeStatementDTO.setPriceChangeCurrentStock(
                    financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice());
            incomeStatementDTO.setPercentageChangeCurrentStock(
                    (financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice()) * 100
                            / financialDataDTO.getHighestStockPrice());
            return incomeStatementDTO;
        } else if (InputDetails.INFO.equals(inputDetails)) {
            InfoDTO infoDTO = new InfoDTO();
            FinancialDataDTO financialDataDTO = (FinancialDataDTO) basicAndFinancialMap.get("financialDTO");
            BasicInfoDataDTO basicInfoDTO = (BasicInfoDataDTO) basicAndFinancialMap.get("basicInfoDTO");
            infoDTO.setTicker((String) basicAndFinancialMap.get("ticker"));
            infoDTO.setCompanyName(basicInfoDTO.getCompanyName());
            infoDTO.setDateOfValuation(basicInfoDTO.getDateOfValuation());
            infoDTO.setWebsite(basicInfoDTO.getWebsite());
            infoDTO.setCountryOfIncorporation(basicInfoDTO.getCountryOfIncorporation());
            infoDTO.setIndustryUs(basicInfoDTO.getIndustryUs());
            infoDTO.setIndustryGlobal(basicInfoDTO.getIndustryGlobal());
            infoDTO.setNoOfShareOutstanding(financialDataDTO.getNoOfShareOutstanding());
            infoDTO.setStockPrice(financialDataDTO.getStockPrice());
            infoDTO.setHighestStockPrice(financialDataDTO.getHighestStockPrice());
            infoDTO.setLowestStockPrice(financialDataDTO.getLowestStockPrice());
            infoDTO.setPriceChangeFromLastStock(
                    financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice());
            infoDTO.setPercentageChangeFromLastStock(
                    (financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice()) * 100
                            / financialDataDTO.getStockPrice());
            infoDTO.setPriceChangeCurrentStock(
                    financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice());
            infoDTO.setPercentageChangeCurrentStock(
                    (financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice()) * 100
                            / financialDataDTO.getHighestStockPrice());
            return infoDTO;
        } else {
            BalanceSheetDTO balanceSheetDTO = new BalanceSheetDTO();

            FinancialDataDTO financialDataDTO = (FinancialDataDTO) basicAndFinancialMap.get("financialDTO");
            BasicInfoDataDTO basicInfoDTO = (BasicInfoDataDTO) basicAndFinancialMap.get("basicInfoDTO");
            balanceSheetDTO.setTicker((String) basicAndFinancialMap.get("ticker"));
            balanceSheetDTO.setCompanyName(basicInfoDTO.getCompanyName());
            balanceSheetDTO.setCashAndMarkablTTM(financialDataDTO.getCashAndMarkablTTM());
            balanceSheetDTO.setCashAndMarkablLTM(financialDataDTO.getCashAndMarkablLTM());
            balanceSheetDTO.setBookValueEqualityTTM(financialDataDTO.getBookValueEqualityTTM());
            balanceSheetDTO.setBookValueEqualityLTM(financialDataDTO.getBookValueEqualityLTM());
            balanceSheetDTO.setStockPrice(financialDataDTO.getStockPrice());
            balanceSheetDTO.setBookValueDebtTTM(financialDataDTO.getBookValueDebtTTM());
            balanceSheetDTO.setBookValueDebtLTM(financialDataDTO.getBookValueDebtLTM());
            balanceSheetDTO.setHighestStockPrice(financialDataDTO.getHighestStockPrice());
            balanceSheetDTO.setLowestStockPrice(financialDataDTO.getLowestStockPrice());
            balanceSheetDTO.setPriceChangeFromLastStock(
                    financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice());
            balanceSheetDTO.setPercentageChangeFromLastStock(
                    (financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice()) * 100
                            / financialDataDTO.getStockPrice());
            balanceSheetDTO.setPriceChangeCurrentStock(
                    financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice());
            balanceSheetDTO.setPercentageChangeCurrentStock(
                    (financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice()) * 100
                            / financialDataDTO.getHighestStockPrice());
            return balanceSheetDTO;
        }
    }

    public RDResult calculateRDConverterValue(String industry, Double marginalTaxRate) {
        Map<String, Double> researchAndDevelopmentMap = Optional
                .ofNullable((FinancialDataDTO) basicAndFinancialMap.get("financialDTO"))
                .map(FinancialDataDTO::getResearchAndDevelopmentMap)
                .orElse(Collections.emptyMap());
        return calculateRDConverterValue(industry, marginalTaxRate, researchAndDevelopmentMap);
    }

    public RDResult calculateRDConverterValue(String industry, Double marginalTaxRate,
            Map<String, Double> researchAndDevelopmentMap) {
        int defaultAmortization = 4;
        SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(industry);
        if (Objects.isNull(sectorMapping)) {
            log.info("Sector mapping not found for industry: {}", industry);
            // throw new RuntimeException("Sector mapping not found for industry: " +
            // input.getIndustryUS());
        }
        // TODO: handle case when sector mapping is not found and
        // also handle the duplicated data while calculations

        RDConverter rdConverter = rdConverterRepository.findByIndustryName(sectorMapping.getIndustryAsPerExcel());

        int amortizationPeriod;

        if (Objects.isNull(rdConverter)) {
            // throw new RuntimeException("Amortization period not found for industry: " +
            // input.getIndustryUS());
            log.info("Amortization period not found for industry: {} ", industry);
            amortizationPeriod = defaultAmortization;
        } else {
            amortizationPeriod = rdConverter.getAmortizationPeriod();
            log.info("Amortization Period of {} is {} ", "entertainment", rdConverter.getAmortizationPeriod());
            if (amortizationPeriod > 4) {
                log.info("Amortization period for {} is greater than 5, setting to default: {}", "entertainment",
                        defaultAmortization);
                amortizationPeriod = defaultAmortization;
            }
        }

        Double currentYearExpense = researchAndDevelopmentMap.get("currentR&D-0");

        List<Double> pastRdExpenses = new ArrayList<>();
        if (researchAndDevelopmentMap.get("currentR&D-1") != null
                && researchAndDevelopmentMap.get("currentR&D-1") != 0.0
                && !Objects.equals(currentYearExpense, researchAndDevelopmentMap.get("currentR&D-1"))) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-1"));
        }
        if (researchAndDevelopmentMap.get("currentR&D-2") != null
                && researchAndDevelopmentMap.get("currentR&D-2") != 0.0) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-2"));
        }
        if (researchAndDevelopmentMap.get("currentR&D-3") != null
                && researchAndDevelopmentMap.get("currentR&D-3") != 0.0) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-3"));
        }
        if (researchAndDevelopmentMap.get("currentR&D-4") != null
                && researchAndDevelopmentMap.get("currentR&D-4") != 0.0) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-4"));
        }

        // FIXED: Use available data instead of returning zeros when insufficient
        // history
        // Only return zeros if there is NO R&D data at all (no current year expense)
        if (currentYearExpense == null || currentYearExpense == 0) {
            log.info("No current R&D expense, returning zero R&D adjustments");
            return new RDResult(0.00, 0.00, 0.00, 0.00);
        }

        // Adjust amortization period based on available data
        // Per Damodaran: Use the best available data rather than ignoring R&D entirely
        int effectiveAmortizationPeriod;
        if (pastRdExpenses.isEmpty()) {
            // If no historical data, use current year only (1-year amortization)
            effectiveAmortizationPeriod = 1;
            log.warn("No historical R&D data available. Using current year expense only for R&D capitalization.");
        } else if (pastRdExpenses.size() < amortizationPeriod - 1) {
            // Use available historical data with adjusted amortization period
            effectiveAmortizationPeriod = pastRdExpenses.size() + 1;
            log.info("Limited R&D history available ({} years). Adjusting amortization period from {} to {}.",
                    pastRdExpenses.size(), amortizationPeriod, effectiveAmortizationPeriod);
        } else {
            effectiveAmortizationPeriod = amortizationPeriod;
        }

        log.info("Current Year expense: {} , tax rate: {} , effective amortization period: {}, past year expenses: {}",
                currentYearExpense, marginalTaxRate, effectiveAmortizationPeriod, pastRdExpenses);
        RDResult result = new RDResult();
        List<YearlyCalculation> calculations = new ArrayList<>();
        log.info("R&D calculation started...");
        // Current year calculation
        calculations.add(createYearlyCalculation("current Year", currentYearExpense, effectiveAmortizationPeriod, 0));
        // Past years calculations
        for (int i = 0; i < effectiveAmortizationPeriod - 1; i++) {
            double pastRdExpense = i < pastRdExpenses.size() ? pastRdExpenses.get(i) : 0;
            if (i >= pastRdExpenses.size()) {
                log.warn("No R&D expense data available for year {}. setting default value to 0.", (i + 1));
            }
            calculations
                    .add(createYearlyCalculation("Year - " + (i + 1), pastRdExpense, effectiveAmortizationPeriod, i));
        }

        // Set results
        result.setYearlyCalculations(calculations);
        double totalResearchAsset = 0;
        double totalAmortization = 0;
        for (YearlyCalculation calc : calculations) {
            totalResearchAsset += calc.getUnamortizedPortion();
            if (!calc.getYear().contains("Current")) {
                totalAmortization += calc.getAmortizationThisYear();
            }
        }
        result.setTotalResearchAsset(totalResearchAsset);
        result.setTotalAmortization(totalAmortization);
        log.info("Total Amortization calculated: {} and Total research asset value is: {} ",
                result.getTotalAmortization(), result.getTotalResearchAsset());

        // Adjusted amount to operating income
        double adjustmentToOperatingIncome = currentYearExpense - totalAmortization;
        result.setAdjustmentToOperatingIncome(adjustmentToOperatingIncome);
        log.info("Adjustment to Operating Income calculated: {}", result.getAdjustmentToOperatingIncome());

        // Tax Effect: calculating tax amount
        double taxEffect = adjustmentToOperatingIncome * (marginalTaxRate / 100);
        result.setTaxEffect(taxEffect);
        log.info("Tax Effect calculated: {}", result.getTaxEffect());
        log.info("R&D converter completed...");
        return new RDResult(totalResearchAsset, totalAmortization, adjustmentToOperatingIncome, taxEffect);
    }

    private YearlyCalculation createYearlyCalculation(String yearLabel, double rdExpense, int amortizationPeriod,
            int yearIndex) {
        YearlyCalculation yearlyCalculation = new YearlyCalculation();
        yearlyCalculation.setYear(yearLabel);
        yearlyCalculation.setRdExpense(rdExpense);

        if (yearLabel.contains("current")) {
            // For current year, unamortized portion is equal to the entire expense and
            // amortization is 0
            yearlyCalculation.setUnamortizedPortion(rdExpense);
            yearlyCalculation.setAmortizationThisYear(0.00);
        } else {
            // For past years, calculate the amortization and unamortized portion
            double unamortizedPortion = rdExpense * (double) (amortizationPeriod - (yearIndex + 1))
                    / amortizationPeriod;
            yearlyCalculation.setUnamortizedPortion(Math.max(unamortizedPortion, 0));
            yearlyCalculation.setAmortizationThisYear(rdExpense / amortizationPeriod);
        }

        log.info(
                "Added {} calculations , amortization: {}, unamortization portion this year: {} , RD expenses this year: {}",
                yearlyCalculation.getYear(), yearlyCalculation.getAmortizationThisYear(),
                yearlyCalculation.getUnamortizedPortion(), yearlyCalculation.getRdExpense());

        return yearlyCalculation;
    }

    private String formatDouble(double value) {
        return df.format(value);
    }

    public String convertExcelToJson(MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(11); // Assuming the relevant data is on the 12th sheet

        List<Map<String, Object>> excelData = new ArrayList<>(); // Changed to Object to accommodate numeric values
        Row headerRow = sheet.getRow(3); // Assuming the actual header row starts at row 4 (index 3)
        if (headerRow == null) {
            throw new IllegalArgumentException("Header row is missing in the Excel file");
        }

        // Create a FormulaEvaluator for evaluating formulas
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        // Iterate over rows starting from row 4 (index 3)
        for (int i = 4; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row))
                continue; // Skip empty or null rows

            Map<String, Object> rowData = new LinkedHashMap<>();
            // Mapping financial and country data
            rowData.put("country", getCellValueAsString(row.getCell(0))); // Country name
            rowData.put("moodysRating", getCellValueAsString(row.getCell(1))); // Moody's credit rating
            rowData.put("adjustedDefaultSpread", getNumericCellValue(row.getCell(2))); // Adjusted default spread
            rowData.put("equityRiskPremium", getNumericCellValue(row.getCell(3))); // Equity risk premium
            rowData.put("countryRiskPremium", getNumericCellValue(row.getCell(4))); // Country risk premium
            rowData.put("corporateTaxRate", getNumericCellValue(row.getCell(5))); // Corporate tax rate
            rowData.put("gdpInMillions", getNumericCellValue(row.getCell(6))); // GDP in millions

            excelData.add(rowData);
        }

        workbook.close();

        // Convert to JSON using Jackson ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(excelData);
    }

    // method to get values numerically instead of string
    private Object getNumericCellValue(Cell cell) {
        if (cell == null)
            return null; // Return null if the cell is empty
        switch (cell.getCellType()) {
            case NUMERIC:
                // Check if the cell is formatted as a percentage
                // Check if the cell is formatted as a percentage
                if (cell.getCellStyle().getDataFormatString().contains("%")) {
                    return cell.getNumericCellValue() * 100; // Multiply by 100 for percentage values
                }
                return cell.getNumericCellValue(); // Return numeric value as is
            case FORMULA:
                // Evaluate the formula and return numeric value if applicable
                FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue evaluatedValue = evaluator.evaluate(cell);
                if (evaluatedValue.getCellType() == CellType.NUMERIC) {
                    // Check if the evaluated value is formatted as a percentage
                    if (cell.getCellStyle().getDataFormatString().contains("%")) {
                        return evaluatedValue.getNumberValue() * 100; // Return the evaluated numeric value directly
                    }
                    return evaluatedValue.getNumberValue(); // Return the evaluated numeric value as is
                }
                return null; // Return null if the evaluated cell is not numeric
            default:
                return null; // Return null for non-numeric types
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    public String convertExcelToJsonSingleObject(MultipartFile file) throws IOException {
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // Use a list to wrap the single object
            List<Map<String, Object>> excelData = new ArrayList<>();
            Map<String, Object> rowData = new LinkedHashMap<>();

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row))
                    continue;

                Cell keyCell = row.getCell(0);
                Cell valueCell = row.getCell(1);
                if (row.getCell(3) != null) {
                }

                if (keyCell != null && keyCell.getCellType() == CellType.STRING
                        && !keyCell.getStringCellValue().trim().isEmpty()) {
                    String key = keyCell.getStringCellValue().trim();
                    Object value = getCellValue(evaluator, valueCell);

                    if (!isUnwantedKey(key)) { // if value is not present with value- continue ( instructions)
                        rowData.put(toCamelCase(key), value); // Convert key to camelCase
                    }
                }
            }

            // Add the populated rowData map to the excelData list
            excelData.add(rowData);

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(excelData);
        } finally {
            if (workbook != null)
                workbook.close();
        }
    }

    private String toCamelCase(String key) {
        if (key == null || key.trim().isEmpty()) {
            return key;
        }

        key = key.replaceAll("[=]", ""); // Remove trailing '=' sign if any
        StringBuilder camelCaseKey = new StringBuilder();
        String[] parts = key.split("[-_ ]");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty())
                continue;

            if (i == 0) {
                camelCaseKey.append(part.toLowerCase());
            } else {
                camelCaseKey.append(part.substring(0, 1).toUpperCase());
                camelCaseKey.append(part.substring(1).toLowerCase());
            }
        }
        return camelCaseKey.toString();
    }

    private Object getCellValue(FormulaEvaluator evaluator, Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Format the date as a string
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
                    return dateFormat.format(cell.getDateCellValue()); // Convert to formatted string
                }

                return cell.getNumericCellValue(); // Return as a number

            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return evaluator.evaluate(cell).getNumberValue();
            default:
                return "";
        }
    }

    private boolean isUnwantedKey(String key) {
        return key.contains("If you don't understand") || key.contains("Numbers from your base year");
    }

    public String convertIndustryAverageExcelToJson(MultipartFile file) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Ensure the sheet exists
            if (workbook.getNumberOfSheets() <= 0) {
                throw new IllegalArgumentException("Sheet index out of bounds");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            List<String> keys = new ArrayList<>();

            // Read header row for keys
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                for (Cell cell : headerRow) {
                    keys.add(toCamelCase(cell.getStringCellValue()));
                }
            }

            // Read data rows
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < keys.size(); cellIndex++) {
                    Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    Object cellValue = getCellValueAsString(cell); // Extracting value

                    // Handle percentage values
                    if (cell.getCellType() == CellType.NUMERIC) {
                        if (cell.getCellStyle().getDataFormatString().contains("%")) {
                            // It's a percentage, convert it to the appropriate format (multiply by 100)
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue() * 100); // Return as percentage
                        } else {
                            // Return as normal numeric value
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue());
                        }
                    } else if (cell.getCellType() == CellType.STRING) {
                        // If the cell is a string, handle it accordingly
                        rowData.put(keys.get(cellIndex), cellValue);
                    } else {
                        // Handle other types as needed (e.g., boolean, formula, etc.)
                        rowData.put(keys.get(cellIndex), cellValue);
                    }
                }
                data.add(rowData);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        // Convert to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(data);
    }

    public String convertCountryEquityExcelToJson(MultipartFile file) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Ensure the sheet exists
            if (workbook.getNumberOfSheets() <= 11) {
                throw new IllegalArgumentException("Sheet index out of bounds");
            }

            Sheet sheet = workbook.getSheetAt(11);
            Iterator<Row> rowIterator = sheet.iterator();
            List<String> keys = new ArrayList<>();

            // Read header row for keys
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                for (Cell cell : headerRow) {
                    keys.add(toCamelCase(cell.getStringCellValue()));
                }
            }

            // Read data rows
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < keys.size(); cellIndex++) {
                    Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    Object cellValue = getCellValueAsString(cell); // Extracting value

                    // Handle percentage values
                    if (cell.getCellType() == CellType.NUMERIC) {
                        if (cell.getCellStyle().getDataFormatString().contains("%")) {
                            // It's a percentage, convert it to the appropriate format (multiply by 100)
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue() * 100); // Return as percentage
                        } else {
                            // Return as normal numeric value
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue());
                        }
                    } else if (cell.getCellType() == CellType.STRING) {
                        // If the cell is a string, handle it accordingly
                        rowData.put(keys.get(cellIndex), cellValue);
                    } else {
                        // Handle other types as needed (e.g., boolean, formula, etc.)
                        rowData.put(keys.get(cellIndex), cellValue);
                    }
                }
                data.add(rowData);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        // Convert to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(data);
    }

    public String convertInputExcelDataToJson(MultipartFile file) throws IOException {
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, List<Object>>> data = new ArrayList<>();
            Map<String, List<Object>> rowData = new LinkedHashMap<>();

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row))
                    continue; // if row is empty skip that row

                Cell keyCell = row.getCell(0); // for key
                List<Object> colData = new ArrayList<>();

                for (int j = 1; j < row.getLastCellNum(); j++) {
                    Cell valueCell = row.getCell(j);
                    if (valueCell == null || valueCell.getCellType() == CellType.BLANK)
                        break; // Stop if the cell is blank

                    // Get cell value as a String, Double, or other types as necessary
                    switch (valueCell.getCellType()) {
                        case STRING:
                            colData.add(valueCell.getStringCellValue());
                            break;
                        case NUMERIC:
                            colData.add(valueCell.getNumericCellValue());
                            break;
                        case BOOLEAN:
                            colData.add(valueCell.getBooleanCellValue());
                            break;
                        case BLANK:
                            colData.add(null); // or handle as needed
                            break;
                        default:
                            colData.add(valueCell.toString()); // Fallback for other types
                    }
                }

                if (keyCell != null && keyCell.getCellType() == CellType.STRING
                        && !keyCell.getStringCellValue().trim().isEmpty()) {
                    String key = keyCell.getStringCellValue().trim();

                    if (!isUnwantedKey(key)) {
                        rowData.put(toCamelCase(key), colData);
                    }
                }
            }

            data.add(rowData);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    public List<IndustryAveragesUS> getAllIndustryUS() {
        return industryAvgUSRepository.findAll();
    }

    public void loadIndustryUSData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<IndustryAveragesUS> industryAveragesList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, IndustryAveragesUS.class));

        // Save the list to the database
        industryAvgUSRepository.saveAll(industryAveragesList);

        log.info("Data successfully loaded into the database!");
    }

    public void loadIndustryGloData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<IndustryAveragesGlobal> industryAveragesList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, IndustryAveragesGlobal.class));

        // Save the list to the database
        industryAvgGloRepository.saveAll(industryAveragesList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<IndustryAveragesGlobal> getAllIndustryGlo() {
        return industryAvgGloRepository.findAll();
    }

    public void loadCountryEquityData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<CountryEquity> countryEquityList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CountryEquity.class));

        // Save the list to the database
        countryEquityRepository.saveAll(countryEquityList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<CountryEquity> getAllCountryEquity() {
        return countryEquityRepository.findAll();
    }

    public void loadRegionEquityData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<RegionEquity> regionEquityList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RegionEquity.class));

        // Save the list to the database
        regionEquityRepository.saveAll(regionEquityList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<RegionEquity> getAllRegionEquity() {
        return regionEquityRepository.findAll();
    }

    public void loadRDConverterData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<RDConverter> rdConverterList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RDConverter.class));

        // Save the list to the database
        rdConverterRepository.saveAll(rdConverterList);
    }

    public List<RDConverter> getAllRDConverter() {
        return rdConverterRepository.findAll();
    }

    public List<InputStatDistribution> getAllInputStat() {
        return inputStatRepository.findAll();
    }

    public void loadRegionSectorMapping(MultipartFile file) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<SectorMapping> sectorMappingList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SectorMapping.class));

        // Save the list to the database
        sectorMappingRepository.saveAll(sectorMappingList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<SectorMapping> getAllSectorMapping() {
        return sectorMappingRepository.findAll();
    }

    public void loadRiskFreeRate(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<RiskFreeRate> riskFreeRateList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RiskFreeRate.class));

        // Save the list to the database
        riskFreeRateRepository.saveAll(riskFreeRateList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<RiskFreeRate> getAllRiskFreeRate() {
        return riskFreeRateRepository.findAll();
    }

    public void loadCostOfCapital(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<CostOfCapital> costOfCapitalList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CostOfCapital.class));

        // Save the list to the database
        costOfCapitalRepository.saveAll(costOfCapitalList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<CostOfCapital> getAllCostOfCapital() {
        return costOfCapitalRepository.findAll();
    }

    public void loadLargeSpread(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<LargeBondSpread> largeBondSpreadList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, LargeBondSpread.class));

        // Save the list to the database
        largeSpreadRepository.saveAll(largeBondSpreadList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<LargeBondSpread> getAllLargeSpread() {
        return largeSpreadRepository.findAll();
    }

    public void loadSmallSpread(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<SmallBondSpread> smallBondSpreadList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SmallBondSpread.class));

        // Save the list to the database
        smallSpreadRepository.saveAll(smallBondSpreadList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<SmallBondSpread> getAllSmallSpread() {
        return smallSpreadRepository.findAll();
    }

    public void loadBondRating(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<BondRating> bondRatingList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, BondRating.class));

        // Save the list to the database
        bondRatingRepository.saveAll(bondRatingList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<BondRating> getAllBondRating() {
        return bondRatingRepository.findAll();
    }

    public void loadFailureRate(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<FailureRate> failureRateList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, FailureRate.class));

        // Save the list to the database
        failureRateRepository.saveAll(failureRateList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<FailureRate> getAllFailureRate() {
        return failureRateRepository.findAll();
    }

    public void loadInputStat(MultipartFile file) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<InputStatDistribution> inputStatList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, InputStatDistribution.class));

        // Save the list to the database
        inputStatRepository.saveAll(inputStatList);

        System.out.println("Data successfully loaded into the database!");
    }

    /**
     * Calculate operating lease conversion with default zero values.
     * This is a backward-compatible method that returns zero adjustments when no
     * lease data is available.
     * For companies with actual operating lease commitments, use the overloaded
     * method with parameters.
     * 
     * @return LeaseResultDTO with zero adjustments (no operating lease impact)
     */
    public LeaseResultDTO calculateOperatingLeaseConverter() {
        // Return zero adjustments when no lease data is provided
        // This prevents applying arbitrary default values to companies without
        // operating leases
        return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Calculate operating lease conversion using Damodaran's methodology.
     * Converts operating lease commitments to debt equivalent for proper DCF
     * valuation.
     * 
     * Per Damodaran: Operating leases are effectively debt obligations that should
     * be
     * capitalized to get a true picture of the company's debt and operating income.
     * 
     * @param leaseExpenseCurrentYear Current year's operating lease expense
     * @param commitments             Array of lease commitments for Years 1-5
     * @param futureCommitment        Total commitment beyond Year 5
     * @return LeaseResultDTO with calculated adjustments
     */
    public LeaseResultDTO calculateOperatingLeaseConverter(
            Double leaseExpenseCurrentYear,
            Double[] commitments,
            Double futureCommitment) {

        // Return zero adjustments if no valid lease data provided
        if (leaseExpenseCurrentYear == null || leaseExpenseCurrentYear <= 0) {
            log.debug("No operating lease expense provided, returning zero adjustments");
            return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        }

        if (commitments == null || commitments.length == 0) {
            log.debug("No lease commitments provided, returning zero adjustments");
            return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        }

        // Default future commitment to 0 if not provided
        if (futureCommitment == null) {
            futureCommitment = 0.0;
        }

        double costOfDebt = valuationAssumptionProperties.getPreTaxCostOfDebt();
        int numberOfYearsInCommits;

        // Array to store present values of commitments
        double[] presentValues = new double[commitments.length];
        double totalCommits = 0;
        double avgCommits;
        for (Double commitment : commitments) {
            if (commitment != null) {
                totalCommits += commitment;
            }
        }

        // Handle edge case where all commitments are zero
        if (totalCommits == 0) {
            log.debug("All lease commitments are zero, returning zero adjustments");
            return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        }

        avgCommits = totalCommits / commitments.length;
        numberOfYearsInCommits = avgCommits > 0 ? (int) Math.round(futureCommitment / avgCommits) : 0;
        log.info("Number of years embedded in yr 6 estimate: {} ", numberOfYearsInCommits);

        // Variable to accumulate total debt
        double totalDebt = 0;

        log.info("Starting the lease conversion calculation.");

        // Convert each commitment into Present Value (PV) using the given commitments
        log.info("Calculating Present Values for Year 1 to Year 5 commitments.");
        for (int i = 0; i < commitments.length; i++) {
            double commitment = commitments[i] != null ? commitments[i] : 0.0;
            presentValues[i] = calculatePresentValue(commitment, i + 1);
            totalDebt += presentValues[i];
            log.info("Year {}: Commitment = {}, Present Value = {}", i + 1, commitment, presentValues[i]);
        }

        // Calculate Present Value for Year 6 and beyond as an annuity
        log.info("Calculating Present Value for Year 6 and beyond using an annuity method.");
        double annuityCommitments = calculateAnnuityCommitments(numberOfYearsInCommits, futureCommitment);
        double annuityPV = calculateValue(numberOfYearsInCommits, annuityCommitments, costOfDebt);

        totalDebt += annuityPV;
        log.info("Year 6 and beyond annuity PV: {} and total Debt value: {}", annuityPV, totalDebt);

        // Calculate depreciationOnLeaseAsset on operating lease asset (straight-line)
        int totalYears = numberOfYearsInCommits + commitments.length;
        double depreciationOnLeaseAsset = totalYears > 0 ? totalDebt / totalYears : 0;
        log.info("Calculating depreciationOnLeaseAsset: Total Debt = {}, Depreciation = {}", totalDebt,
                depreciationOnLeaseAsset);

        // Calculate adjustment to operating earnings
        double adjustmentToOperatingEarnings = leaseExpenseCurrentYear - depreciationOnLeaseAsset;
        log.info("Calculating adjustment to operating earnings: Lease Expense = {}, Depreciation = {}, Adjustment = {}",
                leaseExpenseCurrentYear, depreciationOnLeaseAsset, adjustmentToOperatingEarnings);

        double adjustmentToTotalDebt = totalDebt;

        // Return all calculated adjustments in the DTO
        LeaseResultDTO resultDTO = new LeaseResultDTO(depreciationOnLeaseAsset, adjustmentToOperatingEarnings,
                adjustmentToTotalDebt, depreciationOnLeaseAsset);
        log.info("Lease Conversion calculation completed successfully.");

        return resultDTO;
    }

    private double calculatePresentValue(double commitment, int year) {
        log.debug("Calculating Present Value for commitment {} at year {}", commitment, year);
        double pv = commitment / Math.pow(1 + valuationAssumptionProperties.getPreTaxCostOfDebt(), year);
        log.debug("Present Value for commitment {} in year {}: {}", commitment, year, pv);
        return pv;
    }

    private double calculateAnnuityCommitments(int numberOfYearsInCommits, double futureCommitment) {
        if (numberOfYearsInCommits > 0) {
            return futureCommitment > 0 ? futureCommitment / numberOfYearsInCommits : futureCommitment;
        } else {
            return 0;
        }
    }

    public double calculateValue(double numberOfYearsInCommits, double annuityCommitments, double costOfDebt) {
        double result;

        // Calculate the formula when D18 > 0
        if (numberOfYearsInCommits > 0) {
            double part1 = Math.pow(1 + costOfDebt, -numberOfYearsInCommits);
            double part2 = 1 - part1;
            double part3 = annuityCommitments * part2 / costOfDebt;
            double part4 = Math.pow(1 + costOfDebt, 5);
            result = part3 / part4;
        } else {
            // Calculate the formula when D18 <= 0
            result = annuityCommitments / Math.pow(1 + costOfDebt, 6);
        }

        return result;
    }

    /**
     * Extract Authorization header from current HTTP request context
     */
    private String getAuthTokenFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && !authHeader.isEmpty()) {
                    return authHeader;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract Authorization header from request context: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> story(String url, Object json) {
        // Try to extract auth token from current request context
        String authToken = getAuthTokenFromRequest();
        return story(url, json, authToken);
    }

    private Map<String, Object> story(String url, Object json, String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward Authorization header if provided (required for credit-gated
        // endpoints)
        if (authToken != null && !authToken.isEmpty()) {
            headers.set("Authorization", authToken);
            log.debug("Forwarding Authorization header to Python backend for story generation");
        } else {
            log.warn("No Authorization header found - Python backend may reject request for credit-gated endpoint");
        }

        try {
            // Manually serialize with JavaTimeModule to handle LocalDate fields
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

            String jsonString = objectMapper.writeValueAsString(json);
            HttpEntity<String> entity = new HttpEntity<>(jsonString, headers);

            return restTemplate.postForObject(url, entity, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize request body for story endpoint: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize request", e);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Python backend returned error for story endpoint: {} - {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw e;
        }
    }

    @Transactional
    public void saveInputData(List<InputRequestDTO> inputRequestDTOList) {
        List<Input> inputs = inputRequestDTOList.stream().map(inputDTO -> {
            Input input = new Input();
            input.setDateOfValuation(inputDTO.getDateOfValuation());
            input.setTicker(inputDTO.getTicker());
            input.setCompanyName(inputDTO.getCompanyName());
            input.setCurrency(inputDTO.getCurrency());
            input.setIndustryUS(inputDTO.getIndustryUS());
            input.setIndustryGlo(inputDTO.getIndustryGlo());
            input.setCurrentYearExpense(inputDTO.getCurrentYearExpense());
            input.setTotalRevenue(inputDTO.getTotalRevenue());
            input.setOperatingIncome(inputDTO.getOperatingIncome());
            input.setHasRAndDExpensesToCapitalize(inputDTO.getHasRAndDExpensesToCapitalize());
            input.setMarginalTaxRate(inputDTO.getMarginalTaxRate());
            // Handle PastExpense as well
            List<PastExpense> pastExpenses = inputDTO.getPastExpense().stream().map(pastExpenseDTO -> {
                PastExpense pastExpense = new PastExpense();
                pastExpense.setExpense(pastExpenseDTO.getExpense());
                pastExpense.setInput(input); // Set the input reference
                return pastExpense;
            }).collect(Collectors.toList());

            input.setPastExpense(pastExpenses); // Set the list of past expenses

            return input;
        }).collect(Collectors.toList());

        // Save all inputs in one batch operation
        inputRepository.saveAll(inputs);
    }

    public List<Input> getAllInputData() {
        return inputRepository.findAll();
    }

    @Transactional
    public void deleteInput(Long inputId) {
        // Check if the input exists
        if (inputRepository.existsById(inputId)) {
            inputRepository.deleteById(inputId);
        } else {
            throw new RuntimeException("Input not found with id: " + inputId);
        }
    }

    public String saveSingleInputData(InputRequestDTO inputDTO) {
        Input input = new Input();
        input.setDateOfValuation(inputDTO.getDateOfValuation());
        input.setTicker(inputDTO.getTicker());
        input.setCompanyName(inputDTO.getCompanyName());
        input.setCurrency(inputDTO.getCurrency());
        input.setIndustryUS(inputDTO.getIndustryUS());
        input.setIndustryGlo(inputDTO.getIndustryGlo());
        input.setCurrentYearExpense(inputDTO.getCurrentYearExpense());
        input.setTotalRevenue(inputDTO.getTotalRevenue());
        input.setOperatingIncome(inputDTO.getOperatingIncome());
        input.setHasRAndDExpensesToCapitalize(inputDTO.getHasRAndDExpensesToCapitalize());
        input.setMarginalTaxRate(inputDTO.getMarginalTaxRate());

        List<PastExpense> pastExpense = inputDTO.getPastExpense().stream().map(pastExpenseRequestDTO -> {
            PastExpense pastExpense1 = new PastExpense();
            pastExpense1.setInput(input);
            pastExpense1.setExpense(pastExpenseRequestDTO.getExpense());
            return pastExpense1;
        }).collect(Collectors.toList());
        input.setPastExpense(pastExpense);
        inputRepository.save(input);
        return "data saved successfully";
    }

    public Map<String, Double> getR_DValues(String companyTicker, boolean requireRdConverter) {
        Map<String, Double> result = new HashMap<>();
        if (!requireRdConverter) {
            result.put("totalResearchAsset", 0.0);
            result.put("totalAmortization", 0.0);
            result.put("adjustmentToOperatingIncome", 0.0);
            return result;
        }
        return result;
    }

    /**
     * Calculate causal scenarios with dependency chain reasoning and heat map data.
     * 
     * Implements simple causal chains:
     * Revenue → Margin → ROIC → Value
     * 
     * @param ticker                 Stock ticker symbol
     * @param request                Causal scenario request with variances
     * @param valuationOutputService Service for calculating valuations
     * @return CausalScenarioResponse with 3 scenarios and heat map
     */

    /**
     * Fetch dividend data from Yahoo Finance API for DDM calculations.
     * 
     * @param ticker Stock ticker symbol
     * @return DividendDataDTO with dividend information, or null if not available
     */
    public DividendDataDTO fetchDividendData(String ticker) {
        try {
            log.info("Fetching dividend data for {} via data provider", ticker);
            Map<String, Object> response = dataProvider.getDividendData(ticker);

            if (response == null || response.isEmpty()) {
                log.info("No dividend data available for {}", ticker);
                return null;
            }

            DividendDataDTO dividendDataDTO = DividendDataDTO.builder()
                    .dividendRate(parseDouble(response.get("dividendRate")))
                    .dividendYield(parseDouble(response.get("dividendYield")))
                    .payoutRatio(parseDouble(response.get("payoutRatio")))
                    .trailingAnnualDividendRate(parseDouble(response.get("trailingAnnualDividendRate")))
                    .trailingAnnualDividendYield(parseDouble(response.get("trailingAnnualDividendYield")))
                    .exDividendDate(parseLong(response.get("exDividendDate")))
                    .lastDividendValue(parseDouble(response.get("lastDividendValue")))
                    .lastDividendDate(parseLong(response.get("lastDividendDate")))
                    .fiveYearAvgDividendYield(parseDouble(response.get("fiveYearAvgDividendYield")))
                    .dividendGrowthRate(parseDouble(response.get("dividendGrowthRate")))
                    .build();

            // Parse dividend history if available
            if (response.get("dividendHistory") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> historyMap = (Map<String, Object>) response.get("dividendHistory");
                Map<String, Double> dividendHistory = new HashMap<>();
                for (Map.Entry<String, Object> entry : historyMap.entrySet()) {
                    dividendHistory.put(entry.getKey(), parseDouble(entry.getValue()));
                }
                dividendDataDTO.setDividendHistory(dividendHistory);
            }

            return dividendDataDTO;

        } catch (Exception e) {
            log.warn("Error fetching dividend data for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * Safely parse a Double from an Object
     */
    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Safely parse a Long from an Object
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
