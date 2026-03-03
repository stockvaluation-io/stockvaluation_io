import { Component, OnInit, PLATFORM_ID, Inject } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Router } from '@angular/router';
import { ContentService } from '../../../core/services/data/content.service';
import { TopStock } from '../../../core/interfaces';
import { ContentLoadingComponent } from '../../../core/base';

@Component({
  selector: 'app-top-stocks-table',
  imports: [CommonModule],
  template: `
    <div class="top-stocks-table-container">
      <!-- Loading state -->
      <div *ngIf="loading" class="loading-container">
        <div class="loading-spinner"></div>
        <p class="loading-text">Loading top stocks...</p>
      </div>

      <!-- Error state -->
      <div *ngIf="hasError" class="error-container">
        <div class="error-icon">⚠️</div>
        <p class="error-message">{{ error }}</p>
        <button class="retry-btn" (click)="retry()">Try Again</button>
      </div>

      <!-- Success state with data -->
      <div *ngIf="hasData && !loading" class="table-wrapper">
        <table class="stocks-table">
          <thead>
            <tr>
              <th class="symbol-column" style="text-align: center;">Symbol</th>
              <!--<th class="price-column">Price</th>
              <th class="change-column">Change</th>
              <th class="range-column">Day Range</th>
              <th class="week52-column">52W Range</th>
              <th class="volume-column">Volume</th>
              <th class="pe-column">P/E</th>
              <th class="bid-ask-column">Bid/Ask</th>
              <th class="dividend-column">Div/Yield</th> -->
              <th class="action-column" style="text-align: center;">Action</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let stock of data; trackBy: trackBySymbol" 
                class="stock-row" 
                [class.positive]="stock.change > 0"
                [class.negative]="stock.change < 0">
              <td class="symbol-cell" style="text-align: center;">
                <div class="symbol-container">
                  <span class="symbol">{{ stock.symbol }}</span>
                  <span class="company-name">{{ formatCompanyName(stock.name) }}</span>
                </div>
              </td>
              
              <td class="action-cell" style="text-align: center;">
                <button class="analyze-btn" (click)="analyzeStock(stock.symbol)">
                  Analyze
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

  `,
  styleUrls: ['./top-stocks-table.component.scss']
})
export class TopStocksTableComponent extends ContentLoadingComponent<TopStock[]> implements OnInit {

  constructor(
    private router: Router,
    private contentService: ContentService,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    super();
  }

  ngOnInit(): void {
    this.loadTopStocks();
  }

  private loadTopStocks(): void {
    // Only load data on browser platform
    if (!isPlatformBrowser(this.platformId)) {
      this.setSuccessState([]);
      return;
    }

    this.loadContent(this.contentService.getTopStocks());
  }

  protected reload(): void {
    this.loadTopStocks();
  }

  protected override getErrorMessage(error: any): string {
    if (error?.status === 404) {
      return 'Top stocks data not found. Please try again later.';
    }
    if (error?.status === 0) {
      return 'Unable to connect to server. Please check your internet connection.';
    }
    return 'Unable to load top stocks data. Please try again.';
  }

  trackBySymbol(index: number, stock: TopStock): string {
    return stock.symbol;
  }

  formatPrice(price: number): string {
    return price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  formatChange(change: number): string {
    const prefix = change > 0 ? '+' : '';
    return prefix + change.toFixed(2);
  }

  formatChangePercent(changePercent: number): string {
    const prefix = changePercent > 0 ? '+' : '';
    return prefix + changePercent.toFixed(2) + '%';
  }

  formatMarketCap(marketCap: number): string {
    if (marketCap >= 1e12) {
      return '$' + (marketCap / 1e12).toFixed(2) + 'T';
    } else if (marketCap >= 1e9) {
      return '$' + (marketCap / 1e9).toFixed(2) + 'B';
    } else if (marketCap >= 1e6) {
      return '$' + (marketCap / 1e6).toFixed(2) + 'M';
    }
    return '$' + marketCap.toLocaleString();
  }

  formatVolume(volume: number): string {
    if (volume >= 1e9) {
      return (volume / 1e9).toFixed(1) + 'B';
    } else if (volume >= 1e6) {
      return (volume / 1e6).toFixed(1) + 'M';
    } else if (volume >= 1e3) {
      return (volume / 1e3).toFixed(1) + 'K';
    }
    return volume.toLocaleString();
  }

  formatCompanyName(name: string): string {
    if (name.length > 20) {
      return name.substring(0, 17) + '...';
    }
    return name;
  }

  formatPERatio(peRatio: number | null): string {
    if (peRatio === null || peRatio === undefined) {
      return '--';
    }
    return peRatio.toFixed(2);
  }

  formatDividend(dividend: number): string {
    if (dividend === 0) {
      return '--';
    }
    return dividend.toFixed(2);
  }

  formatDividendYield(dividendYield: number): string {
    if (dividendYield === 0) {
      return '--';
    }
    return dividendYield.toFixed(2) + '%';
  }

  getDayRangePosition(stock: TopStock): number {
    const range = stock.dayHigh - stock.dayLow;
    if (range === 0) return 50;
    return ((stock.price - stock.dayLow) / range) * 100;
  }

  getWeek52RangePosition(stock: TopStock): number {
    const range = stock.week52High - stock.week52Low;
    if (range === 0) return 50;
    return ((stock.price - stock.week52Low) / range) * 100;
  }

  analyzeStock(symbol: string): void {
    this.router.navigate(['/automated-dcf-analysis', symbol, 'valuation']);
  }

}
