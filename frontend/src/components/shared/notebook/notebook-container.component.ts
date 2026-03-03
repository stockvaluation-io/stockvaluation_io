import { Component, Input, Output, EventEmitter, signal, OnInit, OnDestroy, computed, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { NotebookService } from './notebook.service';
import { ThemeService } from './theme.service';
import { CellRendererComponent } from './cells/cell-renderer.component';
import { ThesisSidebarComponent } from './thesis-sidebar.component';
import { Cell, AnalysisSession, Scenario, NotebookTab, ThesisPreview, DCFOverrides, DCFRecalcResult } from './cell.models';
import { BrandLogoComponent } from '../brand-logo/brand-logo.component';
import { ThemeToggleComponent } from '../theme-toggle/theme-toggle.component';

/**
 * Notebook Container Component
 * Full-screen Jupyter-style notebook layout for investment analysis.
 */
@Component({
  selector: 'app-notebook-container',
  standalone: true,
  imports: [CommonModule, FormsModule, CellRendererComponent, ThesisSidebarComponent, BrandLogoComponent, ThemeToggleComponent],
  template: `
    <div class="notebook-container">
      <!-- Thesis Sidebar (Left Panel) -->
      <app-thesis-sidebar
        [isOpen]="sidebarOpen()"
        (close)="sidebarOpen.set(false)"
        (thesisSelected)="onThesisSelected($event)"
      />
      
      <!-- Main Content Area -->
      <div class="main-area">
        <!-- Tab Bar -->
        @if (tabs().length > 0) {
          <div class="tab-bar">
            @for (tab of tabs(); track tab.id) {
              <button 
                class="tab"
                [class.active]="tab.id === activeTabId()"
                [class.dirty]="tab.isDirty"
                (click)="switchTab(tab.id)"
              >
                <span class="tab-title">{{ tab.title }}</span>
                @if (tab.isDirty) {
                  <span class="dirty-indicator">*</span>
                }
                <button class="tab-close" (click)="closeTab($event, tab.id)">
                  <i class="pi pi-times"></i>
                </button>
              </button>
            }
          </div>
        }
        
        <!-- Notebook Content -->
        <div class="notebook-content">
          <!-- Header - Matching DCF Analysis Style -->
          <div class="notebook-header">
            <div class="header-left">
              <button class="menu-btn" (click)="sidebarOpen.set(!sidebarOpen())">
                <i class="pi pi-bars"></i>
              </button>
              
              <!-- Brand Logo -->
              <app-brand-logo 
                size="md" 
                variant="default"
                linkTo="/automated-dcf-analysis">
              </app-brand-logo>
              
              @if (session()) {
                <div class="session-info">
                  <span class="session-divider">|</span>
                  <h2 class="session-title">{{ session()!.title }}</h2>
                  <span class="ticker-badge">{{ session()!.ticker }}</span>
                </div>
              }
            </div>
            
            <div class="header-right">
              <!-- Scenario Selector -->
              @if (scenarios().length > 0) {
                <div class="scenario-selector">
                  <label>Scenario:</label>
                  <select [(ngModel)]="selectedScenarioId">
                    <option value="">Base Case</option>
                    @for (scenario of scenarios(); track scenario.id) {
                      <option [value]="scenario.id">{{ scenario.name }}</option>
                    }
                  </select>
                </div>
              }
              
              <!-- Theme Toggle - Using shared component -->
              <app-theme-toggle></app-theme-toggle>
              
              <!-- Save Thesis Button -->
              <button 
                class="save-btn"
                [disabled]="!session() || isLoading()"
                (click)="saveThesis()"
              >
                <i class="pi pi-save"></i>
                <span class="hide-mobile">Save Thesis</span>
              </button>
              
              <!-- Close Button -->
              <button class="close-notebook-btn" (click)="closeNotebook()">
                <i class="pi pi-times"></i>
              </button>
            </div>
          </div>
          
          <!-- Cells List -->
          <div class="cells-container" #cellsContainer>
            @if (isLoading() && cells().length === 0) {
              <div class="loading-state">
                <div class="spinner"></div>
                <p>Loading analysis...</p>
              </div>
            } @else if (cells().length === 0) {
              <div class="empty-state">
                <i class="pi pi-comments"></i>
                <p>No cells yet. Start by asking a question below.</p>
              </div>
            } @else {
              @for (cell of cells(); track cell.id; let i = $index) {
                <app-cell-renderer
                  [cell]="cell"
                  [sessionId]="session()?.id"
                  [isFirst]="i === 0"
                  [isLast]="i === cells().length - 1"
                  [isStreaming]="isStreaming() && streamingCellId() === cell.id"
                  (onDelete)="onCellDelete($event)"
                  (onUpdateNotes)="onCellUpdateNotes($event)"
                />
              }
            }
            
            <!-- Streaming Indicator -->
            @if (isStreaming()) {
              <div class="streaming-indicator">
                <div class="typing-dots">
                  <span></span><span></span><span></span>
                </div>
                <p>AI is thinking...</p>
              </div>
            }
          </div>
          
          <!-- Input Area -->
          <div class="input-area">
            <div class="input-container">
              <textarea
                [(ngModel)]="messageInput"
                class="message-input"
                rows="2"
                placeholder="Ask about the valuation, challenge assumptions, or explore scenarios..."
                [disabled]="isStreaming()"
                (keydown.enter)="onEnterPress($any($event))"
              ></textarea>
              <button 
                class="send-btn"
                [disabled]="!messageInput.trim() || isStreaming()"
                (click)="sendMessage()"
              >
                @if (isStreaming()) {
                  <i class="pi pi-spin pi-spinner"></i>
                } @else {
                  <i class="pi pi-send"></i>
                }
              </button>
            </div>
            <p class="input-hint">Press Enter to send, Shift+Enter for new line</p>
          </div>
          
          <!-- Footer like DCF Analysis -->
          <footer class="notebook-footer">
            <div class="footer-content">
              <div class="footer-left">
                <div class="footer-links">
                  <a href="/" class="footer-link">Home</a>
                  <a href="/privacy" class="footer-link">Privacy Policy</a>
                  <a href="/faq" class="footer-link">FAQ</a>
                  <a href="mailto:support@stockvaluation.io" class="footer-link">Support</a>
                </div>
              </div>
              <div class="footer-center">
                @if (isStreaming()) {
                  <span class="status-indicator streaming">
                    <i class="pi pi-spin pi-spinner"></i>
                    Processing...
                  </span>
                } @else {
                  <span class="status-indicator ready">
                    <i class="pi pi-check-circle"></i>
                    Ready • {{ cells().length }} cells
                  </span>
                }
              </div>
              <div class="footer-right">
                <span class="footer-copyright">© {{ currentYear }} StockValuation.io</span>
              </div>
            </div>
          </footer>
        </div>
      </div>
      
      <!-- Thesis Preview Modal -->
      @if (showThesisModal()) {
        <div class="modal-overlay" (click)="cancelThesisModal()">
          <div class="thesis-modal" (click)="$event.stopPropagation()">
            <div class="modal-header">
              <h2>Save Investment Thesis</h2>
              <button class="close-btn" (click)="cancelThesisModal()">
                <i class="pi pi-times"></i>
              </button>
            </div>
            
            @if (thesisPreview()) {
              <div class="modal-body">
                <div class="preview-section">
                  <label>Title</label>
                  <h3 class="thesis-title">{{ thesisPreview()!.title }}</h3>
                </div>
                
                <div class="preview-section">
                  <label>Summary</label>
                  <p class="thesis-summary">{{ thesisPreview()!.summary }}</p>
                </div>
                
                <div class="preview-row">
                  <div class="preview-stat">
                    <span class="stat-label">Conviction</span>
                    <span class="stat-value conviction">{{ thesisPreview()!.conviction }}/10</span>
                  </div>
                  <div class="preview-stat">
                    <span class="stat-label">Fair Value</span>
                    <span class="stat-value">\${{ thesisPreview()!.fair_value | number:'1.2-2' }}</span>
                  </div>
                  <div class="preview-stat">
                    <span class="stat-label">Upside</span>
                    <span class="stat-value" [class.positive]="thesisPreview()!.upside_pct > 0" [class.negative]="thesisPreview()!.upside_pct < 0">
                      {{ thesisPreview()!.upside_pct > 0 ? '+' : '' }}{{ thesisPreview()!.upside_pct | number:'1.1-1' }}%
                    </span>
                  </div>
                </div>
                
                <div class="preview-section">
                  <label>Key Assumptions</label>
                  <ul class="assumptions-list">
                    @for (assumption of thesisPreview()!.key_assumptions; track assumption) {
                      <li>{{ assumption }}</li>
                    }
                  </ul>
                </div>
                
                <div class="preview-section">
                  <label>Key Risks</label>
                  <ul class="risks-list">
                    @for (risk of thesisPreview()!.risks; track risk) {
                      <li>{{ risk }}</li>
                    }
                  </ul>
                </div>
              </div>
              
              <div class="modal-footer">
                <button class="btn-cancel" (click)="cancelThesisModal()">Cancel</button>
                <button class="btn-save" (click)="confirmSaveThesis()">
                  <i class="pi pi-save"></i>
                  Save Thesis
                </button>
              </div>
            } @else {
              <div class="modal-loading">
                <div class="spinner"></div>
                <p>Generating thesis preview...</p>
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .notebook-container {
      display: flex;
      height: 100vh;
      background: var(--nb-bg-primary, #0d1117);
      color: var(--nb-text-primary, #e5e7eb);
    }
    
    .main-area {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
      overflow: hidden;
    }
    
    /* Tab Bar */
    .tab-bar {
      display: flex;
      gap: 0.25rem;
      padding: 0.5rem 1rem;
      background: var(--nb-bg-secondary, #161b22);
      border-bottom: 1px solid var(--nb-border, #30363d);
      overflow-x: auto;
    }
    
    .tab {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1rem;
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      color: #9ca3af;
      cursor: pointer;
      font-size: 0.875rem;
      white-space: nowrap;
      transition: all 0.2s;
    }
    
    .tab:hover {
      background: #21262d;
      color: #e5e7eb;
    }
    
    .tab.active {
      background: #21262d;
      border-bottom-color: #10b981;
      color: #f3f4f6;
    }
    
    .tab.dirty {
      color: #10b981;
    }
    
    .tab-title {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    
    .dirty-indicator {
      color: #10b981;
    }
    
    .tab-close {
      padding: 0.125rem;
      background: transparent;
      border: none;
      color: inherit;
      cursor: pointer;
      opacity: 0.5;
      transition: opacity 0.2s;
    }
    
    .tab:hover .tab-close {
      opacity: 1;
    }
    
    /* Notebook Content */
    .notebook-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }
    
    /* Header */
    .notebook-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 1rem;
      background: var(--nb-bg-secondary, #161b22);
      border-bottom: 1px solid var(--nb-border, #30363d);
    }
    
    .header-left {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    
    .menu-btn {
      padding: 0.5rem;
      background: transparent;
      border: none;
      color: #9ca3af;
      cursor: pointer;
      border-radius: 0.375rem;
      transition: background 0.2s;
    }
    
    .menu-btn:hover {
      background: #21262d;
      color: #e5e7eb;
    }
    
    .session-info {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    
    .session-divider {
      color: var(--nb-border, #30363d);
      font-size: 1.25rem;
      font-weight: 300;
      margin: 0 0.25rem;
    }
    
    .session-title {
      margin: 0;
      font-size: 1rem;
      font-weight: 600;
      color: #f3f4f6;
    }
    
    .ticker-badge {
      padding: 0.25rem 0.5rem;
      background: rgba(16, 185, 129, 0.2);
      color: #10b981;
      border-radius: 0.25rem;
      font-size: 0.75rem;
      font-weight: 500;
    }
    
    .header-right {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    
    .scenario-selector {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
    }
    
    .scenario-selector label {
      color: #9ca3af;
    }
    
    .scenario-selector select {
      padding: 0.375rem 0.75rem;
      background: #0d1117;
      border: 1px solid #30363d;
      border-radius: 0.375rem;
      color: #e5e7eb;
      font-size: 0.875rem;
    }
    
    .save-btn {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1rem;
      background: #10b981;
      border: none;
      border-radius: 0.375rem;
      color: white;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }
    
    .save-btn:hover:not(:disabled) {
      background: #059669;
    }
    
    .save-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    
    .close-notebook-btn {
      padding: 0.5rem;
      background: transparent;
      border: none;
      color: #9ca3af;
      cursor: pointer;
      border-radius: 0.375rem;
      transition: background 0.2s;
    }
    
    .close-notebook-btn:hover {
      background: #21262d;
      color: #ef4444;
    }
    
    /* Cells Container */
    .cells-container {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem;
    }
    
    .loading-state,
    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 300px;
      color: #6b7280;
    }
    
    .loading-state .spinner {
      width: 3rem;
      height: 3rem;
      border: 3px solid #30363d;
      border-top-color: #10b981;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin-bottom: 1rem;
    }
    
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
    
    .empty-state i {
      font-size: 3rem;
      margin-bottom: 1rem;
    }
    
    .streaming-indicator {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 1rem;
      color: #9ca3af;
    }
    
    .typing-dots {
      display: flex;
      gap: 0.25rem;
    }
    
    .typing-dots span {
      width: 0.5rem;
      height: 0.5rem;
      background: #10b981;
      border-radius: 50%;
      animation: bounce 1.4s infinite ease-in-out both;
    }
    
    .typing-dots span:nth-child(1) { animation-delay: -0.32s; }
    .typing-dots span:nth-child(2) { animation-delay: -0.16s; }
    
    @keyframes bounce {
      0%, 80%, 100% { transform: scale(0); }
      40% { transform: scale(1); }
    }
    
    /* Input Area */
    .input-area {
      padding: 1rem;
      background: var(--nb-bg-secondary, #161b22);
      border-top: 1px solid var(--nb-border, #30363d);
    }
    
    .input-container {
      display: flex;
      gap: 0.75rem;
      max-width: 800px;
      margin: 0 auto;
    }
    
    .message-input {
      flex: 1;
      padding: 0.75rem 1rem;
      background: var(--nb-bg-primary, #0d1117);
      border: 1px solid var(--nb-border, #30363d);
      border-radius: 0.5rem;
      color: var(--nb-text-primary, #e5e7eb);
      font-size: 0.875rem;
      resize: none;
      transition: border-color 0.2s;
    }
    
    .message-input:focus {
      outline: none;
      border-color: var(--nb-accent, #10b981);
    }
    
    .message-input:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }
    
    .send-btn {
      padding: 0.75rem 1.5rem;
      background: var(--nb-accent, #10b981);
      border: none;
      border-radius: 0.5rem;
      color: white;
      cursor: pointer;
      transition: background 0.2s;
    }
    
    .send-btn:hover:not(:disabled) {
      background: var(--nb-accent-hover, #059669);
    }
    
    .send-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    
    .input-hint {
      text-align: center;
      margin-top: 0.5rem;
      font-size: 0.75rem;
      color: var(--nb-text-muted, #6b7280);
    }
    
    /* Footer - DCF Analysis Style */
    .notebook-footer {
      background: var(--nb-bg-secondary, #161b22);
      border-top: 1px solid var(--nb-border, #30363d);
      padding: 0.75rem 0;
    }
    
    .footer-content {
      width: 100%;
      max-width: 1200px;
      margin: 0 auto;
      padding: 0 1rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }
    
    .footer-left, .footer-center, .footer-right {
      display: flex;
      align-items: center;
    }
    
    .footer-links {
      display: flex;
      gap: 1.5rem;
      align-items: center;
    }
    
    .footer-link {
      color: var(--nb-text-secondary, #9ca3af);
      text-decoration: none;
      font-size: 0.875rem;
      font-weight: 500;
      transition: color 0.2s;
    }
    
    .footer-link:hover {
      color: var(--nb-accent, #10b981);
    }
    
    .footer-copyright {
      color: var(--nb-text-muted, #6b7280);
      font-size: 0.8125rem;
    }
    
    .status-indicator {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.8125rem;
    }
    
    .status-indicator.streaming {
      color: var(--nb-accent, #10b981);
    }
    
    .status-indicator.ready {
      color: var(--nb-text-secondary, #9ca3af);
    }
    
    .theme-toggle-btn {
      padding: 0.5rem;
      background: transparent;
      border: none;
      color: var(--nb-text-secondary, #9ca3af);
      cursor: pointer;
      border-radius: 0.375rem;
      transition: all 0.2s;
    }
    
    .theme-toggle-btn:hover {
      background: var(--nb-bg-tertiary, #21262d);
      color: var(--nb-text-primary, #e5e7eb);
    }
    
    /* Mobile Responsive */
    @media (max-width: 768px) {
      .notebook-container {
        flex-direction: column;
      }
      
      .notebook-header {
        padding: 0.5rem;
        flex-wrap: wrap;
        gap: 0.5rem;
      }
      
      .header-left {
        flex: 1;
        min-width: 0;
      }
      
      .header-right {
        gap: 0.5rem;
      }
      
      .hide-mobile {
        display: none;
      }
      
      .save-btn {
        padding: 0.5rem;
      }
      
      .scenario-selector {
        display: none;
      }
      
      .session-info {
        flex: 1;
        min-width: 0;
      }
      
      .session-title {
        font-size: 0.875rem;
        max-width: 150px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      
      .cells-container {
        padding: 0.75rem;
      }
      
      .input-area {
        padding: 0.75rem;
      }
      
      .input-container {
        gap: 0.5rem;
      }
      
      .message-input {
        font-size: 16px; /* Prevents zoom on iOS */
        padding: 0.625rem 0.75rem;
      }
      
      .send-btn {
        padding: 0.625rem 1rem;
      }
      
      .input-hint {
        display: none;
      }
      
      .notebook-footer {
        padding: 0.5rem;
      }
      
      .footer-content {
        flex-direction: column;
        gap: 0.5rem;
        padding: 0;
      }
      
      .footer-links {
        display: none;
      }
      
      .footer-left {
        display: none;
      }
      
      .footer-center {
        order: 1;
      }
      
      .footer-right {
        order: 2;
      }
      
      .footer-copyright {
        font-size: 0.75rem;
      }
      
      /* Hide tabs on mobile - too cramped */
      .tab-bar {
        display: none;
      }
    }
    
    /* Tablet */
    @media (min-width: 769px) and (max-width: 1024px) {
      .sidebar {
        width: 240px;
      }
      
      .cells-container {
        padding: 1rem;
      }
      
      .session-title {
        max-width: 200px;
      }
    }
    
    /* Modal Styles */
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }
    
    .thesis-modal {
      background: var(--nb-bg-secondary, #161b22);
      border: 1px solid var(--nb-border, #30363d);
      border-radius: 0.75rem;
      max-width: 600px;
      width: 100%;
      max-height: 80vh;
      overflow-y: auto;
    }
    
    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.5rem;
      border-bottom: 1px solid var(--nb-border, #30363d);
    }
    
    .modal-header h2 {
      margin: 0;
      font-size: 1.125rem;
      color: #f3f4f6;
    }
    
    .close-btn {
      padding: 0.5rem;
      background: transparent;
      border: none;
      color: #9ca3af;
      cursor: pointer;
      border-radius: 0.25rem;
    }
    
    .close-btn:hover {
      background: #21262d;
      color: #ef4444;
    }
    
    .modal-body {
      padding: 1.5rem;
    }
    
    .preview-section {
      margin-bottom: 1.25rem;
    }
    
    .preview-section label {
      display: block;
      font-size: 0.75rem;
      text-transform: uppercase;
      color: #6b7280;
      margin-bottom: 0.375rem;
    }
    
    .thesis-title {
      margin: 0;
      font-size: 1.25rem;
      color: #f3f4f6;
    }
    
    .thesis-summary {
      margin: 0;
      color: #d1d5db;
      line-height: 1.6;
    }
    
    .preview-row {
      display: flex;
      gap: 1.5rem;
      margin-bottom: 1.25rem;
      padding: 1rem;
      background: #0d1117;
      border-radius: 0.5rem;
    }
    
    .preview-stat {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    
    .stat-label {
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    .stat-value {
      font-size: 1.25rem;
      font-weight: 600;
      color: #f3f4f6;
    }
    
    .stat-value.conviction {
      color: #10b981;
    }
    
    .stat-value.positive {
      color: #10b981;
    }
    
    .stat-value.negative {
      color: #ef4444;
    }
    
    .assumptions-list, .risks-list {
      margin: 0;
      padding-left: 1.25rem;
      color: #d1d5db;
    }
    
    .assumptions-list li, .risks-list li {
      margin-bottom: 0.5rem;
    }
    
    .risks-list {
      color: #fbbf24;
    }
    
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--nb-border, #30363d);
    }
    
    .btn-cancel {
      padding: 0.625rem 1.25rem;
      background: transparent;
      border: 1px solid #30363d;
      border-radius: 0.375rem;
      color: #9ca3af;
      cursor: pointer;
    }
    
    .btn-cancel:hover {
      background: #21262d;
      color: #e5e7eb;
    }
    
    .btn-save {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.625rem 1.25rem;
      background: #10b981;
      border: none;
      border-radius: 0.375rem;
      color: white;
      font-weight: 500;
      cursor: pointer;
    }
    
    .btn-save:hover {
      background: #059669;
    }
    
    .modal-loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 3rem;
      color: #9ca3af;
    }
    
    .modal-loading .spinner {
      width: 2rem;
      height: 2rem;
      border: 3px solid #30363d;
      border-top-color: #10b981;
      border-radius: 50%;
      animation: spin 1s linear infinite;
      margin-bottom: 1rem;
    }
  `]
})
export class NotebookContainerComponent implements OnInit, OnDestroy {
  @Input() ticker = '';
  @Input() valuationId?: string;
  @Input() sessionId?: string; // Existing session for continuity from chat

