import { Component, Input, Output, EventEmitter, OnInit, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CompanySearchResult } from '../../../core/services';

export type SearchInputSize = 'sm' | 'md' | 'lg';

export interface SearchInputConfig {
  placeholder?: string;
  debounceMs?: number;
  showClearButton?: boolean;
  readonly?: boolean;
  size?: SearchInputSize;
  isInitializing?: boolean;
}

/**
 * Pure UI component for company search input
 * Handles only input rendering and debounced query emission
 */
@Component({
    selector: 'app-company-search-input',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="search-input-wrapper" 
         [ngClass]="'size-' + (config.size || 'md')"
         [class.initializing]="isInitializing">
      
      <!-- Initializing overlay -->
      <div class="search-initializing-overlay" *ngIf="isInitializing">
        <div class="initializing-content">
          <i class="pi pi-spin pi-spinner initializing-spinner"></i>
          <span class="initializing-message">Initialising search...</span>
        </div>
      </div>
      
      <!-- Search icon or loading indicator -->
      <i class="pi pi-search search-icon" 
         aria-hidden="true" 
         *ngIf="!loading && !isInitializing"></i>
      
      <!-- Loading indicator with selected company -->
      <div class="loading-company-indicator" *ngIf="loading && selectedCompany && !isInitializing">
        <div class="loading-company-logo">
          <img 
            [src]="companyLogoUrl" 
            [alt]="selectedCompany.name + ' logo'"
            (error)="onLogoError()"
            *ngIf="!logoError; else logoPlaceholder"
          />
          <ng-template #logoPlaceholder>
            <div class="loading-logo-placeholder">
              {{ companyInitials }}
            </div>
          </ng-template>
        </div>
        <div class="loading-company-info">
          <span class="loading-company-symbol">{{ selectedCompany.symbol }}</span>
          <span class="loading-status">{{ loadingMessage }}</span>
        </div>
        <i class="pi pi-spin pi-spinner loading-spinner"></i>
      </div>

      <!-- Search input -->
      <input
        #searchInput
        class="search-input"
        [class.loading]="loading && selectedCompany"
        [class.initializing]="isInitializing"
        type="text"
        [placeholder]="isInitializing ? 'Preparing search...' : (config.placeholder || 'Search by company name or stock symbol...')"
        [(ngModel)]="searchQuery"
        (input)="onInput($event)"
        [attr.aria-label]="config.placeholder || 'Search companies'"
        autocomplete="off"
        [readonly]="config.readonly || (loading && selectedCompany) || isInitializing"
        [disabled]="isInitializing"
      />
      
      <!-- Clear button -->
      <button 
        *ngIf="searchQuery && config.showClearButton !== false && !loading && !isInitializing"
        class="clear-button"
        type="button"
        (click)="onClear()"
        aria-label="Clear search"
      >
        <i class="pi pi-times" aria-hidden="true"></i>
      </button>
    </div>
  `,
    styleUrls: ['./company-search-input.component.scss']
})
export class CompanySearchInputComponent implements OnInit {
  @Input() config: SearchInputConfig = {};
  @Input() loading = false;
  @Input() loadingMessage = 'Searching...';
  @Input() selectedCompany: CompanySearchResult | null = null;
  @Input() companyLogoUrl = '';
  @Input() companyInitials = '';
  @Input() isInitializing = false;
  
  @Output() queryChanged = new EventEmitter<string>();
  @Output() clearClicked = new EventEmitter<void>();

  searchQuery = '';
  logoError = false;
  
  private searchSubject = new BehaviorSubject<string>('');
  private destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.setupSearch();
  }


  private setupSearch(): void {
    const debounceMs = this.config.debounceMs || 300;
    
    this.searchSubject
      .pipe(
        debounceTime(debounceMs),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(query => {
        this.queryChanged.emit(query);
      });
  }

  onInput(event: any): void {
    const query = event.target.value;
    this.searchQuery = query;
    this.searchSubject.next(query);
  }

  onClear(): void {
    this.searchQuery = '';
    this.searchSubject.next('');
    this.clearClicked.emit();
  }

  onLogoError(): void {
    this.logoError = true;
  }
}