import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface EmptyStateConfig {
  title: string;
  message: string;
  icon?: string;
  actionText?: string;
  showAction?: boolean;
}

@Component({
    selector: 'app-empty-state',
    imports: [CommonModule],
    template: `
    <div class="empty-container" [class]="sizeClass">
      <div class="empty-icon">
        <i [class]="config.icon || 'pi pi-inbox'" aria-hidden="true"></i>
      </div>
      
      <h3 class="empty-title">{{ config.title }}</h3>
      <p class="empty-message">{{ config.message }}</p>
      
      <button 
        type="button"
        class="action-btn"
        *ngIf="config.showAction"
        (click)="onAction()"
        [attr.aria-label]="config.actionText || 'Take action'">
        <i class="pi pi-plus" aria-hidden="true"></i>
        {{ config.actionText || 'Get Started' }}
      </button>
    </div>
  `,
    styleUrls: ['./empty-state.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class EmptyStateComponent {
  @Input() config!: EmptyStateConfig;
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  
  @Output() action = new EventEmitter<void>();

  get sizeClass(): string {
    return `empty-${this.size}`;
  }

  onAction(): void {
    this.action.emit();
  }
}