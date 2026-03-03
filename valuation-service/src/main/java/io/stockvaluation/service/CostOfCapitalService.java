package io.stockvaluation.service;

import io.stockvaluation.domain.CostOfCapital;
import io.stockvaluation.domain.IndustryAveragesGlobal;
import io.stockvaluation.domain.IndustryAveragesUS;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.repository.CostOfCapitalRepository;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.IndustryAveragesUSRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CostOfCapitalService {

    private final CostOfCapitalRepository costOfCapitalRepository;

    private final IndustryAveragesUSRepository industryAvgUSRepository;

    private final IndustryAveragesGlobalRepository industryAvgGloRepository;

    private final CommonService commonService;

    private final SectorMappingRepository sectorMappingRepository;
    
    private final DecimalFormat df = new DecimalFormat("0.00");

    public String costOfCapitalBasedOnDecile(String region, String riskGrouping) {

        Optional<CostOfCapital> costOfCapitalOpt = costOfCapitalRepository.findByRegion(region);

        // Check if the region was found
        if (costOfCapitalOpt.isPresent()) {
            CostOfCapital costOfCapital = costOfCapitalOpt.get();

            // Return the appropriate value based on the riskGrouping
            switch (riskGrouping) {
                case "First Quartile":
                    return costOfCapital.getFirstQuartile();
                case "First Decile":
                    return costOfCapital.getFirstDecile();
                case "Median":
                    return costOfCapital.getMedian();
                case "Third Quartile":
                    return costOfCapital.getThirdQuartile();
                case "Ninth Decile":
                    return costOfCapital.getNinthDecile();
                default:
                    return "Risk Grouping not Found! Please enter a valid Risk grouping";

            }
        }
        return "Region not found";

    }

    public String costOfCapitalByIndustry(String ticker, String industry) {
        CompanyDataDTO companyDataDTO = commonService.getCompanyDataFromProvider(ticker);
        if (Objects.isNull(companyDataDTO)) {
            log.info("No company data found for the entered ticker");
            throw new RuntimeException("no company data found for the ticker");
        }
        
        double costOfCapital = 0;
        double riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate();
        
        if (industry.equals("Single Business(US)")) {
            SectorMapping sectorMapping = sectorMappingRepository
                .findByIndustryName(companyDataDTO.getBasicInfoDataDTO().getIndustryUs());
            String industryName = sectorMapping.getIndustryAsPerExcel();
            IndustryAveragesUS industryAveragesUS = industryAvgUSRepository.findByIndustryName(industryName);
            costOfCapital = industryAveragesUS.getCostOfCapital();
            
        } else if (industry.equals("Single Business(Global)")) {
            SectorMapping sectorMapping = sectorMappingRepository
                .findByIndustryName(companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal());
            String industryName = sectorMapping.getIndustryAsPerExcel();
            IndustryAveragesGlobal industryAveragesGlobal = industryAvgGloRepository.findByIndustryName(industryName);
            costOfCapital = industryAveragesGlobal.getCostOfCapital();
        }
        
        return df.format(costOfCapital + (riskFreeRate - commonService.resolveBaselineRiskFreeRate()));
    }
    
    private double calculateWeightedCostOfCapitalUS(List<String> industryNames, List<Double> revenues) {
        double totalRevenue = revenues.stream().mapToDouble(Double::doubleValue).sum();
        
        if (totalRevenue == 0) {
            throw new RuntimeException("Total revenue is zero for multi-business calculation");
        }
        
        double weightedCostOfCapital = 0;
        
        for (int i = 0; i < industryNames.size(); i++) {
            String industryName = industryNames.get(i);
            double revenue = revenues.get(i);
            double segmentWeight = revenue / totalRevenue;
            
            SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(industryName);
            String industryExcelName = sectorMapping.getIndustryAsPerExcel();
            
            IndustryAveragesUS industryAveragesUS = industryAvgUSRepository.findByIndustryName(industryExcelName);
            double segmentCostOfCapital = industryAveragesUS.getCostOfCapital();
            
            weightedCostOfCapital += (segmentWeight * segmentCostOfCapital);
            
            log.info("Segment: {}, Weight: {}, Revenue: {}, Cost of Capital: {}", 
                industryExcelName, segmentWeight, revenue, segmentCostOfCapital);
        }
        
        return weightedCostOfCapital;
    }
    
    private double calculateWeightedCostOfCapitalGlobal(List<String> industryNames, List<Double> revenues) {
        double totalRevenue = revenues.stream().mapToDouble(Double::doubleValue).sum();
        
        if (totalRevenue == 0) {
            throw new RuntimeException("Total revenue is zero for multi-business calculation");
        }
        
        double weightedCostOfCapital = 0;
        
        for (int i = 0; i < industryNames.size(); i++) {
            String industryName = industryNames.get(i);
            double revenue = revenues.get(i);
            double segmentWeight = revenue / totalRevenue;
            
            SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(industryName);
            String industryExcelName = sectorMapping.getIndustryAsPerExcel();
            
            IndustryAveragesGlobal industryAveragesGlobal = industryAvgGloRepository.findByIndustryName(industryExcelName);
            double segmentCostOfCapital = industryAveragesGlobal.getCostOfCapital();
            
            weightedCostOfCapital += (segmentWeight * segmentCostOfCapital);
            
            log.info("Segment: {}, Weight: {}, Revenue: {}, Cost of Capital: {}", 
                industryExcelName, segmentWeight, revenue, segmentCostOfCapital);
        }
        
        return weightedCostOfCapital;
    }

    private String formatIfValid(Number value) {
        if (value != null) {
            return df.format(value);  // Only format if value is not null
        } else {
            return "Value not available";  // Return a fallback message if the value is null
        }
    }

}
