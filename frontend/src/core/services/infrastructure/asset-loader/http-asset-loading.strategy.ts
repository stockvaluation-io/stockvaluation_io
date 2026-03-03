import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { Observable } from 'rxjs';
import { AssetLoadingStrategy } from './asset-loading-strategy.interface';

@Injectable({
  providedIn: 'root'
})
export class HttpAssetLoadingStrategy implements AssetLoadingStrategy {

  constructor(
    private http: HttpClient,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {}

  loadAsset<T>(url: string): Observable<T> {
    return this.http.get<T>(url);
  }

  canHandle(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  getStrategyName(): string {
    return 'HTTP Asset Loading Strategy';
  }
}