/**
 * Asset Configuration System
 * Centralized asset path management for different environments
 */

export interface AssetConfig {
  baseUrl: string;
  cdnUrl?: string;
  paths: AssetPaths;
  optimization: AssetOptimization;
  fallback: AssetFallback;
}

export interface AssetPaths {
  content: string;
  logos: string;
  images: string;
  icons: string;
  fonts: string;
  documents: string;
  data: string;
}

export interface AssetOptimization {
  enableWebP: boolean;
  enableLazyLoading: boolean;
  compressionLevel: 'low' | 'medium' | 'high';
  maxCacheAge: number;
}

export interface AssetFallback {
  enableFallback: boolean;
  fallbackCdn?: string;
  retryAttempts: number;
  timeoutMs: number;
}

export interface AssetCategory {
  id: string;
  name: string;
  basePath: string;
  supportedFormats: string[];
  defaultFormat: string;
  cacheTtl: number;
}

export interface AssetEnvironment {
  development: AssetConfig;
  production: AssetConfig;
}

// Default asset categories
export const DEFAULT_ASSET_CATEGORIES: AssetCategory[] = [
  {
    id: 'content',
    name: 'Content Files',
    basePath: '/assets/content',
    supportedFormats: ['json', 'md', 'txt'],
    defaultFormat: 'json',
    cacheTtl: 300000 // 5 minutes
  },
  {
    id: 'logos',
    name: 'Logo Assets',
    basePath: '/assets/logo',
    supportedFormats: ['png', 'webp', 'svg'],
    defaultFormat: 'png',
    cacheTtl: 86400000 // 24 hours
  },
  {
    id: 'images',
    name: 'Image Assets',
    basePath: '/assets/images',
    supportedFormats: ['png', 'jpg', 'jpeg', 'webp', 'svg'],
    defaultFormat: 'png',
    cacheTtl: 86400000 // 24 hours
  },
  {
    id: 'icons',
    name: 'Icon Assets',
    basePath: '/assets/icons',
    supportedFormats: ['svg', 'png', 'webp'],
    defaultFormat: 'svg',
    cacheTtl: 86400000 // 24 hours
  },
  {
    id: 'fonts',
    name: 'Font Assets',
    basePath: '/assets/fonts',
    supportedFormats: ['woff2', 'woff', 'ttf', 'eot'],
    defaultFormat: 'woff2',
    cacheTtl: 2592000000 // 30 days
  },
  {
    id: 'documents',
    name: 'Document Assets',
    basePath: '/assets/documents',
    supportedFormats: ['pdf', 'doc', 'docx', 'txt'],
    defaultFormat: 'pdf',
    cacheTtl: 86400000 // 24 hours
  },
  {
    id: 'data',
    name: 'Data Assets',
    basePath: '/assets/data',
    supportedFormats: ['json', 'csv', 'xml'],
    defaultFormat: 'json',
    cacheTtl: 3600000 // 1 hour
  }
];

// Asset path builder utility
export class AssetPathBuilder {
  static buildPath(config: AssetConfig, category: string, filename: string): string {
    const categoryPath = config.paths[category as keyof AssetPaths];
    if (!categoryPath) {
      throw new Error(`Unknown asset category: ${category}`);
    }
    
    const baseUrl = config.cdnUrl || config.baseUrl;
    return `${baseUrl}${categoryPath}/${filename}`;
  }
  
  static buildOptimizedPath(
    config: AssetConfig, 
    category: string, 
    filename: string, 
    format?: string
  ): string {
    const basePath = this.buildPath(config, category, filename);
    
    // Apply optimization if enabled
    if (config.optimization.enableWebP && format === 'webp') {
      const webpPath = basePath.replace(/\.(png|jpg|jpeg)$/, '.webp');
      return webpPath;
    }
    
    return basePath;
  }
}

// Asset type definitions for better type safety
export type AssetType = 'content' | 'logos' | 'images' | 'icons' | 'fonts' | 'documents' | 'data';
export type AssetFormat = 'png' | 'webp' | 'svg' | 'jpg' | 'jpeg' | 'json' | 'pdf' | 'woff2' | 'woff' | 'ttf' | 'eot' | 'csv' | 'xml' | 'md' | 'txt';
export type CompressionLevel = 'low' | 'medium' | 'high';