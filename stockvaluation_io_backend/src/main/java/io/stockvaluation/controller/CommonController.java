package io.stockvaluation.controller;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.domain.*;
import io.stockvaluation.dto.InputRequestBatchDTO;
import io.stockvaluation.dto.InputRequestDTO;
import io.stockvaluation.dto.LeaseResultDTO;
import io.stockvaluation.dto.ResponseDTO;
import io.stockvaluation.enums.InputDetails;
import io.stockvaluation.exception.BadRequestException;
import io.stockvaluation.form.LoginForm;
import io.stockvaluation.form.SignupForm;
import io.stockvaluation.service.CommonService;
import io.stockvaluation.service.CostOfCapitalService;
import io.stockvaluation.service.OptionValueService;
import io.stockvaluation.service.SyntheticRatingService;
import io.stockvaluation.utils.ResponseGenerator;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@RestController
@RequestMapping(value = "/api/v1")
@Slf4j
public class CommonController {

    @Autowired
    private CommonService commonService;

    @Autowired
    SyntheticRatingService syntheticRatingService;

    @Autowired
    OptionValueService optionValueService;

    @Autowired
    CostOfCapitalService costOfCapitalService;

    @GetMapping("/yahoo/company-data")
    //@Cacheable(value = "stock-valuation-cache", key = "#ticker", cacheManager = "cacheManager")
    public ResponseEntity<ResponseDTO<Object>> getDataFromYahooApi(@RequestParam String ticker) {
        try {
            return ResponseGenerator.generateSuccessResponse(commonService.getCompanyDtaFromYahooApi(ticker));
        } catch (RestClientException | BadRequestException exception) {
            return ResponseGenerator.generateBadRequestResponse(exception.getMessage());
        } catch (Exception exception) {
            System.out.println("Exception: " + exception.getMessage());
            return ResponseGenerator.generateExceptionResponseDTO(new Exception("Currently we are not able to provide valuation for this ticker due to lack of some financial data. Please verify the ticker or give a try later."));
        }
    }


    @PostMapping(value = "/yahoo/company-details")
    ResponseEntity<ResponseDTO<Object>> getCompanyInputData(@RequestParam InputDetails inputDetails) {
        try {
            return ResponseGenerator.generateSuccessResponse(commonService.getCompanyDetails(inputDetails));
        } catch (BadRequestException exception) {
            return ResponseGenerator.generateBadRequestResponse(exception.getMessage());
        }
    }

    @PostMapping("/user/login")
    public ResponseEntity<ResponseDTO<Object>> userCredVerification(@RequestBody LoginForm loginForm) {
        try {
            return ResponseGenerator.generateSuccessResponse(commonService.verifyLoginDetailsUser(loginForm));
        } catch (Exception exception) {
            return ResponseGenerator.generateBadRequestResponse(exception.getMessage());
        }

    }

    @PostMapping("/user/signup")
    public ResponseEntity<ResponseDTO<Object>> createSignupUser(@RequestBody SignupForm signupForm) {
        return ResponseGenerator.generateSuccessResponse(commonService.createNewUser(signupForm));
    }


    @Operation(summary = "Calculate R&D convertor", description = "This API is used for R and D convertor calculations.")
    @GetMapping("/rdConvertor/calculate")
    public ResponseEntity<ResponseDTO<Object>> calculateRD(@RequestParam String industry, @RequestParam Double marginalTaxRate, @RequestParam boolean requireRdConverter) {
        if (!requireRdConverter) {
            return ResponseGenerator.generateSuccessResponse(new RDResult(0.00, 0.00, 0.00, 0.00));
        }
        try {
            return ResponseGenerator.generateSuccessResponse(commonService.calculateR_DConvertorValue(industry, marginalTaxRate, Map.of()));
        } catch (RuntimeException e) {
            return ResponseGenerator.generateNotFoundResponse(e.getMessage());
        } catch (Exception e) {
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }
    }


