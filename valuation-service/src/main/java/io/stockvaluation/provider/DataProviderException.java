package io.stockvaluation.provider;

/**
 * Exception thrown when a DataProvider operation fails.
 */
public class DataProviderException extends RuntimeException {

    private final String providerName;
    private final String ticker;

    public DataProviderException(String providerName, String ticker, String message) {
        super(String.format("[%s] Failed for ticker '%s': %s", providerName, ticker, message));
        this.providerName = providerName;
        this.ticker = ticker;
    }

    public DataProviderException(String providerName, String ticker, String message, Throwable cause) {
        super(String.format("[%s] Failed for ticker '%s': %s", providerName, ticker, message), cause);
        this.providerName = providerName;
        this.ticker = ticker;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getTicker() {
        return ticker;
    }
}
