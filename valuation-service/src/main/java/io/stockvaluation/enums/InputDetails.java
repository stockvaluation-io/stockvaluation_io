package io.stockvaluation.enums;

import lombok.Getter;

@Getter
public enum InputDetails {

    INFO("Info"),
    INCOME_STATEMENT("Income Statement"),
    BALANCE_SHEET("Balance Sheet");

    private String name;

    InputDetails(String name) {
        this.name = name;
    }
}