    @PostMapping("/upload")
    public ResponseEntity<String> uploadExcelFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty.");
        }

        // Convert Excel to JSON
        String jsonResult = commonService.convertExcelToJson(file);

        return ResponseEntity.ok(jsonResult);
    }

    @PostMapping("/uploadInput")
    public ResponseEntity<String> uploadExcelInputFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty.");
        }

        // Convert Excel to JSON
        String jsonResult = commonService.convertExcelToJsonn(file);

        return ResponseEntity.ok(jsonResult);
    }

    @PostMapping("/uploadIndustry")
    public ResponseEntity<String> uploadIndustryFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty.");
        }

        // Convert Excel to JSON
        String jsonResult = commonService.convertIndustryAverageExcelToJson(file);

        return ResponseEntity.ok(jsonResult);
    }

    @PostMapping("/uploadEquity")
    public ResponseEntity<String> uploadEquityFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty.");
        }

        // Convert Excel to JSON
        String jsonResult = commonService.convertCountryEquityExcelToJson(file);

        return ResponseEntity.ok(jsonResult);
    }

    @PostMapping("/uploadInputSheet")
    public ResponseEntity<String> uploadInputSheetFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty.");
        }

        // Convert Excel to JSON
        String jsonResult = commonService.convertInputExcelDataToJson(file);

        return ResponseEntity.ok(jsonResult);
    }


    @PostMapping("/uploadInputStat")
    public ResponseEntity<String> uploadInputStatFile(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is empty.");
        }

        // Convert Excel to JSON
        String jsonResult = commonService.convertInputExcelDataToJson(file);

        return ResponseEntity.ok(jsonResult);
    }

    // industry averages US
    @Operation(summary = "Save Industry US data", description = "This API processes the uploaded file for industry US data to save in to tables.")
    @PostMapping("/processIndustryUS")
    public String processIndustryUSData(@RequestParam("file") MultipartFile file) throws Exception {
        
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadIndustryUSData(file);

            // Return success response
            return "Industry averages US data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Industry US data", description = "This API is used to get industry US data.")
    @GetMapping("/getIndustryUS")
    List<IndustryAveragesUS> getAllIndustryUS() {
        return commonService.getAllIndustryUS();
    }


    // industry averages Beta Global
    @Operation(summary = "Save Industry Global data", description = "This API processes the uploaded file for industry Global Data to save in to tables.")
    @PostMapping("/processIndustryGlo")
    public String processIndustryGloData(@RequestParam("file") MultipartFile file) throws Exception {
        
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadIndustryGloData(file);

            // Return success response
            return "Industry averages Beta Global data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Industry Global data", description = "This API is used to get industry Global data.")
    @GetMapping("/getIndustryGlo")
    List<IndustryAveragesGlobal> getAllIndustryGlo() {
        return commonService.getAllIndustryGlo();
    }

    // country equity risk premium
    @Operation(summary = "Save Country Equity data", description = "This API processes the uploaded file for Country Equity Data to save in to tables.")
    @PostMapping("/processCountryEquity")
    public String processCountryEquityData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadCountryEquityData(file);

            // Return success response
            return "Country Equity data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Country Equity data", description = "This API is used to get Country Equity data.")
    @GetMapping("/getCountryEquity")
    List<CountryEquity> getAllCountryEquity() {
        return commonService.getAllCountryEquity();
    }


    // region equity risk premium
    @Operation(summary = "Save Region Equity data", description = "This API processes the uploaded file for Region Equity Data to save in to tables.")
    @PostMapping("/processRegionEquity")
    public String processRegionEquityData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadRegionEquityData(file);

            // Return success response
            return "Region Equity data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Region Equity data", description = "This API is used to get Region Equity data.")
    @GetMapping("/getRegionEquity")
    List<RegionEquity> getAllRegionEquity() {
        return commonService.getAllRegionEquity();
    }

    // R&D Convertor

    @Operation(summary = "Save Amortization Period data", description = "This API processes the uploaded file for R and D convertor Look Up table or Amortization Periods Data to save in to tables.")
    @PostMapping("/processRDConverter")
    public String processRDConverterData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadRDConvertorData(file);

            // Return success response
            return "R&D Convertor's LookUp Table data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Lookup table for Amortization Lives", description = "This API is used to get Lookup table for Amortization Lives.")
    @GetMapping("/getRDConvertor")
    List<RDConvertor> getAllRDConvertor() {
        return commonService.getAllRDConvertor();
    }

    // sector mapping
    @Operation(summary = "Save Sector Mapping data", description = "This API processes the uploaded file for Sector Mapping Data to save in to tables.")
    @PostMapping("/processSectorMapping")
    public String processSectorMappingData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadRegionSectorMapping(file);

            // Return success response
            return "Sector Mapping data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Sector mapping data", description = "This API is used to get Sector Mapping data.")
    @GetMapping("/getSectorMapping")
    List<SectorMapping> getAllSectorMapping() {
        return commonService.getAllSectorMapping();
    }


    // risk free rate
    @Operation(summary = "Save Risk Free Rate data", description = "This API processes the uploaded file for Risk Free Rate Data to save in to tables.")
    @PostMapping("/processRiskFree")
    public String processRiskFreeRateData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadRiskFreeRate(file);

            // Return success response
            return "RiskFreeRate data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Risk Free Rate", description = "This API is used to get Risk Free Rate data.")
    @GetMapping("/getRiskFreeRate")
    List<RiskFreeRate> getAllRiskFreeRate() {
        return commonService.getAllRiskFreeRate();
    }

    // cost of capital
    @Operation(summary = "Save Cost Of Capital data", description = "This API processes the uploaded file for Cost Of Capital Data to save in to tables.")
    @PostMapping("/processCostOfCapital")
    public String processCostOfCapitalData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadCostOfCapital(file);

            // Return success response
            return "Cost of Capital data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Cost Of Capital", description = "This API is used to get Cost Of Capital data.")
    @GetMapping("/getCostOfCapital")
    List<CostOfCapital> getAllCostOfCapital() {
        return commonService.getAllCostOfCapital();
    }


