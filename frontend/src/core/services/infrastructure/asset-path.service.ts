import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { environment } from '../../../env/environment';
import { 
  AssetConfig, 
  AssetType, 
  AssetFormat, 
  AssetPathBuilder,
  DEFAULT_ASSET_CATEGORIES 
} from '../../config/asset.config';
import { LoggerService } from './logger.service';

@Injectable({
  providedIn: 'root'
})
export class AssetPathService {
  
  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private logger: LoggerService
  ) {}

  /**
   * Get the current asset configuration based on environment
   * @returns Asset configuration object
   */
  private getAssetConfig(): AssetConfig {
    return environment.assets;
  }

  /**
   * Build a complete asset path for a given category and filename
   * @param category Asset category (content, logos, images, etc.)
   * @param filename Name of the asset file
   * @param format Optional format override for optimization
   * @returns Complete asset path/URL
   */
  buildAssetPath(category: AssetType, filename: string, format?: AssetFormat): string {
    try {
      const config = this.getAssetConfig();
      
      // Use optimization if format is specified
      if (format) {
        return AssetPathBuilder.buildOptimizedPath(config, category, filename, format);
      }
      
      return AssetPathBuilder.buildPath(config, category, filename);
    } catch (error) {
      this.logger.error(`Failed to build asset path for ${category}/${filename}`, error, 'AssetPathService');
      // Return fallback path
      return `${environment.contentBaseUrl}/${filename}`;
    }
  }

  /**
   * Build content asset path (for ContentService compatibility)
   * @param filename Name of the content file
   * @returns Content asset path
   */
  buildContentPath(filename: string): string {
    return this.buildAssetPath('content', filename);
  }

  /**
   * Build logo asset path with optimization support
   * @param filename Logo filename
   * @param useWebP Whether to use WebP format if available
   * @returns Logo asset path
   */
  buildLogoPath(filename: string, useWebP?: boolean): string {
    const config = this.getAssetConfig();
    
    if (useWebP && config.optimization.enableWebP) {
      return this.buildAssetPath('logos', filename, 'webp');
    }
    
    return this.buildAssetPath('logos', filename);
  }

  /**
   * Build image asset path with optimization support
   * @param filename Image filename
   * @param useWebP Whether to use WebP format if available
   * @returns Image asset path
   */
  buildImagePath(filename: string, useWebP?: boolean): string {
    const config = this.getAssetConfig();
    
    if (useWebP && config.optimization.enableWebP) {
      return this.buildAssetPath('images', filename, 'webp');
    }
    
    return this.buildAssetPath('images', filename);
  }

  /**
   * Build icon asset path
   * @param filename Icon filename
   * @returns Icon asset path
   */
  buildIconPath(filename: string): string {
    return this.buildAssetPath('icons', filename);
  }

  /**
   * Build font asset path
   * @param filename Font filename
   * @returns Font asset path
   */
  buildFontPath(filename: string): string {
    return this.buildAssetPath('fonts', filename);
  }

  /**
   * Build document asset path
   * @param filename Document filename
   * @returns Document asset path
   */
  buildDocumentPath(filename: string): string {
    return this.buildAssetPath('documents', filename);
  }

  /**
   * Build data asset path
   * @param filename Data filename
   * @returns Data asset path
   */
  buildDataPath(filename: string): string {
    return this.buildAssetPath('data', filename);
  }

  /**
   * Get asset category configuration
   * @param categoryId Category identifier
   * @returns Category configuration or null if not found
   */
  getCategoryConfig(categoryId: string) {
    return DEFAULT_ASSET_CATEGORIES.find(cat => cat.id === categoryId) || null;
  }

  /**
   * Get all available asset categories
   * @returns Array of asset category configurations
   */
  getAvailableCategories() {
    return DEFAULT_ASSET_CATEGORIES;
  }

  /**
   * Get cache TTL for a specific asset category
   * @param category Asset category
   * @returns Cache TTL in milliseconds
   */
  getCacheTtl(category: AssetType): number {
    const categoryConfig = this.getCategoryConfig(category);
    return categoryConfig?.cacheTtl || 300000; // Default 5 minutes
  }

  /**
   * Check if WebP optimization is enabled
   * @returns True if WebP optimization is enabled
   */
  isWebPOptimizationEnabled(): boolean {
    return this.getAssetConfig().optimization.enableWebP;
  }

  /**
   * Check if lazy loading is enabled
   * @returns True if lazy loading is enabled
   */
  isLazyLoadingEnabled(): boolean {
    return this.getAssetConfig().optimization.enableLazyLoading;
  }

  /**
   * Get fallback configuration
   * @returns Fallback configuration object
   */
  getFallbackConfig() {
    return this.getAssetConfig().fallback;
  }

  /**
   * Get asset configuration for debugging
   * @returns Complete asset configuration
   */
  getAssetConfigForDebug() {
    return this.getAssetConfig();
  }

  /**
   * Legacy method for backward compatibility with existing AssetPathResolver
   * @param filename Asset filename
   * @returns Asset path
   */
  resolveAssetPath(filename: string): string {
    // For content files, maintain backward compatibility
    return this.buildContentPath(filename);
  }
}