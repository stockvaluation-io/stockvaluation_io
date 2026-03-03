package io.stockvaluation.domain;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class PastExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "input_id", nullable = false)
    @JsonBackReference
    private Input input;  // Foreign key relationship with AmznInput

    @Column(name = "expense", nullable = false)
    private Double expense;  // Expense value for this record


}
