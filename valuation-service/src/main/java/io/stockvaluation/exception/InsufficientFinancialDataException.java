package io.stockvaluation.exception;

public class InsufficientFinancialDataException extends RuntimeException {

    public InsufficientFinancialDataException(String message) {
        super(message);
    }
}
