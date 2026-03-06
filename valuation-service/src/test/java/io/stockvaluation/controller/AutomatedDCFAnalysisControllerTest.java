package io.stockvaluation.controller;

import io.stockvaluation.dto.ResponseDTO;
import io.stockvaluation.dto.ValuationOutputDTO;
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
