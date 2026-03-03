import { Component, EventEmitter, Input, Output, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface SelectorOption {
  id: string;
  title: string;
  description?: string;
  icon?: string;
  badge?: string;
  disabled?: boolean;
}

export interface SelectorConfig {
  placeholder?: string;
  closeOnSelect?: boolean;
  showDescriptions?: boolean;
  showIcons?: boolean;
  showBadges?: boolean;
}

/**
 * Generic dropdown selector component
 * Can be used for analysis types, filters, or any other selection
 */
@Component({
  selector: 'app-generic-selector',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="generic-selector" [class.disabled]="disabled">
      <div class="dropdown-container" [class.open]="isDropdownOpen">
        <!-- Dropdown trigger -->
        <button 
          class="dropdown-trigger" 
          type="button"
          (click)="toggleDropdown()"
          [attr.aria-expanded]="isDropdownOpen"
          [disabled]="disabled"
          aria-haspopup="true"
        >
          <div class="trigger-content">
            <i class="pi" 
               [class]="selectedOption?.icon"
               *ngIf="config.showIcons !== false && selectedOption?.icon"></i>
            <span class="trigger-title">
              {{ selectedOption?.title || config.placeholder || 'Select an option' }}
            </span>
            <span class="trigger-badge" 
                  *ngIf="config.showBadges !== false && selectedOption?.badge">
              {{ selectedOption!.badge }}
            </span>
          </div>
          <i class="pi pi-chevron-down dropdown-icon" [class.rotated]="isDropdownOpen"></i>
        </button>

        <!-- Dropdown menu -->
        <div class="dropdown-menu" *ngIf="isDropdownOpen">
          <div class="dropdown-option" 
               *ngFor="let option of options; trackBy: trackByOptionId"
               [class.selected]="selectedValue === option.id"
               [class.disabled]="option.disabled"
               (click)="selectOption(option)">
            <div class="option-content">
              <div class="option-header">
                <i class="pi option-icon" 
                   [class]="option.icon"
                   *ngIf="config.showIcons !== false && option.icon"></i>
                <span class="option-title">{{ option.title }}</span>
                <span class="option-badge" 
                      *ngIf="config.showBadges !== false && option.badge">
                  {{ option.badge }}
                </span>
              </div>
              <p class="option-description" 
                 *ngIf="config.showDescriptions !== false && option.description">
                {{ option.description }}
              </p>
            </div>
            <div class="selection-check" *ngIf="selectedValue === option.id">
              <i class="pi pi-check"></i>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./generic-selector.component.scss']
})
export class GenericSelectorComponent implements OnInit, OnDestroy {
  @Input() options: SelectorOption[] = [];
  @Input() selectedValue: string | null = null;
  @Input() config: SelectorConfig = {};
  @Input() disabled = false;
  
  @Output() selectionChanged = new EventEmitter<SelectorOption>();

  isDropdownOpen = false;
  private clickListener?: (event: Event) => void;

  get selectedOption(): SelectorOption | undefined {
    return this.options.find(option => option.id === this.selectedValue);
  }

  ngOnInit(): void {
    this.setupClickOutside();
  }

  ngOnDestroy(): void {
    if (this.clickListener) {
      document.removeEventListener('click', this.clickListener as EventListener);
    }
  }

  private setupClickOutside(): void {
    this.clickListener = (event: Event) => {
      const dropdown = document.querySelector('.generic-selector');
      if (dropdown && !dropdown.contains(event.target as Node)) {
        this.isDropdownOpen = false;
      }
    };
    document.addEventListener('click', this.clickListener as EventListener);
  }

  toggleDropdown(): void {
    if (!this.disabled) {
      this.isDropdownOpen = !this.isDropdownOpen;
    }
  }

  selectOption(option: SelectorOption): void {
    if (option.disabled) {
      return;
    }

    this.selectedValue = option.id;
    this.selectionChanged.emit(option);
    
    if (this.config.closeOnSelect !== false) {
      this.isDropdownOpen = false;
    }
  }

  trackByOptionId(index: number, option: SelectorOption): string {
    return option.id;
  }
}