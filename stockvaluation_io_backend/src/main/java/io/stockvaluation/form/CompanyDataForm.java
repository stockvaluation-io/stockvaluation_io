package io.stockvaluation.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CompanyDataForm {

    // Basic company info
    private BasicInfoDataForm companyInfo;

    // Financial data related to the company
//    private FinancialDataForm financialDataForm;

    // Value drivers for the company
//    private ValueDriversForm valueDriversForm;

    // Market numbers
//    private MarketNumbersForm marketNumbersForm;

    // Employee option details
//    private EmployeeOptionsForm employeeOptionsForm;

    // Default valuation assumptions
    private UserAssumptionsForm userAssumptionsForm;
}

