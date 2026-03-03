import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { environment } from '../../../env/environment';
import { AssetPathService } from './asset-path.service';

export interface AssetPathConfig {
  contentBaseUrl: string;
  serverAssetPath: string;
}

@Injectable({
  providedIn: 'root'
})
export class AssetPathResolver {
  
  constructor(
    @Inject(PLATFORM_ID) private platformId: Object,
    private assetPathService: AssetPathService
  ) {}

  /**
   * Resolve the full path to an asset file based on platform and environment
   * @param filename Name of the asset file
   * @returns Full path/URL to the asset
   */
  resolveAssetPath(filename: string): string {
    // Use the centralized asset path service
    return this.assetPathService.resolveAssetPath(filename);
  }

  /**
   * Get configuration for asset loading
   * @returns Asset path configuration object
   */
  getAssetConfig(): AssetPathConfig {
    return {
      contentBaseUrl: environment.contentBaseUrl,
      serverAssetPath: environment.server?.assetBasePath || '' // Simplified, no file system operations here
    };
  }
}