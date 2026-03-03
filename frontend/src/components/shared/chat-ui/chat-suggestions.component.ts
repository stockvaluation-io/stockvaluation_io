import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SuggestionChipComponent, ChatSuggestion } from './suggestion-chip.component';

@Component({
  selector: 'app-chat-suggestions',
  standalone: true,
  imports: [CommonModule, SuggestionChipComponent],
  template: `
    <div class="chat-suggestions" *ngIf="suggestions && suggestions.length > 0">
      <div class="suggestions-header" *ngIf="showHeader">
        <span class="header-icon">💬</span>
        <span class="header-text">{{ headerText }}</span>
      </div>
      
      <div class="suggestions-grid">
        <app-suggestion-chip
          *ngFor="let suggestion of suggestions"
          [suggestion]="suggestion"
          (chipClicked)="onSuggestionClick($event)">
        </app-suggestion-chip>
      </div>
      
      <div class="suggestions-footer" *ngIf="showFooter">
        <span class="footer-text">{{ footerText }}</span>
      </div>
    </div>
  `,
  styles: [`
    .chat-suggestions {
      width: 100%;
      padding: 16px;
      background: var(--surface-color, #f8fafc);
      border-radius: 12px;
      border: 1px solid var(--border-color, #e2e8f0);
    }
    
    .suggestions-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;
      
      .header-icon {
        font-size: 18px;
      }
      
      .header-text {
        font-size: 15px;
        font-weight: 600;
        color: var(--text-primary, #1a202c);
      }
    }
    
    .suggestions-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 12px;
    }
    
    .suggestions-footer {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid var(--border-color, #e2e8f0);
      
      .footer-text {
        font-size: 13px;
        color: var(--text-secondary, #64748b);
        font-style: italic;
      }
    }
    
    /* Dark mode support */
    :host-context(html[data-theme="dark"]) .chat-suggestions {
      background: #1e2028;
      border-color: #3f4451;
    }
    
    :host-context(html[data-theme="dark"]) .suggestions-header .header-text {
      color: #e5e7eb;
    }
    
    :host-context(html[data-theme="dark"]) .suggestions-footer {
      border-top-color: #3f4451;
    }
    
    :host-context(html[data-theme="dark"]) .suggestions-footer .footer-text {
      color: #9ca3af;
    }
    
    /* Mobile responsive */
    @media (max-width: 768px) {
      .chat-suggestions {
        padding: 12px;
      }
      
      .suggestions-grid {
        gap: 6px;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChatSuggestionsComponent {
  @Input() suggestions: ChatSuggestion[] = [];
  @Input() showHeader = true;
  @Input() headerText = 'Quick questions';
  @Input() showFooter = true;
  @Input() footerText = 'Or ask your own question below...';
  
  @Output() suggestionSelected = new EventEmitter<ChatSuggestion>();
  
  onSuggestionClick(suggestion: ChatSuggestion): void {
    this.suggestionSelected.emit(suggestion);
  }
}

