import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, of, throwError } from 'rxjs';
import { AssetLoadingStrategy } from './asset-loading-strategy.interface';

export interface FileSystemError {
  code: string;
  message: string;
  filepath: string;
  originalError?: any;
}

@Injectable({
  providedIn: 'root'
})
export class FilesystemAssetLoadingStrategy implements AssetLoadingStrategy {

  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  loadAsset<T>(filename: string): Observable<T> {
    if (!this.canHandle()) {
      return throwError(() => new Error('Filesystem loading not available in browser environment'));
    }

    try {
      const fs = require('fs');
      const path = require('path');
      
      // Construct the full file path - filename already contains the full asset path
      const fullPath = path.join(process.cwd(), 'dist', 'dcf-frontend', 'browser', filename);
      
      // Check if file exists first
      if (!fs.existsSync(fullPath)) {
        const error: FileSystemError = {
          code: 'FILE_NOT_FOUND',
          message: `Asset file not found: ${fullPath}`,
          filepath: fullPath
        };
        return throwError(() => error);
      }

      // Read and parse the file
      const fileContent = fs.readFileSync(fullPath, 'utf8');
      const jsonData = JSON.parse(fileContent);
      
      return of(jsonData as T);
    } catch (error: any) {
      const fsError: FileSystemError = {
        code: error.code || 'FILESYSTEM_ERROR',
        message: `Error reading asset file: ${error.message}`,
        filepath: filename,
        originalError: error
      };
      
      return throwError(() => fsError);
    }
  }

  canHandle(): boolean {
    return !isPlatformBrowser(this.platformId);
  }

  getStrategyName(): string {
    return 'Filesystem Asset Loading Strategy';
  }
}