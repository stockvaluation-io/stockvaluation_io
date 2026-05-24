package io.stockvaluation.controller;

import io.stockvaluation.dto.ResponseDTO;
import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.dto.valuationoutput.AssumptionTransparencyDTO;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.dto.valuationoutput.FinancialDTO;
import io.stockvaluation.dto.valuationoutput.TerminalValueDTO;
import io.stockvaluation.exception.InsufficientFinancialDataException;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.service.ValuationWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomatedDCFAnalysisControllerTest {

    @Mock
    private ValuationWorkflowService valuationWorkflowService;

    private AutomatedDCFAnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new AutomatedDCFAnalysisController(valuationWorkflowService);
    }

    @Test
    void postValuation_success_returnsOkResponse() {
        ValuationOutputDTO output = new ValuationOutputDTO();
        when(valuationWorkflowService.getValuation(eq("AAPL"), any(FinancialDataInput.class), eq(false)))
                .thenReturn(output);

        ResponseEntity<?> response = controller.getValuationOutput("AAPL", new FinancialDataInput());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertSame(output, body.getData());
    }

    @Test
    void postValuation_msftContractIncludesDcfAssumptionsAndVersionMetadata() {
        ValuationOutputDTO output = new ValuationOutputDTO();
        output.setCompanyName("Microsoft Corporation");
        output.setCurrency("USD");
        output.setPrimaryModel(io.stockvaluation.enums.CashflowType.FCFF);
        output.setGrowthPattern(io.stockvaluation.enums.GrowthPattern.TWO_STAGE);
        output.setProjectionYears(10);

        CompanyDTO company = new CompanyDTO();
        company.setEstimatedValuePerShare(412.34);
        company.setPrice(390.0);
        output.setCompanyDTO(company);

        FinancialDTO financial = new FinancialDTO();
        financial.setIntrinsicValue(412.34);
        financial.setRevenueGrowthRate(new Double[] { null, 10.0, 8.0, 7.0 });
        financial.setEbitOperatingMargin(new Double[] { 42.0, 43.0, 44.0 });
        financial.setSalesToCapitalRatio(new Double[] { null, 2.4, 2.3 });
        financial.setCostOfCapital(new Double[] { 8.5, 8.4, 8.3 });
        output.setFinancialDTO(financial);

        TerminalValueDTO terminal = new TerminalValueDTO();
        terminal.setGrowthRate(3.0);
        terminal.setCostOfCapital(8.0);
        output.setTerminalValueDTO(terminal);

        AssumptionTransparencyDTO transparency = new AssumptionTransparencyDTO();
        transparency.setGrowthPattern("TWO_STAGE");
        transparency.setProjectionYears(10);
        transparency.setDiscountRate(new AssumptionTransparencyDTO.DiscountRate(
                4.5,
                4.77,
                8.5,
                8.0,
                "risk-free rate plus equity risk premium adjustments",
                "valuation-service",
                "Damodaran",
                "valuation-service"));
        transparency.setOperatingAssumptions(new AssumptionTransparencyDTO.OperatingAssumptions(
                7.0,
                43.0,
                44.0,
                5.0,
                2.4,
                2.3,
                "valuation-service",
                "valuation-service",
                "valuation-service",
                "growth anchor and historical data",
                "current margin and target margin",
                "industry capital efficiency"));
        transparency.setGrowthAnchor(new AssumptionTransparencyDTO.GrowthAnchor(
                "software",
                "Software",
                "United States",
                2026,
                100.0,
                0.08,
                0.07,
                0.09,
                0.82,
                0.04,
                0.08,
                0.12,
                "Damodaran historical growth"));
        output.setAssumptionTransparency(transparency);

        when(valuationWorkflowService.getValuation(eq("MSFT"), any(FinancialDataInput.class), eq(false)))
                .thenReturn(output);

        ResponseEntity<?> response = controller.getValuationOutput("MSFT", new FinancialDataInput());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        ValuationOutputDTO data = (ValuationOutputDTO) body.getData();
        assertEquals("Microsoft Corporation", data.getCompanyName());
        assertEquals(412.34, data.getCompanyDTO().getEstimatedValuePerShare());
        assertEquals(412.34, data.getFinancialDTO().getIntrinsicValue());
        assertEquals(7.0,
                data.getAssumptionTransparency().getOperatingAssumptions().getRevenueGrowthRateYears2To5());
        assertEquals("software", data.getAssumptionTransparency().getGrowthAnchor().getEntity());
        assertEquals(io.stockvaluation.enums.CashflowType.FCFF, data.getPrimaryModel());
        assertEquals(io.stockvaluation.enums.GrowthPattern.TWO_STAGE, data.getGrowthPattern());
        assertEquals(10, data.getProjectionYears());
    }

    @Test
    void postValuation_insufficientData_returns422() {
        when(valuationWorkflowService.getValuation(eq("AAPL"), any(FinancialDataInput.class), eq(false)))
                .thenThrow(new InsufficientFinancialDataException("missing revenue"));

        ResponseEntity<?> response = controller.getValuationOutput("AAPL", new FinancialDataInput());

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("missing revenue", body.getMessage());
    }

    @Test
    void postValuation_runtimeException_returns500() {
        when(valuationWorkflowService.getValuation(eq("AAPL"), any(FinancialDataInput.class), eq(false)))
                .thenThrow(new RuntimeException("boom"));

        ResponseEntity<?> response = controller.getValuationOutput("AAPL", new FinancialDataInput());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("boom", body.getMessage());
    }

    @Test
    void getValuation_success_returnsOkResponse() {
        ValuationOutputDTO output = new ValuationOutputDTO();
        when(valuationWorkflowService.getValuation("MSFT", null, true)).thenReturn(output);

        ResponseEntity<?> response = controller.getValuationOutputWithStory("MSFT");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertTrue(body.isSuccess());
        assertSame(output, body.getData());
    }

    @Test
    void getValuation_insufficientData_returns422() {
        when(valuationWorkflowService.getValuation("MSFT", null, true))
                .thenThrow(new InsufficientFinancialDataException("insufficient dimensions"));

        ResponseEntity<?> response = controller.getValuationOutputWithStory("MSFT");

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("insufficient dimensions", body.getMessage());
    }

    @Test
    void getValuation_runtimeException_returns500() {
        when(valuationWorkflowService.getValuation("MSFT", null, true))
                .thenThrow(new RuntimeException("internal"));

        ResponseEntity<?> response = controller.getValuationOutputWithStory("MSFT");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ResponseDTO<?> body = (ResponseDTO<?>) response.getBody();
        assertNotNull(body);
        assertFalse(body.isSuccess());
        assertEquals("internal", body.getMessage());
    }
}
