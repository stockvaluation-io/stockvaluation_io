package io.stockvaluation.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class OptionValueResultDTO {

    private Double valuePerOption;
    private Double valueOfAllOptionsOutstanding;

    public OptionValueResultDTO(Double valuePerOption, Double valueOfAllOptionsOutstanding) {
        this.valuePerOption = valuePerOption;
        this.valueOfAllOptionsOutstanding = valueOfAllOptionsOutstanding;
    }

}
