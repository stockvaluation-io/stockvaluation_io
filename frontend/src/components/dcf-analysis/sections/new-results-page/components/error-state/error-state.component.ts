import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface ErrorStateConfig {
  title: string;
  message: string;
  icon?: string;
  showRetry?: boolean;
  retryText?: string;
  showSupport?: boolean;
}

@Component({
    selector: 'app-error-state',
    imports: [CommonModule],
    template: `
    <div class="error-container" [class]="sizeClass">
      <div class="error-icon">
        <i [class]="config.icon || 'pi pi-exclamation-triangle'" aria-hidden="true"></i>
      </div>
      
      <h3 class="error-title">{{ config.title }}</h3>
      <p class="error-message">{{ config.message }}</p>
      
      <div class="error-actions" *ngIf="config.showRetry || config.showSupport">
        <button 
          type="button"
          class="retry-btn"
          *ngIf="config.showRetry"
          (click)="onRetry()"
          [attr.aria-label]="config.retryText || 'Retry operation'">
          <i class="pi pi-refresh" aria-hidden="true"></i>
          {{ config.retryText || 'Try Again' }}
        </button>
        
        <button 
          type="button"
          class="support-btn"
          *ngIf="config.showSupport"
          (click)="onContactSupport()"
          aria-label="Contact support">
          <i class="pi pi-question-circle" aria-hidden="true"></i>
          Contact Support
        </button>
      </div>
    </div>
  `,
    styleUrls: ['./error-state.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ErrorStateComponent {
  @Input() config!: ErrorStateConfig;
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  
  @Output() retry = new EventEmitter<void>();
  @Output() contactSupport = new EventEmitter<void>();

  get sizeClass(): string {
    return `error-${this.size}`;
  }

  onRetry(): void {
    this.retry.emit();
  }

  onContactSupport(): void {
    this.contactSupport.emit();
  }
}