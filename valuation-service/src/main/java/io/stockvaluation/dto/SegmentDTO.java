package io.stockvaluation.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SegmentDTO {
    private String ticker;
    private String name;
    private String industry;
}

