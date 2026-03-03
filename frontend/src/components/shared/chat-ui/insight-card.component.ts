import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { MarkdownModule } from 'ngx-markdown';
import { MessageSection } from './message-formatter.service';

@Component({
  selector: 'app-insight-card',
  standalone: true,
  imports: [CommonModule, MarkdownModule],
  template: `
    <div class="insight-card" 
         [class.expanded]="isExpanded"
         [class]="'card-type-' + section.type">
      <div class="card-header" (click)="toggle()">
        <div class="header-left">
          <i [class]="section.icon" class="header-icon"></i>
          <h3 class="header-title">{{ section.title }}</h3>
        </div>
        @if (section.collapsible) {
          <i class="pi expand-icon" 
             [class.pi-chevron-down]="!isExpanded"
             [class.pi-chevron-up]="isExpanded"></i>
        }
      </div>
      
      @if (isExpanded || !section.collapsible) {
        <div class="card-content" [@slideDown]>
          <markdown [data]="section.content" class="section-markdown"></markdown>
        </div>
      }
    </div>
  `,
  styles: [`
    .insight-card {
      margin: 0.75rem 0;
      border-radius: 12px;
      border: 2px solid #e5e7eb;
      overflow: hidden;
      background: white;
      transition: all 0.2s ease;
      
      .chat-ui-container[data-theme="dark"] & {
        background: #1f2937;
        border-color: #374151;
      }
      
      &:hover {
        border-color: #10b981;
        box-shadow: 0 4px 12px rgba(16, 185, 129, 0.1);
      }
      
      &.expanded {
        border-color: #10b981;
        
        .chat-ui-container[data-theme="dark"] & {
          border-color: #10b981;
        }
      }
      
      // Type-specific styling
      &.card-type-math {
        border-left-width: 4px;
        border-left-color: #3b82f6;
        
        .header-icon {
          color: #3b82f6;
        }
      }
      
      &.card-type-question {
        border-left-width: 4px;
        border-left-color: #8b5cf6;
        
        .header-icon {
          color: #8b5cf6;
        }
      }
      
      &.card-type-concern {
        border-left-width: 4px;
        border-left-color: #ef4444;
        
        .header-icon {
          color: #ef4444;
        }
      }
      
      &.card-type-hypothesis {
        border-left-width: 4px;
        border-left-color: #10b981;
        
        .header-icon {
          color: #10b981;
        }
      }
      
      &.card-type-analysis {
        border-left-width: 4px;
        border-left-color: #f59e0b;
        
        .header-icon {
          color: #f59e0b;
        }
      }
    }
    
    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      cursor: pointer;
      user-select: none;
      transition: background 0.2s ease;
      
      &:hover {
        background: rgba(16, 185, 129, 0.05);
        
        .chat-ui-container[data-theme="dark"] & {
          background: rgba(16, 185, 129, 0.1);
        }
      }
      
      .header-left {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        flex: 1;
      }
      
      .header-icon {
        font-size: 1.25rem;
        flex-shrink: 0;
      }
      
      .header-title {
        margin: 0;
        font-size: 1rem;
        font-weight: 700;
        color: #1f2937;
        
        .chat-ui-container[data-theme="dark"] & {
          color: #e5e7eb;
        }
      }
      
      .expand-icon {
        font-size: 0.875rem;
        color: #9ca3af;
        transition: transform 0.2s ease;
        
        .chat-ui-container[data-theme="dark"] & {
          color: #6b7280;
        }
      }
    }
    
    .card-content {
      padding: 0 1.25rem 1.25rem 1.25rem;
      
      .section-markdown {
        font-size: 0.9375rem;
        line-height: 1.7;
        color: #4b5563;
        
        .chat-ui-container[data-theme="dark"] & {
          color: #d1d5db;
        }
        
        ::ng-deep {
          p {
            margin: 0.75rem 0;
            
            &:first-child {
              margin-top: 0;
            }
            
            &:last-child {
              margin-bottom: 0;
            }
          }
          
          strong {
            color: #10b981;
            font-weight: 700;
          }
          
          em {
            font-style: italic;
            color: #6b7280;
          }
          
          ul, ol {
            margin: 0.75rem 0;
            padding-left: 1.5rem;
          }
          
          li {
            margin: 0.5rem 0;
          }
          
          code {
            background: #f3f4f6;
            padding: 0.125rem 0.375rem;
            border-radius: 4px;
            font-family: 'Courier New', monospace;
            font-size: 0.875rem;
            
            .chat-ui-container[data-theme="dark"] & {
              background: #2d2d2d;
            }
          }
        }
      }
    }
    
    @media (max-width: 640px) {
      .insight-card {
        margin: 0.5rem 0;
      }
      
      .card-header {
        padding: 0.75rem 1rem;
        
        .header-title {
          font-size: 0.9375rem;
        }
        
        .header-icon {
          font-size: 1.125rem;
        }
      }
      
      .card-content {
        padding: 0 1rem 1rem 1rem;
        
        .section-markdown {
          font-size: 0.875rem;
        }
      }
    }
  `],
  animations: [
    trigger('slideDown', [
      transition(':enter', [
        style({ height: 0, opacity: 0 }),
        animate('250ms cubic-bezier(0.4, 0, 0.2, 1)', 
                style({ height: '*', opacity: 1 }))
      ]),
      transition(':leave', [
        style({ height: '*', opacity: 1 }),
        animate('200ms cubic-bezier(0.4, 0, 0.2, 1)', 
                style({ height: 0, opacity: 0 }))
      ])
    ])
  ]
})
export class InsightCardComponent {
  @Input() section!: MessageSection;
  
  isExpanded = false;
  
  ngOnInit() {
    // Auto-expand based on section settings
    this.isExpanded = this.section.defaultExpanded ?? !this.section.collapsible;
  }
  
  toggle() {
    if (this.section.collapsible) {
      this.isExpanded = !this.isExpanded;
    }
  }
}