  @Output() closed = new EventEmitter<void>();

  private destroy$ = new Subject<void>();

  // Local state
  sidebarOpen = signal(true);
  messageInput = '';
  selectedScenarioId = '';
  currentYear = new Date().getFullYear();

  // Thesis preview modal state
  thesisPreview = signal<ThesisPreview | null>(null);
  showThesisModal = signal(false);

  // Theme service
  themeService = inject(ThemeService);
  isDarkTheme = computed(() => this.themeService.isDark());

  // Computed signals from service
  session = computed(() => this.notebookService.currentSession());
  cells = computed(() => this.notebookService.cells());
  scenarios = computed(() => this.notebookService.scenarios());
  tabs = computed(() => this.notebookService.tabs());
  activeTabId = computed(() => this.notebookService.activeTabId());
  isLoading = computed(() => this.notebookService.isLoading());
  isStreaming = computed(() => this.notebookService.isStreaming());
  streamingCellId = computed(() => this.notebookService.streamingCellId());

  constructor(private notebookService: NotebookService) { }

  ngOnInit(): void {
    // Load theses for sidebar
    this.notebookService.loadTheses().subscribe();

    // Prefer loading existing session for continuity, otherwise create new
    if (this.sessionId) {
      // Continue existing chat session in notebook mode
      console.log(`[NotebookContainer] Loading existing session: ${this.sessionId}`);
      this.notebookService.loadSession(this.sessionId).subscribe({
        error: (err) => {
          console.error('[NotebookContainer] Failed to load session, creating new:', err);
          // Fallback: create new session if loading fails
          if (this.ticker) {
            this.notebookService.createSession(this.ticker, this.valuationId).subscribe();
          }
        }
      });
    } else if (this.ticker) {
      // Create new session
      this.notebookService.createSession(this.ticker, this.valuationId).subscribe();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  sendMessage(): void {
    const message = this.messageInput.trim();
    if (!message || this.isStreaming()) return;

    this.notebookService.sendMessage(message);
    this.messageInput = '';
  }

  onEnterPress(event: KeyboardEvent): void {
    if (!event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  saveThesis(): void {
    this.notebookService.saveThesis().subscribe({
      next: (thesis) => {
        alert(`Thesis "${thesis.title}" saved successfully!`);
      },
      error: (err) => {
        console.error('Failed to save thesis:', err);
        alert('Failed to save thesis. Please try again.');
      }
    });
  }

  confirmSaveThesis(overrides?: { title?: string; summary?: string }): void {
    this.showThesisModal.set(false);

    const preview = this.thesisPreview();
    const saveRequest = {
      title: overrides?.title || preview?.title,
      summary: overrides?.summary || preview?.summary,
    };

    this.notebookService.saveThesis(saveRequest).subscribe({
      next: (thesis) => {
        alert(`Thesis "${thesis.title}" saved successfully!`);
        this.thesisPreview.set(null);
      },
      error: (err) => {
        console.error('Failed to save thesis:', err);
        alert('Failed to save thesis. Please try again.');
      }
    });
  }

  cancelThesisModal(): void {
    this.showThesisModal.set(false);
    this.thesisPreview.set(null);
  }

  switchTab(tabId: string): void {
    this.notebookService.switchTab(tabId);
  }

  closeTab(event: MouseEvent, tabId: string): void {
    event.stopPropagation();
    this.notebookService.closeTab(tabId);
  }

  closeNotebook(): void {
    this.closed.emit();
  }

  onThesisSelected(thesisId: string): void {
    this.notebookService.loadThesis(thesisId).subscribe();
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  // Cell action handlers
  onCellDelete(cellId: string): void {
    this.notebookService.deleteCell(cellId).subscribe();
  }

  onCellUpdateNotes(event: { cellId: string; notes: string }): void {
    this.notebookService.updateCell(event.cellId, { user_notes: event.notes }).subscribe();
  }
}
