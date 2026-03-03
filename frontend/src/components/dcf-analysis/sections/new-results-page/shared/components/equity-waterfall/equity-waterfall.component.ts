import { Component, Input, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { createCurrencyContext, formatShares, formatCompactCurrency } from '../../../../../utils/formatting.utils';

export interface WaterfallItem {
  name: string;
  key: string;
  value: number;
  isAddition: boolean;
  isTotal?: boolean;
  isTarget?: boolean;
  isReference?: boolean;
}

@Component({
    selector: 'app-equity-waterfall',
    imports: [CommonModule],
    template: `
    <div class="equity-waterfall-container">
      <div class="table-container">
        <table class="waterfall-table">
          <thead>
            <tr>
              <th>Component</th>
              <th>Value</th>
              <th>Type</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let item of waterfallItems; let i = index" 
                [class]="getItemClass(item)">
              <td class="component-name">
                <i class="pi" [class]="getItemIcon(item)"></i>
                {{ item.name }}
              </td>
              <td class="component-value" [class]="getValueClass(item)">
                <span [innerHTML]="formatCurrencyWithStyle(formatValue(item.value, item.key))"></span>
              </td>
              <td class="component-type">
                <span class="type-badge" [class]="getTypeBadgeClass(item)">
                  {{ getTypeLabel(item) }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  `,
    styleUrls: ['./equity-waterfall.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class EquityWaterfallComponent implements OnInit {
  @Input() pvTerminalValue!: number;
  @Input() pvProjectedCashFlows!: number;
  @Input() valueOfOperatingAssets!: number;
  @Input() debt!: number;
  @Input() minorityInterests!: number;
  @Input() cash!: number;
  @Input() valueOfEquity!: number;
  @Input() numberOfShares!: number;
  @Input() estimatedValuePerShare!: number;
  @Input() currentPrice!: number;
  @Input() currency: string = 'USD';
  @Input() stockCurrency?: string;
  
  currencyCtx: ReturnType<typeof createCurrencyContext> | null = null;

  ngOnInit(): void {
    this.currencyCtx = createCurrencyContext(this.currency, this.stockCurrency);
  }

  get waterfallItems(): WaterfallItem[] {
    return [
      {
        name: 'Present Value (Terminal Value)',
        key: 'pvTerminalValue',
        value: this.pvTerminalValue || 0,
        isAddition: true
      },
      {
        name: 'Present Value (Cash Flow Over Next 10 Years)',
        key: 'pvProjectedCashFlows',
        value: this.pvProjectedCashFlows || 0,
        isAddition: true
      },
      {
        name: 'Value of Operating Assets',
        key: 'valueOfOperatingAssets',
        value: this.valueOfOperatingAssets || 0,
        isAddition: true,
        isTotal: true
      },
      {
        name: 'Debt',
        key: 'debt',
        value: this.debt || 0,
        isAddition: false
      },
      {
        name: 'Minority Interests',
        key: 'minorityInterests',
        value: this.minorityInterests || 0,
        isAddition: false
      },
      {
        name: 'Cash',
        key: 'cash',
        value: this.cash || 0,
        isAddition: true
      },
      {
        name: 'Value of Equity',
        key: 'valueOfEquity',
        value: this.valueOfEquity || 0,
        isAddition: true,
        isTotal: true
      },
      {
        name: 'Number of Shares',
        key: 'numberOfShares',
        value: this.numberOfShares || 0,
        isAddition: true
      },
      {
        name: 'Estimated Value per Share (Fair Value)',
        key: 'estimatedValuePerShare',
        value: this.estimatedValuePerShare || 0,
        isAddition: true,
        isTarget: true
      },
      {
        name: 'Current Market Price',
        key: 'currentPrice',
        value: this.currentPrice || 0,
        isAddition: false,
        isReference: true
      }
    ];
  }

  getItemClass(item: WaterfallItem): string {
    const classes = [];
    
    if (item.isTotal) classes.push('total-item');
    if (item.isTarget) classes.push('target-item');
    if (item.isReference) classes.push('reference-item');
    else if (item.isAddition) classes.push('addition-item');
    else classes.push('subtraction-item');
    
    return classes.join(' ');
  }

  getValueClass(item: WaterfallItem): string {
    if (item.isTarget) return 'target-value';
    if (item.isTotal) return 'total-value';
    if (item.isReference) return 'reference-value';
    if (item.isAddition) return 'positive-value';
    return 'negative-value';
  }

  getBarClass(item: WaterfallItem): string {
    if (item.isAddition) return 'positive-bar';
    return 'negative-bar';
  }

  getBarWidth(value: number): number {
    const maxValue = Math.max(...this.waterfallItems.map(item => Math.abs(item.value)));
    return maxValue > 0 ? (Math.abs(value) / maxValue) * 100 : 0;
  }

  getConnectorIcon(item: WaterfallItem): string {
    return item.isAddition ? 'pi-plus' : 'pi-minus';
  }

  getItemIcon(item: WaterfallItem): string {
    if (item.isTarget) return 'pi-star';
    if (item.isTotal) return 'pi-circle';
    if (item.isReference) return 'pi-info-circle';
    if (item.isAddition) return 'pi-plus';
    return 'pi-minus';
  }

  getTypeLabel(item: WaterfallItem): string {
    if (item.isTarget) return 'Total';
    if (item.isTotal) return 'Sub-total';
    if (item.isReference) return 'Reference';
    if (item.key === 'numberOfShares') return 'Divisor';
    if (item.isAddition) return 'Add';
    return 'Subtract';
  }

  getTypeBadgeClass(item: WaterfallItem): string {
    if (item.isTarget) return 'target-badge';
    if (item.isTotal) return 'total-badge';
    if (item.isReference) return 'addition-badge'; // Use existing styled class
    if (item.key === 'numberOfShares') return 'total-badge'; // Use existing styled class  
    if (item.isAddition) return 'addition-badge';
    return 'subtraction-badge';
  }


  formatValue(value: number, key: string): string {
    if (key === 'numberOfShares') {
      return formatShares(value);
    }
    return this.currencyCtx?.formatCompact(value) || formatCompactCurrency(value, this.currency);
  }

  // Currency styling method
  formatCurrencyWithStyle(value: string): string {
    if (value === '—' || !value) return value;
    
    // Split the value to separate number and currency code
    const parts = value.split(' ');
    if (parts.length >= 2) {
      const numberPart = parts.slice(0, -1).join(' '); // Everything except last part
      const currencyPart = parts[parts.length - 1]; // Last part (currency code)
      
      // Return with styled currency code
      return `${numberPart} <span class="currency-code">${currencyPart}</span>`;
    }
    
    return value;
  }
}