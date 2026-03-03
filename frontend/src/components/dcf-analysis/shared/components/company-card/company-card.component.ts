import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CompanyData } from '../../../models';

@Component({
    selector: 'app-company-card',
    imports: [CommonModule],
    template: `
    <div class="company-card" [class.selected]="isSelected" (click)="onSelect()">
      <div class="company-row">
        <!-- Symbol & Logo Column -->
        <div class="symbol-section">
          <div class="company-logo">
            <img 
              [src]="getCompanyLogoUrl()" 
              [alt]="company.name + ' logo'"
              (error)="onLogoError($event)"
              *ngIf="!logoError; else logoPlaceholder"
            />
            <ng-template #logoPlaceholder>
              <div class="company-logo-placeholder">
                {{ getCompanyInitials() }}
              </div>
            </ng-template>
          </div>
          <div class="symbol-info">
            <span class="company-symbol" [title]="company.symbol">{{ company.symbol }}</span>
            <span class="company-price" *ngIf="company.price">{{ formatPrice(company.price) }}</span>
          </div>
        </div>

        <!-- Company Name Column -->
        <div class="name-section">
          <span class="company-name" [title]="company.name">{{ company.name }}</span>
          <span class="company-industry" *ngIf="company.industry" [title]="company.industry">{{ company.industry }}</span>
        </div>

        <!-- Exchange Data Column -->
        <div class="data-section" *ngIf="company.exchange">
          <span 
            class="exchange-short" 
            [title]="company.exchange"
            *ngIf="getExchangeShortName()"
          >
            {{ getExchangeShortName() }}
          </span>
          <span class="exchange-type" *ngIf="company.type">{{ company.type.toUpperCase() }}</span>
        </div>

        <!-- Action Column -->
        <div class="action-section" *ngIf="showActions">
          <button 
            class="select-button"
            type="button"
            (click)="onSelectClick($event)"
            [class.selected]="isSelected"
            [attr.aria-label]="'Select ' + company.name"
          >
            <i class="pi" [class.pi-check]="isSelected" [class.pi-plus]="!isSelected"></i>
          </button>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./company-card.component.scss']
})
export class CompanyCardComponent {
  @Input() company!: CompanyData;
  @Input() isSelected = false;
  @Input() showDetails = true;
  @Input() showActions = true;
  
  @Output() companySelected = new EventEmitter<CompanyData>();
  
  logoError = false;

  onSelect(): void {
    this.companySelected.emit(this.company);
  }

  onSelectClick(event: Event): void {
    event.stopPropagation();
    this.onSelect();
  }

  getCompanyLogoUrl(): string {
    // Extract base symbol (remove exchange suffix like .ME, .L, etc.)
    const baseSymbol = this.company.symbol.toUpperCase();
    return `https://financialmodelingprep.com/image-stock/${baseSymbol}.png`;
  }

  onLogoError(event: any): void {
    this.logoError = true;
  }

  getCompanyInitials(): string {
    return this.company.name
      .split(' ')
      .map(word => word[0])
      .join('')
      .toUpperCase()
      .substring(0, 3);
  }

  getExchangeShortName(): string {
    // Try to extract from symbol suffix or use a mapping
    if (this.company.symbol.includes('.')) {
      return this.company.symbol.split('.')[1];
    }
    
    // Return exchangeShortName if available, otherwise try to abbreviate exchange name
    if ((this.company as any).exchangeShortName) {
      return (this.company as any).exchangeShortName;
    }
    
    if (this.company.exchange) {
      // Simple abbreviation logic for common exchanges
      const exchangeMap: {[key: string]: string} = {
        'NASDAQ Global Market': 'NASDAQ',
        'New York Stock Exchange': 'NYSE',
        'London Stock Exchange': 'LSE',
        'Tokyo Stock Exchange': 'TSE',
        'Moscow Stock Exchange': 'MCX',
        'Hong Kong Stock Exchange': 'HKEX'
      };
      
      return exchangeMap[this.company.exchange] || 
             this.company.exchange.split(' ').map(word => word[0]).join('').toUpperCase();
    }
    
    return '';
  }

  formatPrice(price: number): string {
    if (price >= 1000) {
      return new Intl.NumberFormat('en-US', {
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
      }).format(price);
    } else {
      return new Intl.NumberFormat('en-US', {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }).format(price);
    }
  }

  formatMarketCap(marketCap: number): string {
    if (marketCap >= 1e12) {
      return `$${(marketCap / 1e12).toFixed(2)}T`;
    } else if (marketCap >= 1e9) {
      return `$${(marketCap / 1e9).toFixed(2)}B`;
    } else if (marketCap >= 1e6) {
      return `$${(marketCap / 1e6).toFixed(2)}M`;
    }
    return `$${marketCap.toLocaleString()}`;
  }
}