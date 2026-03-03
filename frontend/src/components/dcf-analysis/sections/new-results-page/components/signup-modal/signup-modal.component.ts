import { Component, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
    selector: 'app-signup-modal',
    imports: [CommonModule, FormsModule],
    template: `
    <div class="modal-overlay" (click)="onClose()">
      <div class="modal-container" (click)="$event.stopPropagation()">
        <header class="modal-header">
          <h2 class="modal-title">
            <i class="pi pi-download"></i>
            Download PDF Report
          </h2>
          <button type="button" class="close-btn" (click)="onClose()">
            <i class="pi pi-times"></i>
          </button>
        </header>

        <div class="modal-content">
          <div class="feature-highlight">
            <div class="feature-icon">
              <i class="pi pi-file-pdf"></i>
            </div>
            <h3>Get Your Complete DCF Analysis Report</h3>
            <p>Download a comprehensive PDF report with detailed financial projections, risk assessments, and valuation analysis.</p>
          </div>

          <div class="benefits-list">
            <div class="benefit-item">
              <i class="pi pi-check-circle"></i>
              <span>Professional PDF format ready for presentations</span>
            </div>
            <div class="benefit-item">
              <i class="pi pi-check-circle"></i>
              <span>Detailed financial charts and projections</span>
            </div>
            <div class="benefit-item">
              <i class="pi pi-check-circle"></i>
              <span>Complete risk assessment and sensitivity analysis</span>
            </div>
            <div class="benefit-item">
              <i class="pi pi-check-circle"></i>
              <span>Investment insights and key analysis points</span>
            </div>
          </div>

          <div class="signup-form">
            <h4>Sign up for free to download</h4>
            <form (ngSubmit)="onSubmit()">
              <div class="form-group">
                <input
                  type="email"
                  class="form-input"
                  placeholder="Enter your email address"
                  [(ngModel)]="email"
                  name="email"
                  required
                  [class.error]="hasError && !isValidEmail()">
                <div class="error-message" *ngIf="hasError && !isValidEmail()">
                  Please enter a valid email address
                </div>
              </div>
              
              <div class="form-group">
                <input
                  type="text"
                  class="form-input"
                  placeholder="Your name (optional)"
                  [(ngModel)]="name"
                  name="name">
              </div>

              <div class="form-actions">
                <button 
                  type="submit" 
                  class="signup-btn"
                  [disabled]="isSubmitting">
                  <i class="pi" [class]="isSubmitting ? 'pi-spin pi-spinner' : 'pi-download'"></i>
                  {{ isSubmitting ? 'Creating Account...' : 'Sign Up & Download' }}
                </button>
              </div>
            </form>
          </div>

          <div class="footer-note">
            <p><i class="pi pi-info-circle"></i> By signing up, you'll also get access to save analyses, create custom reports, and receive market insights.</p>
          </div>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./signup-modal.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class SignupModalComponent {
  @Output() close = new EventEmitter<void>();
  @Output() signup = new EventEmitter<{ email: string; name?: string }>();

  email = '';
  name = '';
  isSubmitting = false;
  hasError = false;

  onClose(): void {
    this.close.emit();
  }

  onSubmit(): void {
    this.hasError = false;
    
    if (!this.isValidEmail()) {
      this.hasError = true;
      return;
    }

    this.isSubmitting = true;
    
    // Simulate API call
    setTimeout(() => {
      this.signup.emit({
        email: this.email,
        name: this.name || undefined
      });
      this.isSubmitting = false;
    }, 1000);
  }

  isValidEmail(): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(this.email);
  }
}