import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { trigger, state, style, transition, animate } from '@angular/animations';

@Component({
  selector: 'app-disclaimer-banner',
  imports: [CommonModule],
  template: `
    <div 
      class="disclaimer-banner" 
      *ngIf="showBanner"
      [@slideOut]="animationState"
      (@slideOut.done)="onAnimationDone($event)">
      <div class="disclaimer-content">
        <i class="pi pi-info-circle disclaimer-icon"></i>
        <span class="disclaimer-text">
          Educational valuation based on public data and Damodaran-style assumptions. Not financial advice.
        </span>
      </div>
    </div>
  `,
  styleUrls: ['./disclaimer-banner.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    trigger('slideOut', [
      state('visible', style({
        opacity: 1,
        transform: 'translateY(0)',
        height: '*',
        marginBottom: 'var(--space-4)'
      })),
      state('hidden', style({
        opacity: 0,
        transform: 'translateY(-20px)',
        height: 0,
        marginBottom: 0
      })),
      transition('visible => hidden', [
        animate('300ms ease-in-out')
      ])
    ])
  ]
})
export class DisclaimerBannerComponent {
  showBanner = true;
  animationState = 'visible';

  dismissBanner(): void {
    this.animationState = 'hidden';
  }

  onAnimationDone(event: any): void {
    if (event.toState === 'hidden') {
      this.showBanner = false;
    }
  }
}
