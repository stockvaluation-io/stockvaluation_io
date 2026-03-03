import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { UniversalAssetLoaderService, AssetCacheService, AssetPathService } from '../infrastructure';
import { 
    ContentSection, 
    PrivacyContent, 
    FAQItem, 
    FAQContent,
    TopStock,
    Quote,
    QuotesContent
} from '../../interfaces';
import { AssetType, AssetFormat } from '../../config';

@Injectable({
    providedIn: 'root'
})
export class ContentService {
    constructor(
        private assetLoader: UniversalAssetLoaderService,
        private assetCache: AssetCacheService,
        private assetPathService: AssetPathService
    ) {}

    /**
     * Load an asset with caching support
     * @param filename Name of the JSON file to load
     * @param fallbackData Optional fallback data if loading fails
     * @returns Observable with the parsed JSON content
     */
    private loadAssetWithCache<T>(filename: string, fallbackData?: T): Observable<T> {
        const cacheKey = `content-asset-${filename}`;
        
        return this.assetCache.getOrSet(
            cacheKey,
            () => this.assetLoader.loadAsset<T>(filename, {
                enableFallback: fallbackData !== undefined,
                fallbackData
            }),
            {
                ttl: 5 * 60 * 1000, // 5 minutes cache
                maxSize: 20 // Max 20 content assets cached
            }
        );
    }

    getPrivacyContent(): Observable<PrivacyContent> {
        const fallbackData: PrivacyContent = {
            lastUpdated: new Date().toISOString(),
            hero: {
                title: 'Privacy Policy',
                subtitle: 'Privacy information currently unavailable',
                summary: {
                    title: 'Summary',
                    items: []
                }
            },
            sections: [],
            contact: {
                title: 'Contact',
                description: 'Privacy policy temporarily unavailable',
                email: 'stockvaluation.io@gmail.com',
                headerIcon: 'mail',
                buttonIcon: 'external-link'
            }
        };
        
        return this.loadAssetWithCache<PrivacyContent>('privacy.json', fallbackData);
    }

    getFAQContent(): Observable<FAQContent> {
        const fallbackData: FAQContent = {
            hero: {
                title: 'Frequently Asked Questions',
                subtitle: 'FAQ content currently unavailable'
            },
            categories: [],
            faqs: [],
            contact: {
                title: 'Contact',
                description: 'FAQ content temporarily unavailable',
                email: 'stockvaluation.io@gmail.com'
            }
        };
        
        return this.loadAssetWithCache<FAQContent>('faq.json', fallbackData);
    }

    getTopStocks(): Observable<TopStock[]> {
        const fallbackData: TopStock[] = [];
        return this.loadAssetWithCache<TopStock[]>('top-stocks.json', fallbackData);
    }

    getQuotes(): Observable<QuotesContent> {
        const fallbackData: QuotesContent = {
            title: 'Investment Quotes',
            subtitle: 'Inspirational quotes about investing',
            quotes: [
                {
                    text: 'The stock market is designed to transfer money from the active to the patient.',
                    author: 'Warren Buffett'
                },
                {
                    text: 'Risk comes from not knowing what you are doing.',
                    author: 'Warren Buffett'
                }
            ]
        };
        
        return this.loadAssetWithCache<QuotesContent>('damodaran-quotes.json', fallbackData);
    }

    /**
     * Clear all cached content (useful for cache busting)
     */
    refreshContent(): void {
        this.assetCache.clear('content-asset-privacy.json');
        this.assetCache.clear('content-asset-faq.json');
        this.assetCache.clear('content-asset-top-stocks.json');
        this.assetCache.clear('content-asset-damodaran-quotes.json');
    }

    /**
     * Clear all content cache entries
     */
    clearAllCache(): void {
        this.assetCache.clearAll();
    }

    /**
     * Get cache statistics for content assets
     */
    getCacheStats() {
        return this.assetCache.getStats();
    }

    /**
     * Get centralized asset path for any asset type
     * @param category Asset category (content, logos, images, etc.)
     * @param filename Asset filename
     * @param format Optional format for optimization
     * @returns Asset path/URL
     */
    getAssetPath(category: AssetType, filename: string, format?: AssetFormat): string {
        return this.assetPathService.buildAssetPath(category, filename, format);
    }

    /**
     * Get logo asset path with WebP optimization support
     * @param filename Logo filename
     * @param useWebP Whether to use WebP format if available
     * @returns Logo asset path
     */
    getLogoPath(filename: string, useWebP?: boolean): string {
        return this.assetPathService.buildLogoPath(filename, useWebP);
    }

    /**
     * Get image asset path with WebP optimization support
     * @param filename Image filename
     * @param useWebP Whether to use WebP format if available
     * @returns Image asset path
     */
    getImagePath(filename: string, useWebP?: boolean): string {
        return this.assetPathService.buildImagePath(filename, useWebP);
    }

    /**
     * Get icon asset path
     * @param filename Icon filename
     * @returns Icon asset path
     */
    getIconPath(filename: string): string {
        return this.assetPathService.buildIconPath(filename);
    }

    /**
     * Get document asset path
     * @param filename Document filename
     * @returns Document asset path
     */
    getDocumentPath(filename: string): string {
        return this.assetPathService.buildDocumentPath(filename);
    }

    /**
     * Check if WebP optimization is enabled
     * @returns True if WebP optimization is enabled
     */
    isWebPEnabled(): boolean {
        return this.assetPathService.isWebPOptimizationEnabled();
    }

    /**
     * Get asset optimization configuration
     * @returns Asset configuration for debugging
     */
    getAssetConfig() {
        return this.assetPathService.getAssetConfigForDebug();
    }
}
