import { Component, Input, Output, EventEmitter, signal, computed, effect, OnInit, inject, PLATFORM_ID } from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotebookService } from './notebook.service';
import { Thesis, GroupedTheses } from './cell.models';

/**
 * Thesis Sidebar Component
 * Left panel showing saved theses grouped by company and date.
 */
@Component({
  selector: 'app-thesis-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <!-- Overlay for mobile -->
    @if (isOpen && isMobile()) {
      <div class="overlay" (click)="close.emit()"></div>
    }
    
    <!-- Sidebar -->
    <div 
      class="sidebar"
      [class.open]="isOpen"
      [class.mobile]="isMobile()"
    >
      <!-- Header -->
      <div class="sidebar-header">
        <h2 class="sidebar-title">Saved Theses</h2>
        <button class="close-btn mobile-only" (click)="close.emit()">
          <i class="pi pi-times"></i>
        </button>
      </div>
      
      <!-- Search -->
      <div class="search-container">
        <i class="pi pi-search"></i>
        <input
          type="text"
          [(ngModel)]="searchQuery"
          placeholder="Search theses..."
          class="search-input"
        />
      </div>
      
      <!-- Theses List -->
      <div class="theses-list">
        @if (isLoading()) {
          <div class="loading-state">
            <p>Loading...</p>
          </div>
        } @else if (filteredThesesKeys().length === 0) {
          <div class="empty-state">
            <p>{{ searchQuery ? 'No theses found' : 'No saved theses yet' }}</p>
          </div>
        } @else {
          @for (ticker of filteredThesesKeys(); track ticker) {
            <div class="company-group">
              <!-- Company Header -->
              <button 
                class="company-header"
                (click)="toggleCompany(ticker)"
              >
                <i [class]="expandedCompanies().has(ticker) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"></i>
                <span class="company-name">{{ getCompanyName(ticker) }}</span>
                <span class="thesis-count">{{ getThesisCount(ticker) }}</span>
              </button>
              
              <!-- Date Groups -->
              @if (expandedCompanies().has(ticker)) {
                <div class="date-groups">
                  @for (monthYear of getDateKeys(ticker); track monthYear) {
                    <div class="date-group">
                      <!-- Date Header -->
                      <button 
                        class="date-header"
                        (click)="toggleDate(ticker + '-' + monthYear)"
                      >
                        <i [class]="expandedDates().has(ticker + '-' + monthYear) ? 'pi pi-chevron-down' : 'pi pi-chevron-right'"></i>
                        <span class="date-label">{{ formatMonthYear(monthYear) }}</span>
                        <span class="date-count">{{ getThesesForDate(ticker, monthYear).length }}</span>
                      </button>
                      
                      <!-- Thesis Items -->
                      @if (expandedDates().has(ticker + '-' + monthYear)) {
                        <div class="thesis-items">
                          @for (thesis of getThesesForDate(ticker, monthYear); track thesis.id) {
                            <button 
                              class="thesis-item"
                              (click)="onThesisClick(thesis)"
                            >
                              <i class="pi pi-file-o"></i>
                              <span class="thesis-content">
                                <span class="thesis-title">{{ thesis.title }}</span>
                                <span class="thesis-meta">{{ thesis.company_name || thesis.ticker }} • {{ formatThesisDate(thesis.created_at) }}</span>
                              </span>
                            </button>
                          }
                        </div>
                      }
                    </div>
                  }
                </div>
              }
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 40;
    }
    
    .sidebar {
      position: fixed;
      left: 0;
      top: 0;
      height: 100%;
      width: 280px;
      background: #161b22;
      border-right: 1px solid #30363d;
      z-index: 50;
      display: flex;
      flex-direction: column;
      transform: translateX(-100%);
      transition: transform 0.3s ease;
    }
    
    .sidebar.open {
      transform: translateX(0);
    }
    
    @media (min-width: 1024px) {
      .sidebar {
        position: relative;
        transform: translateX(0);
      }
      
      .sidebar:not(.open) {
        display: none;
      }
      
      .mobile-only {
        display: none;
      }
    }
    
    /* Header */
    .sidebar-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem;
      border-bottom: 1px solid #30363d;
    }
    
    .sidebar-title {
      margin: 0;
      font-size: 0.875rem;
      font-weight: 600;
      color: #e5e7eb;
    }
    
    .close-btn {
      padding: 0.25rem;
      background: transparent;
      border: none;
      color: #9ca3af;
      cursor: pointer;
      border-radius: 0.25rem;
      transition: background 0.2s;
    }
    
    .close-btn:hover {
      background: #21262d;
      color: #e5e7eb;
    }
    
    /* Search */
    .search-container {
      position: relative;
      padding: 0.75rem;
      border-bottom: 1px solid #30363d;
    }
    
    .search-container i {
      position: absolute;
      left: 1.25rem;
      top: 50%;
      transform: translateY(-50%);
      color: #6b7280;
      font-size: 0.875rem;
    }
    
    .search-input {
      width: 100%;
      padding: 0.5rem 0.75rem 0.5rem 2rem;
      background: #0d1117;
      border: 1px solid #30363d;
      border-radius: 0.375rem;
      color: #e5e7eb;
      font-size: 0.875rem;
    }
    
    .search-input::placeholder {
      color: #6b7280;
    }
    
    .search-input:focus {
      outline: none;
      border-color: #10b981;
    }
    
    /* Theses List */
    .theses-list {
      flex: 1;
      overflow-y: auto;
      padding: 0.5rem 0;
    }
    
    .loading-state,
    .empty-state {
      padding: 1rem;
      text-align: center;
      color: #6b7280;
      font-size: 0.875rem;
    }
    
    /* Company Group */
    .company-group {
      margin-bottom: 0.25rem;
    }
    
    .company-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      padding: 0.5rem 0.75rem;
      background: transparent;
      border: none;
      color: #d1d5db;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      text-align: left;
      transition: background 0.2s;
    }
    
    .company-header:hover {
      background: #21262d;
    }
    
    .company-header i {
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    .company-name {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    
    .thesis-count,
    .date-count {
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    /* Date Groups */
    .date-groups {
      margin-left: 1rem;
    }
    
    .date-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      width: 100%;
      padding: 0.375rem 0.75rem;
      background: transparent;
      border: none;
      color: #9ca3af;
      font-size: 0.75rem;
      font-weight: 500;
      cursor: pointer;
      text-align: left;
      transition: background 0.2s;
    }
    
    .date-header:hover {
      background: #21262d;
    }
    
    .date-header i {
      font-size: 0.625rem;
    }
    
    .date-label {
      flex: 1;
    }
    
    /* Thesis Items */
    .thesis-items {
      margin-left: 1rem;
    }
    
    .thesis-item {
      display: flex;
      align-items: flex-start;
      gap: 0.5rem;
      width: 100%;
      padding: 0.5rem 0.75rem;
      background: transparent;
      border: none;
      color: #d1d5db;
      font-size: 0.75rem;
      cursor: pointer;
      text-align: left;
      transition: background 0.2s, color 0.2s;
    }
    
    .thesis-item:hover {
      background: #21262d;
      color: #f3f4f6;
    }
    
    .thesis-item:hover i {
      color: #10b981;
    }
    
    .thesis-item i {
      color: #6b7280;
      flex-shrink: 0;
      margin-top: 0.125rem;
    }
    
    .thesis-title {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      line-height: 1.4;
    }

    .thesis-content {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;
      gap: 0.125rem;
    }

    .thesis-meta {
      color: #9ca3af;
      font-size: 0.6875rem;
      line-height: 1.2;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  `]
})
export class ThesisSidebarComponent implements OnInit {
  @Input() isOpen = true;

  @Output() close = new EventEmitter<void>();
  @Output() thesisSelected = new EventEmitter<string>();

  searchQuery = '';

  // State signals
  expandedCompanies = signal<Set<string>>(new Set());
  expandedDates = signal<Set<string>>(new Set());
  isMobile = signal(false);

  // From service
  groupedTheses = computed(() => this.notebookService.groupedTheses());
  isLoading = computed(() => this.notebookService.isLoading());

  private platformId = inject(PLATFORM_ID);

  constructor(private notebookService: NotebookService) {
    effect(() => {
      const grouped = this.groupedTheses();
      const keys = Object.keys(grouped || {});
      if (keys.length === 0) return;

      // Auto-expand first company/date once data arrives.
      if (this.expandedCompanies().size === 0) {
        this.expandedCompanies.set(new Set([keys[0]]));
      }

      const firstTicker = keys[0];
      const firstMonth = this.getDateKeys(firstTicker)[0];
      if (firstMonth) {
        const dateKey = `${firstTicker}-${firstMonth}`;
        if (!this.expandedDates().has(dateKey)) {
          const nextDates = new Set(this.expandedDates());
          nextDates.add(dateKey);
          this.expandedDates.set(nextDates);
        }
      }
    });
  }

  ngOnInit(): void {
    // Guard browser-only code for SSR compatibility
    if (isPlatformBrowser(this.platformId)) {
      this.checkMobile();
      window.addEventListener('resize', () => this.checkMobile());
    }

    // Auto-expand first company
    const grouped = this.groupedTheses();
    const firstTicker = Object.keys(grouped)[0];
    if (firstTicker) {
      this.expandedCompanies.set(new Set([firstTicker]));
    }
  }

  private checkMobile(): void {
    // Only access window in browser
    if (isPlatformBrowser(this.platformId)) {
      this.isMobile.set(window.innerWidth < 1024);
    }
  }

  filteredThesesKeys(): string[] {
    const grouped = this.groupedTheses();
    if (!grouped) return [];

    if (!this.searchQuery.trim()) {
      return Object.keys(grouped);
    }

    const query = this.searchQuery.toLowerCase();
    return Object.keys(grouped).filter(ticker => {
      const dateGroups = grouped[ticker];
      return Object.values(dateGroups).some(theses =>
        theses.some(t =>
          (t.title || '').toLowerCase().includes(query) ||
          (t.ticker || '').toLowerCase().includes(query) ||
          (t.company_name || '').toLowerCase().includes(query) ||
          (t.summary || '').toLowerCase().includes(query)
        )
      );
    });
  }

  getCompanyName(ticker: string): string {
    const grouped = this.groupedTheses();
    const dateGroups = grouped[ticker];
    if (!dateGroups) return ticker;

    const firstMonthTheses = Object.values(dateGroups)[0];
    return firstMonthTheses?.[0]?.company_name || ticker;
  }

  getThesisCount(ticker: string): number {
    const grouped = this.groupedTheses();
    const dateGroups = grouped[ticker];
    if (!dateGroups) return 0;

    return Object.values(dateGroups).reduce((sum, theses) => sum + theses.length, 0);
  }

  getDateKeys(ticker: string): string[] {
    const grouped = this.groupedTheses();
    const dateGroups = grouped[ticker];
    if (!dateGroups) return [];

    return Object.keys(dateGroups).sort().reverse();
  }

  getThesesForDate(ticker: string, monthYear: string): Thesis[] {
    const grouped = this.groupedTheses();
    return grouped[ticker]?.[monthYear] || [];
  }

  formatMonthYear(monthYear: string): string {
    const [year, month] = monthYear.split('-');
    const date = new Date(parseInt(year), parseInt(month) - 1);
    return date.toLocaleDateString('en-US', { month: 'short', year: 'numeric' });
  }

  formatThesisDate(dateString: string): string {
    if (!dateString) return '';
    const date = new Date(dateString);
    if (Number.isNaN(date.getTime())) return dateString;
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  toggleCompany(ticker: string): void {
    const current = this.expandedCompanies();
    const next = new Set(current);
    if (next.has(ticker)) {
      next.delete(ticker);
    } else {
      next.add(ticker);
    }
    this.expandedCompanies.set(next);
  }

  toggleDate(key: string): void {
    const current = this.expandedDates();
    const next = new Set(current);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.expandedDates.set(next);
  }

  onThesisClick(thesis: Thesis): void {
    this.thesisSelected.emit(thesis.id);

    // Close sidebar on mobile
    if (this.isMobile()) {
      this.close.emit();
    }
  }
}
