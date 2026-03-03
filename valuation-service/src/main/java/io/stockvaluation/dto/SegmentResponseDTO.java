package io.stockvaluation.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SegmentResponseDTO {
    public List<Segment> segments; 

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Segment {
        public String sector;
        public String industry;
        public List<String> components;
        public Double mappingScore;
        public Double revenueShare;
        public Double operatingMargin;
    }  
}

