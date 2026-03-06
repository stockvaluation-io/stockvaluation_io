package io.stockvaluation.service;

import io.stockvaluation.dto.GrowthAnchorDTO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GrowthAnchorServiceTest {

    @Test
    void getAnchorByYahooIndustry_resolvesViaIndustryMapping() {
        GrowthAnchorService service = new GrowthAnchorService();
        service.init();

        Optional<GrowthAnchorDTO> anchor = service.getAnchorByYahooIndustry("consumer-electronics", "United States");
        assertTrue(anchor.isPresent(), "Expected mapped growth anchor for consumer-electronics");
    }

    @Test
    void getAnchorByYahooIndustry_prefersLatestYearPerRegion() {
        GrowthAnchorService service = new GrowthAnchorService();
        service.init();

        Optional<GrowthAnchorDTO> anchor = service.getAnchorByYahooIndustry("Software - Infrastructure",
                "United States");
        assertTrue(anchor.isPresent(), "Expected mapped growth anchor for Software - Infrastructure");

        GrowthAnchorDTO dto = anchor.get();
        assertEquals(2026, dto.getYear(), "Anchor should use latest available year for the region");
        assertNotNull(dto.getP25());
        assertNotNull(dto.getP50());
        assertNotEquals(dto.getP25(), dto.getP50(), "Dispersion band should not collapse for latest-year row");
    }
}
