import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { ChatSidebarComponent } from './chat-sidebar.component';
import { ToolResult } from './socket-chat.service';

/**
 * DCF Page with Integrated Chat Sidebar - Example
 * 
 * This demonstrates how to integrate the chat sidebar into your existing
 * DCF valuation results page without breaking the layout.
 */
@Component({
  selector: 'app-dcf-with-chat-example',
  standalone: true,
  imports: [CommonModule, ChatSidebarComponent],
  template: `
    <div class="dcf-page-container">
      <!-- Your Existing DCF Content -->
      <div class="dcf-content">
        <header class="dcf-header">
          <h1>DCF Valuation: {{ ticker }}</h1>
          <div class="header-actions">
            <button class="btn-primary">Download Report</button>
            <button class="btn-secondary">Export Data</button>
          </div>
        </header>

        <!-- Valuation Summary -->
        <section class="valuation-summary">
          <h2>Valuation Summary</h2>
          <div class="metrics-grid">
            <div class="metric-card">
              <div class="metric-label">Intrinsic Value</div>
              <div class="metric-value">{{ valuationData().intrinsicValue | currency }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">Current Price</div>
              <div class="metric-value">{{ valuationData().currentPrice | currency }}</div>
            </div>
            <div class="metric-card">
              <div class="metric-label">Upside/Downside</div>
              <div class="metric-value" [class.positive]="valuationData().upside > 0">
                {{ valuationData().upside | percent }}
              </div>
            </div>
          </div>
        </section>

        <!-- Financial Projections -->
        <section class="financial-projections">
          <h2>Financial Projections</h2>
          <!-- Your existing projections table/chart -->
          <p>Your existing DCF tables, charts, and analysis go here...</p>
        </section>

        <!-- Assumptions -->
        <section class="assumptions">
          <h2>Key Assumptions</h2>
          <!-- Your existing assumptions display -->
          <p>WACC, growth rates, margins, etc...</p>
        </section>

        <!-- Sensitivity Analysis -->
        <section class="sensitivity-analysis">
          <h2>Sensitivity Analysis</h2>
          <!-- Your existing sensitivity tables -->
          <p>Sensitivity tables and scenarios...</p>
        </section>
      </div>

      <!-- Chat Sidebar (Non-intrusive) -->
      <app-chat-sidebar
        [userId]="currentUserId"
        [ticker]="ticker"
        [backendUrl]="chatBackendUrl"
        [autoConnect]="true"
        [initiallyOpen]="false"
        [showToggleButton]="true"
        [width]="'420px'"
        [contextData]="valuationData()"
        (toolResultReceived)="handleToolResult($event)"
        (opened)="onChatOpened()"
        (closed)="onChatClosed()"
      />
    </div>
  `,
  styles: [`
    .dcf-page-container {
      min-height: 100vh;
      background: #f9fafb;
      position: relative;
    }

    .dcf-content {
      max-width: 1400px;
      margin: 0 auto;
      padding: 2rem;
      transition: margin-right 0.3s ease;
    }

    .dcf-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
      background: white;
      padding: 1.5rem;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);

      h1 {
        margin: 0;
        font-size: 1.875rem;
        color: #1f2937;
      }
    }

    .header-actions {
      display: flex;
      gap: 0.75rem;
    }

    .btn-primary,
    .btn-secondary {
      padding: 0.625rem 1.25rem;
      border: none;
      border-radius: 8px;
      font-size: 0.9375rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s;
    }

    .btn-primary {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;

      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 4px 12px rgba(102, 126, 234, 0.3);
      }
    }

    .btn-secondary {
      background: white;
      color: #6b7280;
      border: 1px solid #e5e7eb;

      &:hover {
        background: #f9fafb;
        border-color: #d1d5db;
      }
    }

    .valuation-summary,
    .financial-projections,
    .assumptions,
    .sensitivity-analysis {
      background: white;
      padding: 1.5rem;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      margin-bottom: 1.5rem;

      h2 {
        margin: 0 0 1.25rem 0;
        font-size: 1.25rem;
        color: #1f2937;
      }
    }

    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 1rem;
    }

    .metric-card {
      padding: 1rem;
      background: #f9fafb;
      border-radius: 8px;
      border: 1px solid #e5e7eb;
    }

    .metric-label {
      font-size: 0.875rem;
      color: #6b7280;
      margin-bottom: 0.5rem;
    }

    .metric-value {
      font-size: 1.5rem;
      font-weight: 700;
      color: #1f2937;

      &.positive {
        color: #10b981;
      }
    }

    @media (max-width: 768px) {
      .dcf-content {
        padding: 1rem;
      }

      .dcf-header {
        flex-direction: column;
        gap: 1rem;
        align-items: stretch;

        h1 {
          font-size: 1.5rem;
        }
      }

      .header-actions {
        flex-direction: column;
      }

      .metrics-grid {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class DcfWithChatExampleComponent implements OnInit {
  ticker = 'AAPL';
  currentUserId = 'user-' + Date.now();
  chatBackendUrl = 'http://localhost:5000';

  // Valuation data (would come from your service)
  valuationData = signal({
    intrinsicValue: 185.50,
    currentPrice: 175.25,
    upside: 0.0585, // 5.85%
    dcfValue: 185.50,
    wacc: 0.089,
    terminalGrowthRate: 0.025,
    // ... other valuation metrics
  });

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    // Get ticker from route params
    this.route.params.subscribe(params => {
      if (params['ticker']) {
        this.ticker = params['ticker'].toUpperCase();
      }
    });

    // Load your valuation data
    this.loadValuationData();
  }

  private loadValuationData(): void {
    // Replace with your actual service call
    // this.dcfService.getValuation(this.ticker).subscribe(data => {
    //   this.valuationData.set(data);
    // });
  }

  /**
   * Handle tool results from chat
   * Update the page when AI tools calculate new valuations
   */
  handleToolResult(result: ToolResult): void {
    console.log('Tool result received:', result);

    // Handle different tool types
    switch (result.toolName) {
      case 'calculate_dcf':
        this.updateDcfValue(result.result);
        break;
      case 'update_assumptions':
        this.updateAssumptions(result.result);
        break;
      case 'sensitivity_analysis':
        this.showSensitivityResults(result.result);
        break;
      default:
        console.log('Unknown tool:', result.toolName);
    }
  }

  private updateDcfValue(result: any): void {
    if (result.intrinsicValue) {
      const current = this.valuationData();
      this.valuationData.set({
        ...current,
        intrinsicValue: result.intrinsicValue,
        upside: (result.intrinsicValue - current.currentPrice) / current.currentPrice,
      });
      
      // Show notification
      console.log('DCF value updated!', result.intrinsicValue);
    }
  }

  private updateAssumptions(result: any): void {
    console.log('Assumptions updated:', result);
    // Update your assumptions display
  }

  private showSensitivityResults(result: any): void {
    console.log('Sensitivity analysis:', result);
    // Update your sensitivity table
  }

  onChatOpened(): void {
    console.log('Chat opened');
    // Optional: track analytics
  }

  onChatClosed(): void {
    console.log('Chat closed');
    // Optional: track analytics
  }
}

