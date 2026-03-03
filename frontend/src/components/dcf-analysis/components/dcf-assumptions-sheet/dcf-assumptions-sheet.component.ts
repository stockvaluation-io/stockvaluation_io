import { 
  Component, 
  Input, 
  Output, 
  EventEmitter, 
  OnInit, 
  OnChanges,
  OnDestroy, 
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  HostListener
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { 
  DCFAssumptionsFormData, 
  CoreDCFAssumptions,
  RiskAssessment,
  AdvancedOverrides,
  FormValidationResult,
  CompanyData,
  ValuationResults
} from '../../models';
import { DCFAssumptionsPersistenceService } from '../../services';
import { LoggerService } from '../../../../core/services';
import { DCFFacadeService } from '../../../../core/services/dcf/dcf-facade.service';

@Component({
    selector: 'app-dcf-assumptions-sheet',
    imports: [
        CommonModule,
        FormsModule
    ],
    template: `
    <!-- Sheet Container -->
    <div 
      class="sheet-container"
      [class.slide-in]="isOpen"
      [class.slide-out]="!isOpen"
      role="dialog"
      [attr.aria-label]="'DCF Assumptions Form'"
      [attr.aria-modal]="isOpen">
      
      <!-- Sheet Header -->
      <div class="sheet-header">
        <div class="header-content">
          <div class="header-text">
            <h2 class="sheet-title">Refine your estimates</h2>
          </div>
          <button 
            class="close-btn"
            (click)="close()"
            [attr.aria-label]="'Close assumptions form'"
            type="button">
            <i class="pi pi-times" aria-hidden="true"></i>
          </button>
        </div>
      </div>

      <!-- Sheet Content -->
      <div class="sheet-content" #sheetContent>
        
        <!-- Calculation Overlay for Form -->
        <div *ngIf="isCalculating" class="form-calculation-overlay">
          <div class="overlay-spinner">
            <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
            <p>Processing your assumptions...</p>
          </div>
        </div>
        
        <!-- Loading State -->
        <div *ngIf="isLoading" class="loading-state">
          <div class="loading-spinner">
            <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
          </div>
          <p>Loading assumptions...</p>
        </div>

        <!-- Error State -->
        <div *ngIf="hasError && !isLoading" class="error-state">
          <div class="error-icon">
            <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          </div>
          <h3>{{ isApiError ? 'API Request Failed' : 'Unable to Load Assumptions' }}</h3>
          <p>{{ errorMessage || 'Please try again later.' }}</p>
          <div class="error-actions">
            <button 
              *ngIf="canRetry" 
              class="btn-secondary" 
              (click)="retryLoad()" 
              type="button">
              <i class="pi pi-refresh" aria-hidden="true"></i>
              Retry
            </button>
            <button 
              class="btn-tertiary" 
              (click)="close()" 
              type="button">
              <i class="pi pi-times" aria-hidden="true"></i>
              Close
            </button>
          </div>
        </div>

        <!-- Form Content -->
        <div *ngIf="!isLoading && !hasError && formData" class="form-content">
          
          <!-- Core DCF Assumptions -->
          <div class="form-section">
            <div class="section-content">
              <!-- Revenue Growth -->
              <div class="input-group">
                <label class="input-label">
                  Revenue growth rate for next year
                  <span class="tooltip-trigger" [title]="'Default is the analyst projection for the next period.'">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                  </span>
                </label>
                <div class="input-with-slider">
                  <input 
                    type="number" 
                    class="form-input"
                    [(ngModel)]="formData.coreAssumptions.revenueNextYear"
                    (ngModelChange)="onFormChange()"
                    [min]="0" 
                    [max]="100"
                    step="0.1"
                    placeholder="0.0"
                    [disabled]="isCalculating">
                  <span class="input-suffix">%</span>
                </div>
                <input 
                  type="range" 
                  class="form-slider"
                  [(ngModel)]="formData.coreAssumptions.revenueNextYear"
                  (ngModelChange)="onFormChange()"
                  min="0" 
                  max="100"
                  step="0.1"
                  [disabled]="isCalculating">
              </div>

              <!-- Operating Margin -->
              <div class="input-group">
                <label class="input-label">
                  Operating Margin for next year
                  <span class="tooltip-trigger" [title]="'Default value assumes your company maintains the current year\\'s margin.'">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                  </span>
                </label>
                <div class="input-with-slider">
                  <input 
                    type="number" 
                    class="form-input"
                    [(ngModel)]="formData.coreAssumptions.operatingMarginNextYear"
                    (ngModelChange)="onFormChange()"
                    [min]="0" 
                    [max]="99"
                    step="0.1"
                    placeholder="0.0"
                    [disabled]="isCalculating">
                  <span class="input-suffix">%</span>
                </div>
                <input 
                  type="range" 
                  class="form-slider"
                  [(ngModel)]="formData.coreAssumptions.operatingMarginNextYear"
                  (ngModelChange)="onFormChange()"
                  min="0" 
                  max="99"
                  step="0.1"
                  [disabled]="isCalculating">
              </div>

              <!-- Growth Years 2-5 -->
              <div class="input-group">
                <label class="input-label">
                  Compounded annual revenue growth rate - years 2-5
                  <span class="tooltip-trigger" [title]="'Uses next year\\'s revenue projection as the default value.'">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                  </span>
                </label>
                <div class="input-with-slider">
                  <input 
                    type="number" 
                    class="form-input"
                    [(ngModel)]="formData.coreAssumptions.compoundAnnualGrowth2_5"
                    (ngModelChange)="onFormChange()"
                    [min]="0" 
                    [max]="100"
                    step="0.1"
                    placeholder="0.0"
                    [disabled]="isCalculating">
                  <span class="input-suffix">%</span>
                </div>
                <input 
                  type="range" 
                  class="form-slider"
                  [(ngModel)]="formData.coreAssumptions.compoundAnnualGrowth2_5"
                  (ngModelChange)="onFormChange()"
                  min="0" 
                  max="100"
                  step="0.1"
                  [disabled]="isCalculating">
              </div>

              <!-- Target Operating Margin -->
              <div class="input-group">
                <label class="input-label">
                  Target pre-tax operating margin
                  <span class="tooltip-trigger" [title]="'Default is the industry average; adjust if your company operates differently.'">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                  </span>
                </label>
                <div class="input-with-slider">
                  <input 
                    type="number" 
                    class="form-input"
                    [(ngModel)]="formData.coreAssumptions.targetPreTaxOperatingMargin"
                    (ngModelChange)="onFormChange()"
                    [min]="0" 
                    [max]="99"
                    step="0.1"
                    placeholder="0.0"
                    [disabled]="isCalculating">
                  <span class="input-suffix">%</span>
                </div>
                <input 
                  type="range" 
                  class="form-slider"
                  [(ngModel)]="formData.coreAssumptions.targetPreTaxOperatingMargin"
                  (ngModelChange)="onFormChange()"
                  min="0" 
                  max="99"
                  step="0.1"
                  [disabled]="isCalculating">
              </div>

              <!-- Sales to Capital Years 1-5 -->
              <div class="input-group">
                <label class="input-label">
                  Sales to capital ratio (for years 1-5)
                  <span class="tooltip-trigger" [title]="'For every dollar spent, this value shows revenue earned. Default is 90th decile value for the industry.'">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                  </span>
                </label>
                <div class="input-with-slider">
                  <input 
                    type="number" 
                    class="form-input"
                    [(ngModel)]="formData.coreAssumptions.salesToCapitalYears1To5"
                    (ngModelChange)="onFormChange()"
                    [min]="0" 
                    [max]="10"
                    step="0.1"
                    placeholder="0.0"
                    [disabled]="isCalculating">
                </div>
                <input 
                  type="range" 
                  class="form-slider"
                  [(ngModel)]="formData.coreAssumptions.salesToCapitalYears1To5"
                  (ngModelChange)="onFormChange()"
                  min="0" 
                  max="10"
                  step="0.1"
                  [disabled]="isCalculating">
              </div>

              <!-- Sales to Capital Years 6-10 -->
              <div class="input-group">
                <label class="input-label">
                  Sales to capital ratio (for years 6-10)
                  <span class="tooltip-trigger" [title]="'Default matches the average value for the industry.'">
                    <i class="pi pi-info-circle" aria-hidden="true"></i>
                  </span>
                </label>
                <div class="input-with-slider">
                  <input 
                    type="number" 
                    class="form-input"
                    [(ngModel)]="formData.coreAssumptions.salesToCapitalYears6To10"
                    (ngModelChange)="onFormChange()"
                    [min]="0" 
                    [max]="10"
                    step="0.1"
                    placeholder="0.0"
                    [disabled]="isCalculating">
                </div>
                <input 
                  type="range" 
                  class="form-slider"
                  [(ngModel)]="formData.coreAssumptions.salesToCapitalYears6To10"
                  (ngModelChange)="onFormChange()"
                  min="0" 
                  max="10"
                  step="0.1"
                  [disabled]="isCalculating">
              </div>

              <!-- Read-only fields -->
              <div class="readonly-fields">
                <div class="readonly-field">
                  <label class="input-label">Riskfree rate</label>
                  <div class="readonly-value">
                    {{ formData.coreAssumptions.riskFreeRate | number:'1.2-2' }}%
                  </div>
                </div>
                <div class="readonly-field">
                  <label class="input-label">Initial cost of capital</label>
                  <div class="readonly-value">
                    {{ formData.coreAssumptions.initialCostCapital | number:'1.2-2' }}%
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Risk Assessment (simplified) -->
          <div class="form-section">
            <div class="section-content">
              
              <!-- R&D Capitalization Question -->
              <div class="input-group">
                <label class="input-label">Do you have R & D expenses to capitalize?</label>
                <div class="radio-group-horizontal">
                  <label class="radio-label">
                    <input 
                      type="radio" 
                      name="rdExpenses"
                      class="form-radio"
                      [value]="true"
                      [(ngModel)]="formData.riskAssessment.isExpensesCapitalize"
                      (ngModelChange)="onFormChange()"
                      [disabled]="isCalculating">
                    <span class="radio-text">Yes</span>
                  </label>
                  <label class="radio-label">
                    <input 
                      type="radio" 
                      name="rdExpenses"
                      class="form-radio"
                      [value]="false"
                      [(ngModel)]="formData.riskAssessment.isExpensesCapitalize"
                      (ngModelChange)="onFormChange()"
                      [disabled]="isCalculating">
                    <span class="radio-text">No</span>
                  </label>
                </div>
              </div>

              <!-- Company Risk Level -->
              <div class="input-group">
                <label class="input-label">How risky is your company?</label>
                <div class="radio-group-horizontal">
                  <label class="radio-label" *ngFor="let level of displayRiskLevels">
                    <input 
                      type="radio" 
                      name="companyRiskLevel"
                      class="form-radio"
                      [value]="level.value"
                      [(ngModel)]="formData.riskAssessment.companyRiskLevel"
                      (ngModelChange)="onFormChange()"
                      [disabled]="isCalculating">
                    <span class="radio-text">{{ level.label }}</span>
                  </label>
                </div>
              </div>

            </div>
          </div>

        </div>
      </div>

      <!-- Sheet Footer -->
      <div class="sheet-footer" *ngIf="!isLoading && !hasError">
        <div class="footer-content">
          <button 
            class="btn-secondary" 
            (click)="resetToDefaults()"
            [disabled]="isSubmitting || isCalculating"
            type="button">
            <i class="pi pi-refresh" aria-hidden="true"></i>
            Reset
          </button>
          
          <div class="primary-actions">
            <button 
              class="btn-primary" 
              [class.btn-cancel]="isSubmitting || isCalculating"
              (click)="(isSubmitting || isCalculating) ? cancelRequest() : submitForm()"
              [disabled]="(!isFormValid && !isSubmitting && !isCalculating)"
              type="button">
              <i class="pi pi-calculator" aria-hidden="true" *ngIf="!isSubmitting && !isCalculating"></i>
              <i class="pi pi-times" aria-hidden="true" *ngIf="isSubmitting || isCalculating"></i>
              <div class="button-text">
                <span class="primary-text">{{ (isSubmitting || isCalculating) ? 'Cancel' : 'Calculate' }}</span>
                <span class="secondary-text" *ngIf="!isSubmitting && !isCalculating">with my assumptions</span>
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./dcf-assumptions-sheet.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class DCFAssumptionsSheetComponent implements OnInit, OnChanges, OnDestroy {
  @Input() isOpen = false;
  @Input() company: CompanyData | null = null;
  @Input() results: ValuationResults | null = null;
  @Input() companySymbol: string = '';
  @Input() isCalculating = false;
  
  @Output() closed = new EventEmitter<void>();
  @Output() formSubmitted = new EventEmitter<DCFAssumptionsFormData>();
  @Output() formSaved = new EventEmitter<DCFAssumptionsFormData>();
  @Output() requestCancelled = new EventEmitter<void>();

  formData: DCFAssumptionsFormData | null = null;
  originalFormData: DCFAssumptionsFormData | null = null;
  
  isLoading = false;
  hasError = false;
  errorMessage = '';
  isSubmitting = false;
  isFormValid = true;
  canRetry = false;
  isApiError = false;
  
  private destroy$ = new Subject<void>();

  // Risk level options for radio buttons (full list for API compatibility)
  riskLevels = [
    {
      value: 'Very Low' as const,
      label: 'Very Low Risk',
      description: 'Extremely stable companies with predictable cash flows'
    },
    {
      value: 'Low' as const,
      label: 'Low Risk',
      description: 'Established companies with stable cash flows and predictable business models'
    },
    {
      value: 'Medium' as const,
      label: 'Medium Risk',
      description: 'Companies with moderate business risk and reasonable growth prospects'
    },
    {
      value: 'Very Medium' as const,
      label: 'Very Medium Risk',
      description: 'Companies with above-average business risk'
    },
    {
      value: 'High' as const,
      label: 'High Risk',
      description: 'Early-stage companies, cyclical industries, or significant regulatory uncertainty'
    },
    {
      value: 'Very High' as const,
      label: 'Very High Risk',
      description: 'Extremely risky companies with high uncertainty'
    }
  ];

  // Simplified risk levels for display (matching the images)
  displayRiskLevels = [
    { value: 'Low' as const, label: 'Low' },
    { value: 'Very Low' as const, label: 'Very Low' },
    { value: 'High' as const, label: 'High' },
    { value: 'Very High' as const, label: 'Very High' },
    { value: 'Medium' as const, label: 'Medium' },
    { value: 'Very Medium' as const, label: 'Very Medium' }
  ];

  // Accordion states for advanced overrides
  accordionStates = {
    costCapital: false,
    returnOnCapital: false,
    probabilityOfFailure: false,
    reinvestmentLag: false,
    taxRate: false,
    nol: false,
    riskFreeRate: false,
    growthRate: false,
    cashPosition: false
  };

  constructor(
    private cdr: ChangeDetectorRef,
    private persistenceService: DCFAssumptionsPersistenceService,
    private logger: LoggerService,
    private dcfFacade: DCFFacadeService
  ) {}

  ngOnInit(): void {
    this.loadFormData();
  }

  ngOnChanges(): void {
    if (this.isOpen && !this.formData) {
      this.loadFormData();
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  @HostListener('document:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (event.key === 'Escape' && this.isOpen) {
      this.close();
    }
  }


  close(): void {
    // Always allow closing, but handle ongoing requests
    if (this.isSubmitting) {
      this.cancelRequest();
    }
    this.resetErrorState();
    this.closed.emit();
  }

  cancelRequest(): void {
    if (this.isSubmitting) {
      this.isSubmitting = false;
      this.isApiError = false;
      this.requestCancelled.emit();
      this.cdr.markForCheck();
    }
  }

  resetErrorState(): void {
    this.hasError = false;
    this.errorMessage = '';
    this.isApiError = false;
    this.canRetry = false;
    this.cdr.markForCheck();
  }

  toggleAccordion(section: keyof typeof this.accordionStates): void {
    this.accordionStates[section] = !this.accordionStates[section];
    this.cdr.markForCheck();
  }

  async loadFormData(): Promise<void> {
    if (!this.companySymbol) {
      return;
    }

    this.isLoading = true;
    this.hasError = false;
    this.cdr.markForCheck();

    try {
      // Always use fresh defaults when form is opened
      // This ensures we get the latest financial data and prevents cross-company contamination
      const fullCompanyApiData = this.dcfFacade.getCompanyApiData();
      this.formData = this.persistenceService.getDefaultFormData(fullCompanyApiData);
      
      // Note: We removed the caching logic here to ensure users always start with
      // current financial data. Form changes are still saved when users modify values.
      
      // Store original for reset functionality
      this.originalFormData = JSON.parse(JSON.stringify(this.formData));
      
      this.isLoading = false;
      this.cdr.markForCheck();
    } catch (error) {
      this.logger.error('Failed to load form data', error, 'DCFAssumptionsSheetComponent');
      this.hasError = true;
      this.errorMessage = 'Failed to load assumptions data';
      this.canRetry = true;
      this.isLoading = false;
      this.cdr.markForCheck();
    }
  }

  retryLoad(): void {
    this.resetErrorState();
    this.loadFormData();
  }

  onFormChange(): void {
    // Save form data to session storage on every change
    if (this.formData && this.companySymbol) {
      this.persistenceService.saveFormData(this.companySymbol, this.formData);
    }
    
    // Validate form
    this.validateForm();
    this.cdr.markForCheck();
  }

  validateForm(): void {
    if (!this.formData) {
      this.isFormValid = false;
      return;
    }

    // Basic validation - ensure all core assumptions are within valid ranges
    const core = this.formData.coreAssumptions;
    this.isFormValid = 
      core.revenueNextYear >= 0 && core.revenueNextYear <= 100 &&
      core.operatingMarginNextYear >= 0 && core.operatingMarginNextYear <= 99 &&
      core.compoundAnnualGrowth2_5 >= 0 && core.compoundAnnualGrowth2_5 <= 100 &&
      core.targetPreTaxOperatingMargin >= 0 && core.targetPreTaxOperatingMargin <= 99 &&
      core.salesToCapitalYears1To5 >= 0 && core.salesToCapitalYears1To5 <= 10 &&
      core.salesToCapitalYears6To10 >= 0 && core.salesToCapitalYears6To10 <= 10;
  }

  resetToDefaults(): void {
    if (this.originalFormData) {
      this.formData = JSON.parse(JSON.stringify(this.originalFormData));
      this.onFormChange();
    }
  }


  submitForm(): void {
    if (!this.formData || !this.isFormValid || this.isSubmitting) {
      return;
    }

    this.isSubmitting = true;
    this.cdr.markForCheck();

    // Save final form data
    this.persistenceService.saveFormData(this.companySymbol, this.formData);
    
    // Emit form submission
    this.formSubmitted.emit(this.formData);
    
    // Note: isSubmitting will be reset when parent handles the submission
    // Set a timeout to handle cases where parent doesn't respond
    setTimeout(() => {
      if (this.isSubmitting) {
        this.handleSubmissionError('Request timed out. Please try again.');
      }
    }, 30000); // 30 second timeout
  }

  handleSubmissionError(errorMessage: string): void {
    this.isSubmitting = false;
    this.hasError = true;
    this.isApiError = true;
    this.canRetry = true;
    this.errorMessage = errorMessage;
    this.cdr.markForCheck();
  }

  handleSubmissionSuccess(): void {
    this.isSubmitting = false;
    this.resetErrorState();
    this.cdr.markForCheck();
  }

  // Method to be called by parent component after submission is complete
  onSubmissionComplete(success: boolean, error?: string): void {
    if (success) {
      this.handleSubmissionSuccess();
    } else {
      this.handleSubmissionError(error || 'Calculation failed. Please try again.');
    }
  }
}