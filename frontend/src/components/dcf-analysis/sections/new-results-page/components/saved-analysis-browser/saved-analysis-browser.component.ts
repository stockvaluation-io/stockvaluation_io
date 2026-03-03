import { Component, Output, EventEmitter, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { SavedAnalysisService, SavedAnalysis } from '../../../../services/saved-analysis.service';
import { CompanyData, ValuationResults } from '../../../../models';

@Component({
    selector: 'app-saved-analysis-browser',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="browser-overlay" (click)="onClose()">
      <div class="browser-modal" (click)="$event.stopPropagation()">
        <header class="browser-header">
          <h2 class="browser-title">
            <i class="pi pi-folder-open"></i>
            Saved Analyses
          </h2>
          <div class="header-actions">
            <span class="analysis-count">{{ savedAnalyses.length }} saved</span>
            <button 
              type="button" 
              class="clear-all-btn"
              *ngIf="savedAnalyses.length > 0"
              (click)="onClearAll()"
              [disabled]="isClearing">
              <i class="pi pi-trash"></i>
              Clear All
            </button>
            <button type="button" class="close-btn" (click)="onClose()">
              <i class="pi pi-times"></i>
            </button>
          </div>
        </header>

        <div class="browser-content">
          <!-- Search and Filter -->
          <div class="search-section" *ngIf="savedAnalyses.length > 0">
            <div class="search-input-wrapper">
              <i class="pi pi-search"></i>
              <input 
                type="text" 
                class="search-input"
                placeholder="Search by company name or symbol..."
                [(ngModel)]="searchTerm"
                (input)="filterAnalyses()">
            </div>
            <div class="filter-options">
              <label class="filter-label">Sort by:</label>
              <select class="sort-select" [(ngModel)]="sortBy" (change)="sortAnalyses()">
                <option value="date">Date Saved</option>
                <option value="company">Company Name</option>
                <option value="symbol">Symbol</option>
                <option value="upside">Upside Potential</option>
              </select>
            </div>
          </div>

          <!-- Empty State -->
          <div class="empty-state" *ngIf="savedAnalyses.length === 0">
            <i class="pi pi-folder-open"></i>
            <h3>No Saved Analyses</h3>
            <p>Your saved DCF analyses will appear here. Save an analysis to get started!</p>
          </div>

          <!-- No Results -->
          <div class="no-results" *ngIf="savedAnalyses.length > 0 && filteredAnalyses.length === 0">
            <i class="pi pi-search"></i>
            <h3>No Results Found</h3>
            <p>No analyses match your search criteria.</p>
          </div>

          <!-- Analysis List -->
          <div class="analysis-list" *ngIf="filteredAnalyses.length > 0">
            <div 
              *ngFor="let analysis of filteredAnalyses; trackBy: trackByAnalysis"
              class="analysis-card"
              [class.selected]="selectedAnalysis?.id === analysis.id">
              
              <div class="analysis-main" (click)="selectAnalysis(analysis)">
                <div class="analysis-info">
                  <h3 class="analysis-title">{{ analysis.title }}</h3>
                  <div class="company-info">
                    <span class="company-symbol">{{ analysis.company.symbol }}</span>
                    <span class="company-name">{{ analysis.company.name }}</span>
                  </div>
                  <p class="analysis-description" *ngIf="analysis.description">
                    {{ analysis.description }}
                  </p>
                  <div class="analysis-meta">
                    <span class="saved-date">
                      <i class="pi pi-calendar"></i>
                      {{ formatDate(analysis.savedAt) }}
                    </span>
                    <span class="fair-value">
                      Fair Value: \${{ analysis.results.intrinsicValue | number:'1.2-2' }}
                    </span>
                    <span 
                      class="upside-value"
                      [class.positive]="analysis.results.upside >= 0"
                      [class.negative]="analysis.results.upside < 0">
                      {{ analysis.results.upside >= 0 ? '+' : '' }}{{ analysis.results.upside | number:'1.1-1' }}%
                    </span>
                  </div>
                </div>
                
                <div class="analysis-actions">
                  <button 
                    type="button" 
                    class="load-btn"
                    (click)="onLoadAnalysis(analysis); $event.stopPropagation()"
                    title="Load this analysis">
                    <i class="pi pi-external-link"></i>
                    Load
                  </button>
                  <button 
                    type="button" 
                    class="export-btn"
                    (click)="onExportAnalysis(analysis); $event.stopPropagation()"
                    title="Export analysis data">
                    <i class="pi pi-download"></i>
                  </button>
                  <button 
                    type="button" 
                    class="delete-btn"
                    (click)="onDeleteAnalysis(analysis); $event.stopPropagation()"
                    title="Delete this analysis">
                    <i class="pi pi-trash"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <footer class="browser-footer" *ngIf="savedAnalyses.length > 0">
          <div class="footer-info">
            <span class="storage-info">
              <i class="pi pi-database"></i>
              {{ savedAnalyses.length }}/50 analyses stored locally
            </span>
          </div>
          <div class="footer-actions">
            <button 
              type="button" 
              class="import-btn"
              (click)="onImportAnalysis()">
              <i class="pi pi-upload"></i>
              Import Analysis
            </button>
          </div>
        </footer>
      </div>
    </div>

    <!-- Hidden file input for import -->
    <input 
      #fileInput 
      type="file" 
      accept=".json"
      style="display: none"
      (change)="handleFileImport($event)">
  `,
    styleUrls: ['./saved-analysis-browser.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class SavedAnalysisBrowserComponent implements OnInit {
  @Output() close = new EventEmitter<void>();
  @Output() loadAnalysis = new EventEmitter<{ company: CompanyData; results: ValuationResults }>();

  savedAnalyses: SavedAnalysis[] = [];
  filteredAnalyses: SavedAnalysis[] = [];
  selectedAnalysis: SavedAnalysis | null = null;
  searchTerm = '';
  sortBy: 'date' | 'company' | 'symbol' | 'upside' = 'date';
  isClearing = false;

  private destroyRef = inject(DestroyRef);

  constructor(
    private savedAnalysisService: SavedAnalysisService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.savedAnalysisService.savedAnalyses$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((analyses: SavedAnalysis[]) => {
        this.savedAnalyses = analyses;
        this.filterAnalyses();
        this.cdr.markForCheck();
      });
  }


  onClose(): void {
    this.close.emit();
  }

  selectAnalysis(analysis: SavedAnalysis): void {
    this.selectedAnalysis = this.selectedAnalysis?.id === analysis.id ? null : analysis;
  }

  onLoadAnalysis(analysis: SavedAnalysis): void {
    this.loadAnalysis.emit({
      company: analysis.company,
      results: analysis.results
    });
    this.close.emit();
  }

  onDeleteAnalysis(analysis: SavedAnalysis): void {
    if (confirm(`Are you sure you want to delete the analysis for ${analysis.company.name} (${analysis.company.symbol})?`)) {
      this.savedAnalysisService.deleteAnalysis(analysis.id);
    }
  }

  onClearAll(): void {
    if (confirm('Are you sure you want to delete all saved analyses? This action cannot be undone.')) {
      this.isClearing = true;
      this.savedAnalysisService.clearAllAnalyses();
      this.isClearing = false;
    }
  }

  onExportAnalysis(analysis: SavedAnalysis): void {
    const exportData = this.savedAnalysisService.exportAnalysis(analysis.id);
    if (exportData) {
      const blob = new Blob([exportData], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${analysis.company.symbol}-${analysis.company.name}-dcf-analysis.json`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    }
  }

  onImportAnalysis(): void {
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    fileInput?.click();
  }

  handleFileImport(event: Event): void {
    const target = event.target as HTMLInputElement;
    const file = target.files?.[0];
    
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result as string;
        const imported = this.savedAnalysisService.importAnalysis(content);
        
        if (imported) {
          alert(`Successfully imported analysis for ${imported.company.name} (${imported.company.symbol})`);
        } else {
          alert('Failed to import analysis. Please check the file format.');
        }
      };
      reader.readAsText(file);
    }
    
    // Reset file input
    target.value = '';
  }

  filterAnalyses(): void {
    let filtered = [...this.savedAnalyses];

    if (this.searchTerm.trim()) {
      const term = this.searchTerm.toLowerCase();
      filtered = filtered.filter(analysis =>
        analysis.company.name.toLowerCase().includes(term) ||
        analysis.company.symbol.toLowerCase().includes(term) ||
        analysis.title.toLowerCase().includes(term)
      );
    }

    this.filteredAnalyses = filtered;
    this.sortAnalyses();
  }

  sortAnalyses(): void {
    this.filteredAnalyses.sort((a, b) => {
      switch (this.sortBy) {
        case 'date':
          return new Date(b.savedAt).getTime() - new Date(a.savedAt).getTime();
        case 'company':
          return a.company.name.localeCompare(b.company.name);
        case 'symbol':
          return a.company.symbol.localeCompare(b.company.symbol);
        case 'upside':
          return b.results.upside - a.results.upside;
        default:
          return 0;
      }
    });
  }

  formatDate(date: Date): string {
    return new Date(date).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  trackByAnalysis(index: number, analysis: SavedAnalysis): string {
    return analysis.id;
  }
}