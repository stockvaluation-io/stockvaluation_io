package io.stockvaluation.valuationOutputTest;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.controller.AutomatedDCFAnalysisController;
import io.stockvaluation.domain.RDConvertor;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.ResponseDTO;
import io.stockvaluation.repository.RDConvertorRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import io.stockvaluation.service.CommonService;
import io.stockvaluation.service.ValuationOutputService;
import io.stockvaluation.utils.ResponseGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class ValuationOutputTest {

    @InjectMocks
    private AutomatedDCFAnalysisController calculationTestController;

    @Mock
    private ValuationOutputService valuationOutputService;

    @InjectMocks
    private CommonService commonService;
    @Mock
    private ResponseGenerator responseGenerator;

    @Mock
    private SectorMappingRepository sectorMappingRepository;

    @Mock
    private RDConvertorRepository rdConvertorRepository;

    @Mock
    private Map<String, Double> researchAndDevelopmentMap;

    @BeforeEach
    public void setUp() {
        // no use rn
    }

    /*
    @Test
    void testCalculateRD_success() {
        // Arrange: Mocking service response
        RDResult mockResult = new RDResult(0.00, 0.00, 0.00, 0.00);
        Mockito.when(commonService.calculateR_DConvertorValue("internet-retail", 25.0))
                .thenReturn(mockResult);

        // Act: Call the controller method
        ResponseEntity<ResponseDTO<Object>> response = calculationTestController.calculateRD("internet-retail", 25.0, true);

        // Assert: Verify the response
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Response status should be 200 OK");

        ResponseDTO<?> responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");

        RDResult result = (RDResult) responseBody.getData();
        assertNotNull(result, "RDResult should not be null");
        assertEquals(0.00, result.getTotalResearchAsset(), "Total Research Asset should be 0.00");
        assertEquals(0.00, result.getTotalAmortization(), "Total Amortization should be 0.00");
        assertEquals(0.00, result.getAdjustmentToOperatingIncome(), "Adjustment to Operating Income should be 0.00");
        assertEquals(0.00, result.getTaxEffect(), "Tax Effect should be 0.00");
    }

    @Test
    void testCalculateRD_runtimeException() {
        // Arrange: Mocking a RuntimeException
        Mockito.when(commonService.calculateR_DConvertorValue("UnknownIndustry", 20.0))
                .thenThrow(new RuntimeException("Sector mapping not found for industry"));

        // Act: Call the controller method
        ResponseEntity<ResponseDTO<Object>> response = calculationTestController.calculateRD("UnknownIndustry", 20.0, true);

        // Assert: Verify the response
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ResponseDTO<Object> responseBody = response.getBody();
        assertNotNull(responseBody);
        assertEquals("Sector mapping not found for industry", responseBody.getMessage());
    }*/

    // service testing


    /*
    @Test
    void testCalculateR_DConvertorValueForAMZN_success() {
        // Arrange
        String industry = "internet-retail";
        double marginalTaxRate = 25.0;

        // Mock SectorMapping
        SectorMapping mockSectorMapping = Mockito.mock(SectorMapping.class);
        Mockito.when(mockSectorMapping.getIndustryAsPerExcel()).thenReturn("Retail (General)");
        Mockito.when(sectorMappingRepository.findByIndustryName(industry))
                .thenReturn(mockSectorMapping);

        // Mock RDConvertor
        RDConvertor mockRDConvertor = Mockito.mock(RDConvertor.class);
        Mockito.when(mockRDConvertor.getAmortizationPeriod()).thenReturn(3);
        Mockito.when(rdConvertorRepository.findAmortizationPeriod("Retail (General)"))
                .thenReturn(mockRDConvertor);

        // Mock R&D Expense Map
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-0")).thenReturn(0.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-1")).thenReturn(0.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-2")).thenReturn(0.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-3")).thenReturn(0.00);

        // Act
        RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(0.00, result.getTotalResearchAsset(), "Total Research asset not matched");
        assertEquals(0.00, result.getTotalAmortization(), "Total Amortization not matched");
        assertEquals(0.00, result.getAdjustmentToOperatingIncome(), "Adjustment to operating income is not matched");
        assertEquals(0.00, result.getTaxEffect(), "Tax Effect not matched");
    }

    // for aaple

    @Test
    void testCalculateR_DConvertorValueForAAPL_success() {
        // Arrange
        String industry = "consumer-electronics";
        double marginalTaxRate = 25.0;

        // Mock SectorMapping
        SectorMapping mockSectorMapping = Mockito.mock(SectorMapping.class);
        Mockito.when(mockSectorMapping.getIndustryAsPerExcel()).thenReturn("Electronics");
        Mockito.when(sectorMappingRepository.findByIndustryName(industry))
                .thenReturn(mockSectorMapping);

        // Mock RDConvertor
        RDConvertor mockRDConvertor = Mockito.mock(RDConvertor.class);
        Mockito.when(mockRDConvertor.getAmortizationPeriod()).thenReturn(4);
        Mockito.when(rdConvertorRepository.findAmortizationPeriod("Electronics"))
                .thenReturn(mockRDConvertor);

        // Mock R&D Expense Map
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-0")).thenReturn(31370000000.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-1")).thenReturn(31370000000.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-2")).thenReturn(29915000000.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-3")).thenReturn(26251000000.00);
        Mockito.when(researchAndDevelopmentMap.get("currentR&D-4")).thenReturn(21914000000.00);


        RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate);


        assertNotNull(result, "Result should not be null");
        assertEquals(72410250000.00, result.getTotalResearchAsset(), "Total Research asset not matched");
        assertEquals(19520000000.00, result.getTotalAmortization(), "Total Amortization not matched");
        assertEquals(11850000000.00, result.getAdjustmentToOperatingIncome(), "Adjustment to operating income is not matched");
        assertEquals(2962500000.00, result.getTaxEffect(), "Tax Effect not matched");
    }

     */

}
