package io.stockvaluation.enums;

import lombok.Getter;

@Getter
public enum CompanyName {

    AAPL("Apple"),
    AMZN("Amazon"),
    NFLX("Netflix");

    private String name;

    CompanyName(String name) {
        this.name = name;
    }

    public static boolean containsCompanyName(String companyName) {
        if (companyName == null || companyName.isEmpty()) {
            return false;
        }
        for (CompanyName company : CompanyName.values()) {
            if (companyName.equalsIgnoreCase(company.name) || companyName.toUpperCase().contains(company.name())) {
                return true;
            }
        }
        return false;
    }
}
