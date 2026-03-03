package io.stockvaluation.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Objects;

@Slf4j
@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of("stock-valuation-cache"));
        return cacheManager;
    }

    @Scheduled(fixedRate = 24 * 60 * 60 * 1000) // 48 hours in milliseconds
    public void evictCache() {
        log.info("stock-valuation-cache -> clear");
        ConcurrentMapCacheManager cacheManager = (ConcurrentMapCacheManager) cacheManager();
        Objects.requireNonNull(cacheManager.getCache("stock-valuation-cache")).clear();
    }
}
