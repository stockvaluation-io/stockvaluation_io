import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppIconComponent } from '../shared/app-icon/app-icon.component';

@Component({
  selector: 'app-action-button',
  standalone: true,
  imports: [CommonModule, AppIconComponent],
  template: `
    <button 
      [class]="buttonClass"
      [disabled]="isLoading"
      (click)="onClick()">
      <app-icon [icon]="icon" size="sm"></app-icon>
      <span *ngIf="!isLoading">{{ text }}</span>
      <span *ngIf="isLoading" class="loading-content">
        <app-icon icon="loading" size="sm" class="spinner"></app-icon>
        Loading...
      </span>
    </button>
  `,
  styleUrls: ['./not-found.component.scss']
})
export class ActionButtonComponent {
  @Input() variant: 'primary' | 'secondary' | 'tertiary' = 'primary';
  @Input() icon!: string;
  @Input() text!: string;
  @Input() isLoading = false;
  @Output() clicked = new EventEmitter<void>();

  get buttonClass(): string {
    const baseClass = `${this.variant}-btn`;
    const loadingClass = this.isLoading ? 'loading' : '';
    return `${baseClass} ${loadingClass}`.trim();
  }

  onClick(): void {
    if (!this.isLoading) {
      this.clicked.emit();
    }
  }
}