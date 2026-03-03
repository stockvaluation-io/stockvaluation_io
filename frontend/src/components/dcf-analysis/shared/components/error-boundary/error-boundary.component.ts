import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ErrorInfo {
  title: string;
  message: string;
  code?: string;
  recoverable: boolean;
  suggestions?: string[];
}

@Component({
    selector: 'app-error-boundary',
    imports: [CommonModule],
    template: `
    <div class="error-boundary" [class]="'error-' + errorType">
      <div class="error-content">
        <div class="error-icon">
          <i [class]="getErrorIcon()" aria-hidden="true"></i>
        </div>
        
        <div class="error-details">
          <h3 class="error-title">{{ error.title }}</h3>
          <p class="error-message">{{ error.message }}</p>
          
          <div class="error-code" *ngIf="error.code">
            <span class="code-label">Error Code</span>
            <code class="code-value">{{ error.code }}</code>
          </div>
          
          <div class="error-suggestions" *ngIf="error.suggestions?.length">
            <h4 class="suggestions-title">What you can try:</h4>
            <ul class="suggestions-list">
              <li *ngFor="let suggestion of error.suggestions">{{ suggestion }}</li>
            </ul>
          </div>
        </div>
      </div>
      
      <div class="error-actions">
        <button 
          class="retry-button"
          type="button"
          (click)="onRetry()"
          *ngIf="error.recoverable"
          [attr.aria-label]="'Retry the operation'"
        >
          <i class="pi pi-refresh" aria-hidden="true"></i>
          Try Again
        </button>
        
        <button 
          class="home-button"
          type="button"
          (click)="onGoHome()"
          [attr.aria-label]="'Return to the main page'"
        >
          <i class="pi pi-home" aria-hidden="true"></i>
          Start Over
        </button>
        
        <button 
          class="support-button"
          type="button"
          (click)="onContactSupport()"
          *ngIf="!error.recoverable || errorType === 'critical'"
          [attr.aria-label]="'Contact support for help'"
        >
          <i class="pi pi-envelope" aria-hidden="true"></i>
          Contact Support
        </button>
      </div>
    </div>
  `,
    styleUrls: ['./error-boundary.component.scss']
})
export class ErrorBoundaryComponent {
  @Input() error!: ErrorInfo;
  @Input() errorType: 'warning' | 'error' | 'critical' = 'error';
  
  @Output() retryClicked = new EventEmitter<void>();
  @Output() homeClicked = new EventEmitter<void>();
  @Output() supportClicked = new EventEmitter<void>();

  onRetry(): void {
    this.retryClicked.emit();
  }

  onGoHome(): void {
    this.homeClicked.emit();
  }

  onContactSupport(): void {
    this.supportClicked.emit();
  }

  getErrorIcon(): string {
    switch (this.errorType) {
      case 'warning':
        return 'pi pi-exclamation-triangle';
      case 'critical':
        return 'pi pi-times-circle';
      default:
        return 'pi pi-exclamation-circle';
    }
  }
}