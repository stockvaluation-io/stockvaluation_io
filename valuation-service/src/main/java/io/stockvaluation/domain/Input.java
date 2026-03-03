package io.stockvaluation.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Input {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticker;
    @Column(name = "date_of_valuation")
    private String dateOfValuation;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "industry_us", nullable = false)
    private String industryUS;

    @Column(name = "industry_glo", nullable = false)
    private String industryGlo;

    @Column(name = "current_year_expense", nullable = false)
    private Double currentYearExpense;

    @Column(name = "total_revenue", nullable = false)
    private Double totalRevenue;

    @Column(name = "operating_income", nullable = false)
    private Double operatingIncome;

    @Column(name = "has_r_and_d_expenses_to_capitalize", nullable = false)
    private Boolean hasRAndDExpensesToCapitalize;

    @Column(name = "marginal_tax_rate", nullable = false)
    private Double marginalTaxRate;

    // One-to-Many relationship with PastExpense
    @OneToMany(mappedBy = "input", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference // Prevents circular reference while serializing
    private List<PastExpense> pastExpense;

}
