import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ChatSuggestion {
  icon: string;
  text: string;
  category: 'valuation' | 'scenario' | 'risk' | 'assumption' | 'sensitivity' | 'comparison' | 'drivers' | 'summary' | 'advice';
}

@Component({
  selector: 'app-suggestion-chip',
  standalone: true,
  imports: [CommonModule],
  template: `
    <button
      class="suggestion-chip"
      [class.clicked]="isClicked"
      [attr.data-category]="suggestion.category"
      (click)="onChipClick()"
      [attr.aria-label]="'Ask: ' + suggestion.text">
      <span class="chip-icon">{{ suggestion.icon }}</span>
      <span class="chip-text">{{ suggestion.text }}</span>
    </button>
  `,
  styles: [`
    .suggestion-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 10px 16px;
      border: 1.5px solid var(--border-color, #e2e8f0);
      border-radius: 20px;
      background: white;
      color: var(--text-primary, #1a202c);
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      white-space: nowrap;
      line-height: 1.4;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
      
      &:hover {
        background: var(--primary-color, #3b82f6);
        color: white;
        border-color: var(--primary-color, #3b82f6);
        transform: translateY(-2px);
        box-shadow: 0 4px 12px rgba(59, 130, 246, 0.25);
      }
      
      &:active {
        transform: translateY(0);
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
      }
      
      &:focus-visible {
        outline: 2px solid var(--primary-color, #3b82f6);
        outline-offset: 2px;
      }
      
      &.clicked {
        background: var(--success-color, #10b981);
        color: white;
        border-color: var(--success-color, #10b981);
        animation: chipClick 0.3s ease;
      }
      
      .chip-icon {
        font-size: 16px;
        line-height: 1;
      }
      
      .chip-text {
        font-size: 14px;
      }
    }
    
    @keyframes chipClick {
      0%, 100% { transform: scale(1); }
      50% { transform: scale(0.95); }
    }
    
    /* Dark mode support - must be OUTSIDE the nested SCSS to work with Angular */
    :host-context(html[data-theme="dark"]) .suggestion-chip {
      background: #2a2d3a;
      color: #e5e7eb;
      border-color: #3f4451;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
    }
    
    /* Category-specific hover colors */
    .suggestion-chip[data-category="valuation"]:hover {
      background: #3b82f6;
    }
    
    .suggestion-chip[data-category="scenario"]:hover {
      background: #8b5cf6;
    }
    
    .suggestion-chip[data-category="risk"]:hover {
      background: #ef4444;
    }
    
    .suggestion-chip[data-category="assumption"]:hover {
      background: #f59e0b;
    }
    
    .suggestion-chip[data-category="sensitivity"]:hover {
      background: #14b8a6;
    }
    
    .suggestion-chip[data-category="comparison"]:hover {
      background: #6366f1;
    }
    
    /* Mobile responsive */
    @media (max-width: 768px) {
      .suggestion-chip {
        padding: 8px 14px;
        font-size: 13px;
        
        .chip-icon {
          font-size: 15px;
        }
        
        .chip-text {
          font-size: 13px;
        }
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SuggestionChipComponent {
  @Input() suggestion!: ChatSuggestion;
  @Output() chipClicked = new EventEmitter<ChatSuggestion>();
  
  isClicked = false;
  
  onChipClick(): void {
    this.isClicked = true;
    this.chipClicked.emit(this.suggestion);
    
    // Reset clicked state after animation
    setTimeout(() => {
      this.isClicked = false;
    }, 300);
  }
}

