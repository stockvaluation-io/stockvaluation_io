import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { QuickAction } from './message-formatter.service';

@Component({
  selector: 'app-message-actions',
  standalone: true,
  imports: [CommonModule, ButtonModule],
  template: `
    <div class="message-actions">
      @for (action of actions; track action.action) {
        <button 
          pButton
          type="button"
          class="action-btn"
          [class.action-primary]="action.primary"
          (click)="handleAction(action)">
          <i [class]="action.icon"></i>
          <span class="action-label">{{ action.label }}</span>
        </button>
      }
    </div>
  `,
  styles: [`
    .message-actions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2, 0.5rem);
      margin-top: var(--space-3, 0.75rem);
    }
    
    // Override PrimeNG button styles completely
    .action-btn {
      // Reset PrimeNG defaults
      all: unset;
      box-sizing: border-box;
      
      // Professional button styling
      display: inline-flex !important;
      align-items: center !important;
      justify-content: center !important;
      gap: var(--space-2, 0.5rem) !important;
      padding: 0.625rem 1rem !important;
      min-height: 36px !important;
      border: 1px solid transparent !important;
      border-radius: 8px !important;
      background: var(--color-bg-secondary, rgba(16, 185, 129, 0.08)) !important;
      color: var(--color-primary, #10b981) !important;
      font-size: 0.875rem !important;
      font-weight: 500 !important;
      font-family: inherit !important;
      line-height: 1.5 !important;
      cursor: pointer !important;
      white-space: nowrap !important;
      outline: none !important;
      user-select: none !important;
      -webkit-tap-highlight-color: transparent !important;
      
      // Smooth single transition (prevents flickering)
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
      
      // Remove PrimeNG's default styles
      &::before,
      &::after {
        display: none !important;
      }
      
      i {
        font-size: 0.875rem !important;
        color: inherit !important;
        line-height: 1 !important;
        display: inline-flex !important;
        align-items: center !important;
        transition: none !important;
      }
      
      .action-label {
        line-height: 1.5 !important;
        transition: none !important;
      }
      
      // Hover state - smooth and professional
      &:hover:not(:disabled) {
        background: var(--color-bg-hover, rgba(16, 185, 129, 0.12)) !important;
        border-color: var(--color-primary-alpha, rgba(16, 185, 129, 0.2)) !important;
        color: var(--color-primary-dark, #059669) !important;
        transform: translateY(-1px) !important;
        box-shadow: 0 4px 12px rgba(16, 185, 129, 0.15) !important;
      }
      
      // Active state
      &:active:not(:disabled) {
        transform: translateY(0) !important;
        box-shadow: 0 2px 6px rgba(16, 185, 129, 0.2) !important;
      }
      
      // Focus state for accessibility
      &:focus-visible {
        outline: 2px solid var(--color-primary, #10b981) !important;
        outline-offset: 2px !important;
        border-color: var(--color-primary, #10b981) !important;
      }
      
      // Disabled state
      &:disabled {
        opacity: 0.5 !important;
        cursor: not-allowed !important;
        transform: none !important;
        box-shadow: none !important;
        pointer-events: none !important;
      }
      
      // Primary button variant
      &.action-primary {
        background: var(--color-primary, #10b981) !important;
        color: white !important;
        font-weight: 600 !important;
        box-shadow: 0 2px 8px rgba(16, 185, 129, 0.25) !important;
        border-color: transparent !important;
        
        i {
          color: white !important;
        }
        
        &:hover:not(:disabled) {
          background: var(--color-primary-dark, #059669) !important;
          color: white !important;
          transform: translateY(-2px) !important;
          box-shadow: 0 6px 16px rgba(16, 185, 129, 0.35) !important;
        }
        
        &:active:not(:disabled) {
          background: var(--color-primary-darker, #047857) !important;
          transform: translateY(0) !important;
          box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3) !important;
        }
        
        &:focus-visible {
          outline-color: var(--color-primary-dark, #059669) !important;
        }
      }
    }
    
    .action-label {
      line-height: 1.5 !important;
      display: inline-block !important;
      
      @media (max-width: 640px) {
        display: none !important;
      }
    }
    
    // Mobile responsive
    @media (max-width: 640px) {
      .message-actions {
        gap: 0.5rem !important;
      }
      
      .action-btn {
        padding: 0.625rem !important;
        min-width: 44px !important;
        min-height: 44px !important;
        justify-content: center !important;
        
        i {
          font-size: 1rem !important;
          margin: 0 !important;
        }
      }
    }
    
    // Dark theme support
    :host-context([data-theme="dark"]) {
      .action-btn {
        background: rgba(16, 185, 129, 0.15) !important;
        color: var(--color-primary, #10b981) !important;
        border-color: rgba(16, 185, 129, 0.2) !important;
        
        &:hover:not(:disabled) {
          background: rgba(16, 185, 129, 0.22) !important;
          border-color: rgba(16, 185, 129, 0.35) !important;
          color: var(--color-primary-light, #34d399) !important;
        }
        
        &.action-primary {
          background: var(--color-primary, #10b981) !important;
          color: white !important;
          
          &:hover:not(:disabled) {
            background: var(--color-primary-dark, #059669) !important;
          }
        }
      }
    }
    
    // Light theme support
    :host-context([data-theme="light"]) {
      .action-btn {
        background: var(--color-bg-secondary, rgba(16, 185, 129, 0.08)) !important;
        color: var(--color-primary, #10b981) !important;
        
        &:hover:not(:disabled) {
          background: var(--color-bg-hover, rgba(16, 185, 129, 0.12)) !important;
          border-color: var(--color-primary-alpha, rgba(16, 185, 129, 0.2)) !important;
        }
      }
    }
  `]
})
export class MessageActionsComponent {
  @Input() actions: QuickAction[] = [];
  @Output() actionClicked = new EventEmitter<QuickAction>();
  
  handleAction(action: QuickAction) {
    this.actionClicked.emit(action);
  }
}

