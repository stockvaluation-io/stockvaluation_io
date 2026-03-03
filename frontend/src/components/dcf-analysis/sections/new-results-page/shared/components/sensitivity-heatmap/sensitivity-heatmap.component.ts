import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';

export interface HeatMapData {
  growth_rates: number[];
  discount_rates: number[];
  valuations: number[][];
  base_value?: number;
  min_value?: number;
  max_value?: number;
}

interface FilteredHeatMapData {
  growth_rates: number[];
  discount_rates: number[];
  valuations: number[][];
  growth_indices: number[];
  discount_indices: number[];
}

@Component({
  selector: 'app-sensitivity-heatmap',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="sensitivity-heatmap-container">
      <div class="heatmap-header">
        <h4 class="heatmap-title">
          <i class="pi pi-th-large"></i>
          Sensitivity Heat Map
        </h4>
        <p class="heatmap-subtitle">How intrinsic value changes with growth rate and discount rate</p>
      </div>
      
      <div class="heatmap-content" *ngIf="displayData">
        <div class="heatmap-wrapper">
          <div class="heatmap-y-label">Discount Rate (%)</div>
          <div class="heatmap-main">
            <div class="heatmap-y-axis">
              <div *ngFor="let rate of displayData.discount_rates" class="axis-label">
                {{ rate.toFixed(1) }}
              </div>
            </div>
            
            <div class="heatmap-grid-wrapper">
              <div class="heatmap-grid">
                <div 
                  *ngFor="let row of displayData.valuations; let i = index"
                  class="heatmap-row">
                  <div 
                    *ngFor="let value of row; let j = index"
                    class="heatmap-cell"
                    [style.background-color]="getCellColor(value)"
                    [title]="'Growth: ' + displayData.growth_rates[j].toFixed(1) + '%, Discount: ' + displayData.discount_rates[i].toFixed(1) + '% → $' + value.toFixed(2)">
                    <span class="cell-value">\${{ value.toFixed(0) }}</span>
                  </div>
                </div>
              </div>
              
              <div class="heatmap-x-axis">
                <div *ngFor="let rate of displayData.growth_rates" class="axis-label">
                  {{ rate.toFixed(1) }}
                </div>
              </div>
            </div>
          </div>
          <div class="heatmap-x-label">Growth Rate (%)</div>
        </div>
        
        <div class="heatmap-legend">
          <span class="legend-label">Lower</span>
          <div class="legend-gradient"></div>
          <span class="legend-label">Higher</span>
        </div>
      </div>
      
      <div class="no-data-message" *ngIf="!displayData">
        <i class="pi pi-info-circle"></i>
        <p>Sensitivity analysis data not available</p>
      </div>
    </div>
  `,
  styles: [`
    .sensitivity-heatmap-container {
      width: 100%;
    }
    
    .heatmap-header {
      margin-bottom: var(--space-4);
    }
    
    .heatmap-title {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      margin: 0 0 var(--space-2) 0;
      font-size: var(--font-size-xl);
      font-weight: var(--font-weight-semibold);
      color: var(--color-text-primary);
      
      i {
        color: var(--color-primary);
      }
    }
    
    .heatmap-subtitle {
      margin: 0;
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
    }
    
    .heatmap-content {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }
    
    .heatmap-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      align-items: center;
    }
    
    .heatmap-y-label,
    .heatmap-x-label {
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-medium);
      color: var(--color-text-secondary);
      text-align: center;
    }
    
    .heatmap-main {
      display: flex;
      gap: var(--space-2);
      align-items: stretch;
    }
    
    .heatmap-y-axis {
      display: flex;
      flex-direction: column;
      justify-content: space-around;
      padding: 20px 0;
      min-width: 50px;
    }
    
    .heatmap-grid-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }
    
    .heatmap-grid {
      display: flex;
      flex-direction: column;
      gap: 2px;
      background: var(--color-border-primary);
      border-radius: 8px;
      overflow: hidden;
    }
    
    .heatmap-row {
      display: flex;
      gap: 2px;
    }
    
    .heatmap-cell {
      width: 80px;
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: var(--font-size-xs);
      font-weight: var(--font-weight-semibold);
      color: white;
      text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
      cursor: help;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
      
      &:hover {
        transform: scale(1.05);
        z-index: 1;
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
      }
    }
    
    .cell-value {
      font-size: var(--font-size-xs);
      white-space: nowrap;
    }
    
    .heatmap-x-axis {
      display: flex;
      justify-content: space-around;
      gap: 2px;
      
      .axis-label {
        width: 80px;
        text-align: center;
      }
    }
    
    .axis-label {
      font-size: var(--font-size-xs);
      color: var(--color-text-secondary);
      font-weight: var(--font-weight-medium);
      text-align: center;
    }
    
    .heatmap-legend {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      justify-content: center;
      margin-top: var(--space-2);
    }
    
    .legend-gradient {
      width: 200px;
      height: 20px;
      background: linear-gradient(90deg, #ef4444 0%, #f59e0b 25%, #84cc16 50%, #22c55e 75%, #10b981 100%);
      border-radius: 4px;
    }
    
    .legend-label {
      font-size: var(--font-size-xs);
      color: var(--color-text-secondary);
      font-weight: var(--font-weight-medium);
    }
    
    .no-data-message {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-6);
      color: var(--color-text-secondary);
      
      i {
        font-size: 2rem;
      }
      
      p {
        margin: 0;
        font-size: var(--font-size-sm);
      }
    }
    
    /* Tablet responsiveness */
    @media (max-width: 1024px) {
      .heatmap-cell {
        width: 55px;
        height: 45px;
        font-size: 0.65rem;
      }
      
      .cell-value {
        font-size: 0.65rem;
      }
      
      .heatmap-x-axis .axis-label {
        width: 55px;
      }
      
      .legend-gradient {
        width: 150px;
        height: 16px;
      }
    }
    
    /* Mobile responsiveness - 3x3 grid */
    @media (max-width: 768px) {
      .heatmap-cell {
        width: 45px;
        height: 40px;
        font-size: 0.6rem;
      }
      
      .cell-value {
        font-size: 0.6rem;
      }
      
      .heatmap-x-axis .axis-label {
        width: 45px;
        font-size: 0.65rem;
      }
      
      .heatmap-y-axis {
        min-width: 40px;
        padding: 15px 0;
      }
      
      .axis-label {
        font-size: 0.65rem;
      }
      
      .heatmap-title {
        font-size: var(--font-size-lg);
      }
      
      .legend-gradient {
        width: 120px;
        height: 14px;
      }
      
      .legend-label {
        font-size: 0.65rem;
      }
    }
    
    @media (max-width: 480px) {
      .heatmap-wrapper {
        overflow-x: auto;
        width: 100%;
      }
      
      .heatmap-main {
        min-width: fit-content;
      }
    }
  `]
})
export class SensitivityHeatmapComponent implements OnInit {
  @Input() heatMapData: HeatMapData | null = null;
  
  displayData: FilteredHeatMapData | null = null;
  isMobileView = false;
  
  private breakpointObserver = inject(BreakpointObserver);
  
  ngOnInit(): void {
    // Subscribe to breakpoint changes
    this.breakpointObserver.observe([Breakpoints.HandsetPortrait, Breakpoints.HandsetLandscape])
      .subscribe(result => {
        this.isMobileView = result.matches;
        this.updateDisplayData();
      });
    
    // Initial data setup
    this.updateDisplayData();
  }
  
  private updateDisplayData(): void {
    if (!this.heatMapData || !this.isValidHeatmap()) {
      this.displayData = null;
      return;
    }
    
    if (this.isMobileView) {
      // Use 3x3 grid for mobile
      this.displayData = this.getMobileGridData(this.heatMapData);
    } else {
      // Use full 5x5 grid for desktop/tablet
      this.displayData = {
        growth_rates: this.heatMapData.growth_rates,
        discount_rates: this.heatMapData.discount_rates,
        valuations: this.heatMapData.valuations,
        growth_indices: [0, 1, 2, 3, 4],
        discount_indices: [0, 1, 2, 3, 4]
      };
    }
  }
  
  private isValidHeatmap(): boolean {
    if (!this.heatMapData) return false;
    
    const { growth_rates, discount_rates, valuations } = this.heatMapData;
    
    return !!(
      growth_rates && 
      discount_rates && 
      valuations &&
      growth_rates.length > 0 &&
      discount_rates.length > 0 &&
      valuations.length > 0 &&
      valuations.every(row => row && row.length > 0)
    );
  }
  
  private getMobileGridData(heatMapData: HeatMapData): FilteredHeatMapData {
    const indices = [0, 2, 4]; // corners and center
    
    return {
      growth_rates: indices.map(i => heatMapData.growth_rates[i]),
      discount_rates: indices.map(i => heatMapData.discount_rates[i]),
      valuations: indices.map(i => indices.map(j => heatMapData.valuations[i][j])),
      growth_indices: indices,
      discount_indices: indices
    };
  }
  
  getCellColor(value: number): string {
    if (!this.heatMapData?.min_value || !this.heatMapData?.max_value) {
      return '#6b7280';
    }
    
    const min = this.heatMapData.min_value;
    const max = this.heatMapData.max_value;
    const range = max - min;
    
    if (range === 0) {
      return '#6b7280';
    }
    
    const normalized = (value - min) / range; // 0 to 1
    
    // Color scale from red (low) to green (high)
    if (normalized < 0.2) {
      return '#ef4444'; // red
    } else if (normalized < 0.4) {
      return '#f59e0b'; // orange
    } else if (normalized < 0.6) {
      return '#84cc16'; // yellow-green
    } else if (normalized < 0.8) {
      return '#22c55e'; // green
    } else {
      return '#10b981'; // emerald
    }
  }
}

