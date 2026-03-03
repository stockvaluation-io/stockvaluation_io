import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';

import { NavigationItem } from '../../new-results-page.component';
import { ThemeToggleComponent } from '../../../../../shared/theme-toggle/theme-toggle.component';

@Component({
  selector: 'app-navigation-menu',
  imports: [CommonModule, ThemeToggleComponent],
  template: `
    <nav class="navigation-menu">
      <div class="nav-container">
        
        <!-- Mobile Menu Toggle -->
        <div class="mobile-menu-header">
          <div class="mobile-active-section">
            <span class="mobile-section-title">{{ getActiveItem()?.title || 'Navigation' }}</span>
          </div>
          <div class="mobile-header-actions">
            <app-theme-toggle class="mobile-theme-toggle"></app-theme-toggle>
            <button 
              type="button"
              class="mobile-menu-toggle"
              [class.open]="isMobileMenuOpen"
              (click)="toggleMobileMenu()"
              [attr.aria-label]="isMobileMenuOpen ? 'Close navigation menu' : 'Open navigation menu'"
              [attr.aria-expanded]="isMobileMenuOpen">
              <span class="hamburger-line"></span>
              <span class="hamburger-line"></span>
              <span class="hamburger-line"></span>
            </button>
          </div>
        </div>

        <!-- Desktop Horizontal Scroll & Mobile Dropdown -->
        <div class="nav-scroll-container" [class.mobile-open]="isMobileMenuOpen">
          <div class="nav-items">
            <button
              type="button"
              *ngFor="let item of navigationItems; trackBy: trackByItemId"
              class="nav-item"
              [class.active]="item.id === activeSection"
              [attr.aria-label]="'Navigate to ' + item.title"
              (click)="onItemClick(item.id)">
              
              <div class="nav-item-content">
                <span class="nav-item-title">{{ item.title }}</span>
              </div>
              
              <!-- Active indicator -->
              <div class="active-indicator" *ngIf="item.id === activeSection"></div>
            </button>
          </div>
        </div>
        
        <!-- Mobile Menu Backdrop -->
        <div 
          class="mobile-backdrop"
          [class.visible]="isMobileMenuOpen"
          (click)="closeMobileMenu()">
        </div>
      </div>
    </nav>
  `,
  styleUrls: ['./navigation-menu.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NavigationMenuComponent implements OnInit {
  @Input() navigationItems: NavigationItem[] = [];

  private _activeSection = '';
  @Input()
  get activeSection(): string {
    return this._activeSection;
  }
  set activeSection(value: string) {
    if (this._activeSection !== value) {
      this._activeSection = value;
      this.cdr.markForCheck();
    }
  }

  @Output() sectionClicked = new EventEmitter<string>();

  isMobileMenuOpen = false;

  constructor(private cdr: ChangeDetectorRef) { }

  ngOnInit(): void {
  }

  onItemClick(sectionId: string): void {
    // Scroll to the section with proper offset calculation
    const element = document.getElementById(sectionId);
    if (element) {
      // Find the actual scroll container (.dcf-content)
      const scrollContainer = document.querySelector('.dcf-content') as HTMLElement;
      if (!scrollContainer) {
        return;
      }

      // Fixed offset for navigation scroll positioning
      const totalOffset = 20;


      // Calculate element position and apply the offset
      const elementPosition = element.offsetTop - totalOffset;


      // Scroll the correct container (.dcf-content)
      scrollContainer.scrollTo({
        top: Math.max(0, elementPosition),
        behavior: 'smooth'
      });
    }

    // Emit event to update active section
    this.sectionClicked.emit(sectionId);

    // Close mobile menu after navigation
    this.closeMobileMenu();
  }

  toggleMobileMenu(): void {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  closeMobileMenu(): void {
    this.isMobileMenuOpen = false;
  }

  getActiveItem(): NavigationItem | undefined {
    return this.navigationItems.find(item => item.id === this._activeSection);
  }

  trackByItemId(index: number, item: NavigationItem): string {
    return item.id;
  }
}