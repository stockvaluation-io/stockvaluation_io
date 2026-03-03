package io.stockvaluation.controller;

import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.exception.InsufficientFinancialDataException;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.service.ValuationWorkflowService;
import io.stockvaluation.utils.ResponseGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/automated-dcf-analysis")
public class AutomatedDCFAnalysisController {

    private final ValuationWorkflowService valuationWorkflowService;

    @PostMapping("/{ticker}/valuation")
    public ResponseEntity<?> getValuationOutput(@PathVariable String ticker,
                                                @RequestBody FinancialDataInput financialDataInputOverrides) {
        try {
            ValuationOutputDTO valuationOutputDTO = valuationWorkflowService.getValuation(
                    ticker,
                    financialDataInputOverrides,
                    false
            );
            return ResponseGenerator.generateSuccessResponse(valuationOutputDTO);
        } catch (InsufficientFinancialDataException e) {
            log.warn("Unprocessable valuation input in POST /{}/valuation: {}", ticker, e.getMessage());
            return ResponseGenerator.generateUnprocessableEntityResponse(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error in POST /{}/valuation", ticker, e);
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }
    }

    @GetMapping("/{ticker}/valuation")
    public ResponseEntity<?> getValuationOutputWithStory(@PathVariable String ticker) {
        try {
            ValuationOutputDTO valuationOutputDTO = valuationWorkflowService.getValuation(
                    ticker,
                    null,
                    true
            );
            return ResponseGenerator.generateSuccessResponse(valuationOutputDTO);
        } catch (InsufficientFinancialDataException e) {
            log.warn("Unprocessable valuation input in GET /{}/valuation: {}", ticker, e.getMessage());
            return ResponseGenerator.generateUnprocessableEntityResponse(e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error in GET /{}/valuation", ticker, e);
            return ResponseGenerator.generateExceptionResponseDTO(e);
        }
    }
}
