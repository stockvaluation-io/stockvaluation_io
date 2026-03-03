package io.stockvaluation.form;

public class ValuationInputForm {

    private Double revenueNextYear;

    private Double operatingIncomeTTM;

    private Double minorityInterestTTM;

    private Double nonOperatingAssetTTM;

    private Double stockPrice;

    public Double getRevenueNextYear() {
        return revenueNextYear;
    }

    public void setRevenueNextYear(Double revenueNextYear) {
        this.revenueNextYear = revenueNextYear;
    }

    public Double getOperatingIncomeTTM() {
        return operatingIncomeTTM;
    }

    public void setOperatingIncomeTTM(Double operatingIncomeTTM) {
        this.operatingIncomeTTM = operatingIncomeTTM;
    }

    public Double getMinorityInterestTTM() {
        return minorityInterestTTM;
    }

    public void setMinorityInterestTTM(Double minorityInterestTTM) {
        this.minorityInterestTTM = minorityInterestTTM;
    }

    public Double getNonOperatingAssetTTM() {
        return nonOperatingAssetTTM;
    }

    public void setNonOperatingAssetTTM(Double nonOperatingAssetTTM) {
        this.nonOperatingAssetTTM = nonOperatingAssetTTM;
    }

    public Double getStockPrice() {
        return stockPrice;
    }

    public void setStockPrice(Double stockPrice) {
        this.stockPrice = stockPrice;
    }

    public ValuationInputForm() {
    }
}