// Synthetic Rating sheet table 1

    @Operation(summary = "Save Large Spread data", description = "This API processes the uploaded file for Large Spread Data to save in to tables.")
    @PostMapping("/processLargeSpread")
    public String processLargeSpreadData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadLargeSpread(file);

            // Return success response
            return "Large Bond Spread data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Large Spread Data", description = "This API is used to get Large Spread Data.")
    @GetMapping("/getLargeSpread")
    List<LargeBondSpread> getAllLargeSpread() {
        return commonService.getAllLargeSpread();
    }


    // Synthetic Rating sheet table 2

    @Operation(summary = "Save Small Spread data", description = "This API processes the uploaded file for Small Spread Data to save in to tables.")
    @PostMapping("/processSmallSpread")
    public String processSmallSpreadData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadSmallSpread(file);

            // Return success response
            return "Small Bond Spread data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Small Spread Data", description = "This API is used to get Small Spread Data.")
    @GetMapping("/getSmallSpread")
    List<SmallBondSpread> getAllSmallSpread() {
        return commonService.getAllSmallSpread();
    }


    // Failure Rate worksheet - Bond rating - Approach-1
    @Operation(summary = "Save Bond Rating data", description = "This API processes the uploaded file for Bond Rating ( Approch -1 , Failure Rate Worksheet )  Data to save in to tables.")
    @PostMapping("/processBondRating")
    public String processBondRatingData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadBondRating(file);

            // Return success response
            return "Bond Rating data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Bond Rating Data", description = "This API is used to get Large Spread Data.")
    @GetMapping("/getBondRating")
    List<BondRating> getAllBondRating() {
        return commonService.getAllBondRating();
    }

    // Failure Rate worksheet - Failure Rate - Approach-2

    @Operation(summary = "Save Failure Rate data", description = "This API processes the uploaded file for Failure Rate (Approch -2 , Failure Rate Worksheet) Data to save in to tables.")
    @PostMapping("/processFailureRate")
    public String processFailureRateData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadFailureRate(file);

            // Return success response
            return "Failure Rate data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }

    @Operation(summary = "Get Failure Rate Data", description = "This API is used to get Failure Rate Data.")
    @GetMapping("/getFailureRate")
    List<FailureRate> getAllFailureRate() {
        return commonService.getAllFailureRate();
    }


    // Input stats Distribution
    @Operation(summary = "Save Inputs Stat data", description = "This API processes the uploaded file for Inputs Stat Data to save in to tables.")
    @PostMapping("/processInputStat")
    public String processInputStatData(@RequestParam("file") MultipartFile file) throws Exception {
        System.out.println("Received file: " + file.getOriginalFilename());
        try {
            // Pass the uploaded file to the service for processing
            commonService.loadInputStat(file);

            // Return success response
            return "Input Stat Distribution data loaded successfully!";
        } catch (IOException e) {
            return "Error processing the file: " + e.getMessage();
        }
    }


    // input data for r and d controller

    @Operation(summary = "Saves Input Multiple Inputs Data for R&D calculations", description = "This API is used for Saving Multiple Inputs Data for R&D calculations by giving array of objects of input data")
    @PostMapping("/processInputData")
    public ResponseEntity<String> saveBatchData(@RequestBody InputRequestBatchDTO inputRequestBatchDTO) {
        try {
            commonService.saveInputData(inputRequestBatchDTO.getInputList());
            return ResponseEntity.ok("Batch data saved successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error saving batch data: " + e.getMessage());
        }
    }

    @Operation(summary = "Get All Inputs Data for R&D calculations", description = "This API is used for Getting All Inputs Data used  R&D calculations ")
    @GetMapping("/getAllInputs")
    public ResponseEntity<?> getAllInputs() {
        return ResponseGenerator.generateSuccessResponse(commonService.getAllInputData());
    }

    @Operation(summary = "Get Input Stat Data", description = "This API is used to get Input Stat Data.")
    @GetMapping("/getInputStat")
    List<InputStatDistribution> getAllInputStat() {
        return commonService.getAllInputStat();
    }

    @Operation(summary = "Saves Single Input Data for", description = "This API is used for Saving Single Inputs Data for R&D calculations by giving a json Data with similar structure of input Class ")
    @PostMapping("/processSingleInput")
    public String saveInputData(@RequestBody InputRequestDTO inputRequestDTO) {
        return commonService.saveSingleInputData(inputRequestDTO);
    }

    @DeleteMapping("/input/{id}")
    public ResponseEntity<String> deleteInput(@PathVariable Long id) {
        try {
            commonService.deleteInput(id);
            return ResponseEntity.ok("Input with id " + id + " and its associated PastExpense records have been deleted.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // operating lease convertor
    @Operation(summary = "Calculate Operating Lease convertor", description = "This API is used for Operating Lease convertor calculations.")
    @GetMapping("/operating-lease-converter")
    public ResponseEntity<?> calculateLease(@RequestParam boolean requiredLeaseConvertor) {

        if (!requiredLeaseConvertor) {
            System.out.println("Lease convertor is not required , Skipping calculation");
            return ResponseGenerator.generateSuccessResponse(new LeaseResultDTO(0.00, 0.00, 0.00, 0.00));
        }
        try {
            return ResponseGenerator.generateSuccessResponse(commonService.calculateOperatingLeaseConvertor());
        } catch (RuntimeException e) {
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }

    }


    // synthetic Rating controller
    @Operation(summary = "Calculate Synthetic Rating", description = "This API is used for Calculating Synthetic Rating Calculation.")
    @GetMapping("/synthetic-rating-convertor")
    public ResponseEntity<?> calculateSyntheticRating(String ticker, boolean requiredLeaseConvertor, @RequestParam(required = false) Double leaseExpenseCurrentYear, @RequestParam(required = false) Double[] commitments, @RequestParam(required = false) Double futureCommitment, @RequestParam(required = false) Double costOfDebt) {
        if (Objects.isNull(ticker)) {
            return ResponseGenerator.generateBadRequestResponse("Enter Company Ticker");
        }
        try {
            return ResponseGenerator.generateSuccessResponse(syntheticRatingService.calculateSyntheticRating(ticker, requiredLeaseConvertor, leaseExpenseCurrentYear, commitments, futureCommitment));
        } catch (RuntimeException e) {
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }
    }


    // cost of capital

    @Operation(summary = "Calculate Cost Of Capital Based on Decile Grouping", description = "This API is used for Calculating Cost Of Capital Based on Decile Grouping.")
    @GetMapping("/cotOfCapital/basedOnDecileGroup")
    public ResponseEntity<?> calculateCostOfCapital(@RequestParam String region, @RequestParam String riskGrouping) {

        try {
            return ResponseGenerator.generateSuccessResponse(costOfCapitalService.costOfCapitalBasedOnDecile(region, riskGrouping));
        } catch (RuntimeException e) {
            log.error("Error occurred while processing the request: {}", e.getMessage(), e); // Log the error with the stack trace.
            return ResponseGenerator.generateNotFoundResponse(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e); // Log unexpected errors.
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }


    }

    @Operation(summary = "Calculate Cost Of Capital Based on Industry", description = "This API is used for Calculating Cost Of Capital Based on Industry.")
    @GetMapping("/cotOfCapital/byIndustry")
    public ResponseEntity<?> calculateCostOfCapitalByIndustry(@RequestParam String ticker, @RequestParam String industry) {

        if (!industry.equals("Single Business(US)") && !industry.equals("Single Business(Global)")) {
            return ResponseGenerator.generateNotFoundResponse("Please enter valid industry, valid industry: Single Business(US) OR Single Business(Global)");
        }
        try {
            return ResponseGenerator.generateSuccessResponse(costOfCapitalService.costOfCapitalByIndustry(ticker, industry));
        } catch (RuntimeException e) {
            log.error("Error occurred while processing the request: {}", e.getMessage(), e); // Log the error with the stack trace.
            return ResponseGenerator.generateNotFoundResponse(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e); // Log unexpected errors.
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }

    }

    //    option value
    @Operation(summary = "Calculate Option Value", description = "This API is used for Calculating Option value.")
    @GetMapping("/option-value")
    public ResponseEntity<?> calculateOptionValue(@RequestParam String ticker, @RequestParam Double strikePrice, @RequestParam Double avgMaturity, @RequestParam Double optionStanding, @RequestParam Double standardDeviation) {
        try {
            return ResponseGenerator.generateSuccessResponse(optionValueService.calculateOptionValue(ticker, strikePrice, avgMaturity, optionStanding, standardDeviation));
        } catch (RuntimeException e) {
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }
    }


}
