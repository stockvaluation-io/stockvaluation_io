import { Component, Input, Output, EventEmitter, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotebookService } from './notebook.service';
import { Cell, Thesis, Scenario, DCFSummary } from './cell.models';

/**
 * Thesis View Component
 * Read-only view of a saved thesis - displays cells without edit capabilities.
 * User can fork thesis to a new session for continued work.
 */
@Component({
    selector: 'app-thesis-view',
    standalone: true,
    imports: [CommonModule],
    template: `
    <div class="thesis-view">
      <!-- Header -->
      <div class="thesis-header">
        <div class="header-info">
          <div class="title-row">
            <i class="pi pi-file-check"></i>
            <h1 class="thesis-title">{{ thesis.title }}</h1>
            <span class="readonly-badge">Read-only</span>
          </div>
          
          <div class="meta-row">
            <span class="company">{{ thesis.company_name }}</span>
            <span class="ticker">({{ thesis.ticker }})</span>
            <span class="separator">•</span>
            <span class="date">
              <i class="pi pi-calendar"></i>
              {{ formatDate(thesis.created_at) }}
            </span>
            <span class="separator">•</span>
            <span class="cells-count">
              <i class="pi pi-file"></i>
              {{ thesis.cells_snapshot.length }} cells
            </span>
            @if (thesis.scenarios_snapshot.length > 0) {
              <span class="separator">•</span>
              <span class="scenarios-count">
                <i class="pi pi-sitemap"></i>
                {{ thesis.scenarios_snapshot.length }} scenarios
              </span>
            }
          </div>
        </div>
        
        <button class="fork-btn" (click)="onForkToNewSession()">
          <i class="pi pi-copy"></i>
          <span>Fork to New Session</span>
        </button>
      </div>
      
      <!-- Summary -->
      @if (thesis.summary) {
        <div class="thesis-summary">
          <p>{{ thesis.summary }}</p>
        </div>
      }
      
      <!-- DCF Summary Card -->
      @if (dcfSummary) {
        <div class="dcf-card">
          <div class="dcf-header">
            <i class="pi pi-chart-line"></i>
            <h3>Valuation Summary</h3>
            <span class="dcf-label">DCF Analysis Results</span>
          </div>
          
          <div class="dcf-metrics">
            <div class="metric fair-value">
              <div class="metric-label">
                <i class="pi pi-bullseye"></i>
                Fair Value
              </div>
              <div class="metric-value primary">{{ formatCurrency(dcfSummary.fair_value) }}</div>
            </div>
            
            <div class="metric market-price">
              <div class="metric-label">
                <i class="pi pi-dollar"></i>
                Market Price
              </div>
              <div class="metric-value">{{ formatCurrency(dcfSummary.current_price) }}</div>
            </div>
            
            <div class="metric upside">
              <div class="metric-label">
                <i class="pi pi-percentage"></i>
                Upside
              </div>
              <div class="metric-value" [class.positive]="dcfSummary.upside_pct >= 0" [class.negative]="dcfSummary.upside_pct < 0">
                @if (dcfSummary.upside_pct >= 0) {
                  <i class="pi pi-arrow-up"></i>
                } @else {
                  <i class="pi pi-arrow-down"></i>
                }
                {{ formatPercent(dcfSummary.upside_pct) }}
              </div>
            </div>
          </div>
        </div>
      }
      
      <!-- Cells List (Read-only) -->
      <div class="cells-container">
        @for (cell of thesis.cells_snapshot; track cell.id) {
          <div class="readonly-cell">
            <div class="cell-header">
              <span class="sequence">[{{ cell.sequence_number }}]</span>
              <span class="cell-type">{{ getCellTypeLabel(cell) }}</span>
              @if (cell.execution_time_ms) {
                <span class="execution-time">{{ cell.execution_time_ms }}ms</span>
              }
            </div>
            
            <!-- User Input -->
            @if (cell.user_input) {
              <div class="cell-section user-input">
                <div class="section-avatar user">
                  <i class="pi pi-user"></i>
                </div>
                <div class="section-content">
                  <p>{{ cell.user_input }}</p>
                </div>
              </div>
            }
            
            <!-- AI Output -->
            @if (getAIMessage(cell)) {
              <div class="cell-section ai-output">
                <div class="section-avatar ai">
                  <i class="pi pi-bolt"></i>
                </div>
                <div class="section-content markdown" [innerHTML]="formatMarkdown(getAIMessage(cell))">
                </div>
              </div>
            }
            
            <!-- User Notes -->
            @if (cell.user_notes) {
              <div class="cell-notes">
                <i class="pi pi-bookmark"></i>
                <span>{{ cell.user_notes }}</span>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
    styles: [`
    .thesis-view {
      height: 100%;
      background: #0d1117;
      color: #e5e7eb;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    
    /* Header */
    .thesis-header {
      padding: 1.5rem;
      background: #161b22;
      border-bottom: 1px solid #30363d;
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      flex-shrink: 0;
    }
    
    .header-info {
      flex: 1;
    }
    
    .title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      margin-bottom: 0.5rem;
    }
    
    .title-row > i {
      font-size: 1.25rem;
      color: #10b981;
    }
    
    .thesis-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 700;
      color: #f3f4f6;
    }
    
    .readonly-badge {
      padding: 0.25rem 0.5rem;
      background: #21262d;
      border: 1px solid #30363d;
      border-radius: 0.25rem;
      font-size: 0.75rem;
      color: #9ca3af;
    }
    
    .meta-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      color: #9ca3af;
    }
    
    .meta-row .ticker {
      color: #6b7280;
    }
    
    .separator {
      color: #4b5563;
    }
    
    .meta-row i {
      font-size: 0.75rem;
    }
    
    .fork-btn {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 1rem;
      background: rgba(16, 185, 129, 0.2);
      border: 1px solid rgba(16, 185, 129, 0.3);
      border-radius: 0.375rem;
      color: #10b981;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }
    
    .fork-btn:hover {
      background: rgba(16, 185, 129, 0.3);
    }
    
    /* Summary */
    .thesis-summary {
      margin: 1rem 1.5rem;
      padding: 1rem;
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 0.5rem;
    }
    
    .thesis-summary p {
      margin: 0;
      font-size: 0.875rem;
      line-height: 1.6;
      color: #d1d5db;
    }
    
    /* DCF Card */
    .dcf-card {
      margin: 0 1.5rem 1rem;
      padding: 1.5rem;
      background: #161b22;
      border: 1px solid #30363d;
      border-radius: 0.5rem;
    }
    
    .dcf-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 1.5rem;
    }
    
    .dcf-header i {
      color: #10b981;
    }
    
    .dcf-header h3 {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
      color: #f3f4f6;
    }
    
    .dcf-label {
      margin-left: auto;
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    .dcf-metrics {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 1rem;
    }
    
    .metric {
      text-align: center;
      padding: 1rem;
      background: #0d1117;
      border-radius: 0.5rem;
    }
    
    .metric-label {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.375rem;
      font-size: 0.75rem;
      color: #9ca3af;
      text-transform: uppercase;
      margin-bottom: 0.5rem;
    }
    
    .metric-value {
      font-size: 1.5rem;
      font-weight: 700;
      color: #f3f4f6;
    }
    
    .metric-value.primary {
      color: #10b981;
    }
    
    .metric-value.positive {
      color: #10b981;
    }
    
    .metric-value.negative {
      color: #ef4444;
    }
    
    /* Cells Container */
    .cells-container {
      flex: 1;
      overflow-y: auto;
      padding: 1rem 1.5rem 2rem;
    }
    
    .readonly-cell {
      margin-bottom: 1rem;
      border-left: 4px solid #6b7280;
      border-radius: 0.5rem;
      background: #161b22;
      overflow: hidden;
    }
    
    .cell-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 1rem;
      background: #0d1117;
      border-bottom: 1px solid #21262d;
    }
    
    .sequence {
      font-family: monospace;
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    .cell-type {
      font-size: 0.75rem;
      color: #9ca3af;
    }
    
    .execution-time {
      margin-left: auto;
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    .cell-section {
      display: flex;
      gap: 0.75rem;
      padding: 1rem;
    }
    
    .cell-section.user-input {
      border-bottom: 1px solid #21262d;
    }
    
    .section-avatar {
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    
    .section-avatar.user {
      background: linear-gradient(135deg, #3b82f6, #1d4ed8);
    }
    
    .section-avatar.ai {
      background: linear-gradient(135deg, #a855f7, #7c3aed);
    }
    
    .section-avatar i {
      color: white;
      font-size: 0.875rem;
    }
    
    .section-content {
      flex: 1;
    }
    
    .section-content p {
      margin: 0;
      color: #e5e7eb;
      line-height: 1.5;
    }
    
    .section-content.markdown {
      font-size: 0.875rem;
      color: #d1d5db;
      line-height: 1.6;
    }
    
    .cell-notes {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      background: rgba(16, 185, 129, 0.1);
      border-top: 1px solid rgba(16, 185, 129, 0.2);
      font-size: 0.875rem;
      color: #10b981;
    }
    
    .cell-notes i {
      flex-shrink: 0;
      margin-top: 0.125rem;
    }
  `]
})
export class ThesisViewComponent {
    @Input({ required: true }) thesis!: Thesis;

    @Output() forkToNewSession = new EventEmitter<string>();

    get dcfSummary(): DCFSummary | null {
        const snapshot = this.thesis.dcf_snapshot;
        if (!snapshot || Object.keys(snapshot).length === 0) return null;

        return {
            fair_value: snapshot['fair_value'] || snapshot['fairValue'] || 0,
            current_price: snapshot['current_price'] || snapshot['currentPrice'] || 0,
            upside_pct: snapshot['upside_pct'] || snapshot['upsidePct'] || 0,
        };
    }

    onForkToNewSession(): void {
        this.forkToNewSession.emit(this.thesis.id);
    }

    formatDate(dateString: string): string {
        if (!dateString) return '';
        try {
            return new Date(dateString).toLocaleDateString('en-US', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
            });
        } catch {
            return dateString;
        }
    }

    formatCurrency(value: number): string {
        if (value === null || value === undefined) return 'N/A';
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
        }).format(value);
    }

    formatPercent(value: number): string {
        if (value === null || value === undefined) return 'N/A';
        return `${value >= 0 ? '+' : ''}${value.toFixed(1)}%`;
    }

    getCellTypeLabel(cell: Cell): string {
        switch (cell.cell_type) {
            case 'system': return 'System';
            case 'reasoning': return 'Analysis';
            case 'calibration': return 'Calibration';
            case 'visualization': return 'Chart';
            default: return 'Cell';
        }
    }

    getAIMessage(cell: Cell): string {
        const aiOutput = cell.ai_output || (cell.author_type === 'ai' ? cell.content : null);
        return aiOutput?.message || '';
    }

    formatMarkdown(text: string): string {
        if (!text) return '';
        return text
            .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
            .replace(/\\n/g, '<br>')
            .replace(/\n/g, '<br>')
            .replace(/^- (.*)$/gm, '<li>$1</li>')
            .replace(/(<li>.*<\/li>)/gs, '<ul>$1</ul>');
    }
}
