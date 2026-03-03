import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'app-loading-spinner',
    imports: [CommonModule],
    template: `
    <div class="loading-container" [class]="sizeClass">
      <div class="spinner" [class]="spinnerClass">
        <div class="spinner-circle"></div>
        <div class="spinner-circle"></div>
        <div class="spinner-circle"></div>
      </div>
      <p class="loading-text" *ngIf="text">{{ text }}</p>
    </div>
  `,
    styleUrls: ['./loading-spinner.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class LoadingSpinnerComponent {
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  @Input() text?: string;
  @Input() variant: 'primary' | 'light' = 'primary';

  get sizeClass(): string {
    return `loading-${this.size}`;
  }

  get spinnerClass(): string {
    return `spinner-${this.variant}`;
  }
}