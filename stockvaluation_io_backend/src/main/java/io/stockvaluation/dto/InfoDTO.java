package io.stockvaluation.dto;

import java.time.LocalDate;

public class InfoDTO {

    private String companyName;

    private String ticker;

    private String website;

    private LocalDate dateOfValuation;

    private String countryOfIncorporation;

    private String industryUs;

    private String industryGlobal;

    private Double noOfShareOutstanding;

    private Double stockPrice;

    private Double lowestStockPrice;

    private Double highestStockPrice;

    private Double priceChangeFromLastStock;

    private Double percentageChangeFromLastStock;

    private Double priceChangeCurrentStock;

    private Double percentageChangeCurrentStock;

    public Double getPriceChangeFromLastStock() {
        return priceChangeFromLastStock;
    }

    public void setPriceChangeFromLastStock(Double priceChangeFromLastStock) {
        this.priceChangeFromLastStock = priceChangeFromLastStock;
    }

    public Double getPercentageChangeFromLastStock() {
        return percentageChangeFromLastStock;
    }

    public void setPercentageChangeFromLastStock(Double percentageChangeFromLastStock) {
        this.percentageChangeFromLastStock = percentageChangeFromLastStock;
    }

    public Double getPriceChangeCurrentStock() {
        return priceChangeCurrentStock;
    }

    public void setPriceChangeCurrentStock(Double priceChangeCurrentStock) {
        this.priceChangeCurrentStock = priceChangeCurrentStock;
    }

    public Double getPercentageChangeCurrentStock() {
        return percentageChangeCurrentStock;
    }

    public void setPercentageChangeCurrentStock(Double percentageChangeCurrentStock) {
        this.percentageChangeCurrentStock = percentageChangeCurrentStock;
    }

    public Double getLowestStockPrice() {
        return lowestStockPrice;
    }

    public void setLowestStockPrice(Double lowestStockPrice) {
        this.lowestStockPrice = lowestStockPrice;
    }

    public Double getHighestStockPrice() {
        return highestStockPrice;
    }

    public void setHighestStockPrice(Double highestStockPrice) {
        this.highestStockPrice = highestStockPrice;
    }

    public Double getStockPrice() {
        return stockPrice;
    }

    public void setStockPrice(Double stockPrice) {
        this.stockPrice = stockPrice;
    }

    public Double getNoOfShareOutstanding() {
        return noOfShareOutstanding;
    }

    public void setNoOfShareOutstanding(Double noOfShareOutstanding) {
        this.noOfShareOutstanding = noOfShareOutstanding;
    }

    public String getIndustryGlobal() {
        return industryGlobal;
    }

    public void setIndustryGlobal(String industryGlobal) {
        this.industryGlobal = industryGlobal;
    }

    public String getIndustryUs() {
        return industryUs;
    }

    public void setIndustryUs(String industryUs) {
        this.industryUs = industryUs;
    }

    public String getCountryOfIncorporation() {
        return countryOfIncorporation;
    }

    public void setCountryOfIncorporation(String countryOfIncorporation) {
        this.countryOfIncorporation = countryOfIncorporation;
    }

    public LocalDate getDateOfValuation() {
        return dateOfValuation;
    }

    public void setDateOfValuation(LocalDate dateOfValuation) {
        this.dateOfValuation = dateOfValuation;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public InfoDTO() {
    }
}
