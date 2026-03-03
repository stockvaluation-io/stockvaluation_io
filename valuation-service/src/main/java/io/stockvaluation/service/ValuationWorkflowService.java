package io.stockvaluation.service;

import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.dto.valuationoutput.CalibrationResultDTO;
import io.stockvaluation.form.FinancialDataInput;

public interface ValuationWorkflowService {

    ValuationOutputDTO getValuation(String ticker, FinancialDataInput financialDataInputOverrides, boolean addStory);

    CalibrationResultDTO calibrateToMarketPrice(String ticker, FinancialDataInput financialDataInput,
            Double currentPrice);
}
