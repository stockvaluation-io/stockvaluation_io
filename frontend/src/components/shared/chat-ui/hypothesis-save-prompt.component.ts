import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';

@Component({
  selector: 'app-hypothesis-save-prompt',
  standalone: true,
  imports: [CommonModule, ButtonModule, DialogModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [style]="{ width: '90vw', maxWidth: '500px' }"
      [closable]="true"
      [draggable]="false"
      [resizable]="false"
      (onHide)="onClose()"
      [styleClass]="'hypothesis-save-prompt-dialog'"
      [blockScroll]="true">
      <ng-template pTemplate="header">
        <div class="dialog-header">
          <div class="header-icon">
            <i class="pi pi-save"></i>
          </div>
          <div class="header-content">
            <h3 class="dialog-title">Save Investment Thesis?</h3>
          </div>
        </div>
      </ng-template>
      
      <div class="dialog-content">
        <p class="prompt-text">
          Would you like to save this investment thesis to your history so you can track how your thinking evolves over time.
        </p>
      </div>
      
      <ng-template pTemplate="footer">
        <div class="dialog-footer">
          <p-button
            label="Not Now"
            icon="pi pi-times"
            [outlined]="true"
            severity="secondary"
            (onClick)="onCancel()"
            [disabled]="isProcessing">
          </p-button>
          <p-button
            label="Save Thesis"
            icon="pi pi-check-circle"
            severity="success"
            (onClick)="onSave()"
            [disabled]="isProcessing"
            [loading]="isProcessing">
          </p-button>
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep .hypothesis-save-prompt-dialog {
      .p-dialog {
        background: var(--color-bg-card);
        border-radius: 16px;
        box-shadow: 0 20px 25px -5px var(--color-bg-overlay), 0 10px 10px -5px var(--color-bg-overlay);
        border: 1px solid var(--color-border-primary);
        color: var(--color-text-primary);
      }
      
      .p-dialog-header {
        padding: var(--space-8);
        border-bottom: 1px solid var(--color-border-primary);
        background: var(--color-bg-card);
        border-radius: 16px 16px 0 0;
      }
      
      .p-dialog-content {
        padding: var(--space-8);
        background: var(--color-bg-card);
      }
      
      .p-dialog-footer {
        padding: var(--space-6) var(--space-8);
        border-top: 1px solid var(--color-border-primary);
        background: var(--color-bg-secondary);
        border-radius: 0 0 16px 16px;
      }
    }
    
    .dialog-header {
      display: flex;
      align-items: center;
      gap: var(--space-4);
    }
    
    .header-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-text-inverse);
      font-size: 24px;
      box-shadow: 0 4px 12px var(--color-primary-alpha);
    }
    
    .header-content {
      flex: 1;
    }
    
    .dialog-title {
      margin: 0;
      font-size: var(--font-size-2xl);
      font-weight: var(--font-weight-semibold);
      color: var(--color-text-primary);
      font-family: var(--font-primary);
    }
    
    .dialog-content {
      padding: 0;
    }
    
    .prompt-text {
      margin: 0;
      font-size: var(--font-size-base);
      line-height: var(--leading-relaxed);
      color: var(--color-text-primary);
      font-family: var(--font-primary);
      white-space: normal;
      word-wrap: break-word;
    }
    
    .dialog-footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-3);
    }
    
    /* Enhanced button styling for better visibility */
    :host ::ng-deep .hypothesis-save-prompt-dialog {
      .p-button {
        font-weight: var(--font-weight-semibold);
        border-radius: 8px;
        padding: var(--space-3) var(--space-6);
        transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        font-size: var(--font-size-sm);
        min-height: 44px;
        font-family: var(--font-primary);
      }
      
      .p-button.p-button-success {
        background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%) !important;
        border: none !important;
        color: var(--color-text-inverse) !important;
        box-shadow: 0 2px 8px var(--color-primary-alpha) !important;
        
        &:hover:not(:disabled) {
          background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%) !important;
          box-shadow: 0 6px 16px var(--color-primary-alpha) !important;
          transform: translateY(-1px);
        }
        
        &:active:not(:disabled) {
          background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%) !important;
          transform: translateY(0);
        }
        
        &:focus {
          box-shadow: 0 0 0 3px var(--color-primary-alpha) !important;
        }
        
        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
      }
      
      .p-button.p-button-outlined.p-button-secondary {
        background: var(--color-bg-card);
        color: var(--color-text-secondary) !important;
        border: 2px solid var(--color-border-primary) !important;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
        
        &:hover:not(:disabled) {
          background: var(--color-bg-hover) !important;
          color: var(--color-text-primary) !important;
          border-color: var(--color-border-primary) !important;
          transform: translateY(-1px);
          box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        }
        
        &:active:not(:disabled) {
          transform: translateY(0);
        }
      }
    }
    
    /* Tablet responsive */
    @media (max-width: 1024px) {
      :host ::ng-deep .hypothesis-save-prompt-dialog {
        .p-dialog {
          width: 90vw !important;
          max-width: 450px !important;
        }
      }
    }
    
    /* Mobile responsive */
    @media (max-width: 768px) {
      :host ::ng-deep .hypothesis-save-prompt-dialog {
        .p-dialog {
          width: 95vw !important;
          max-width: none !important;
          margin: var(--space-2);
          max-height: 90vh !important;
        }
        
        .p-dialog-header {
          padding: var(--space-4);
        }
        
        .p-dialog-content {
          padding: var(--space-4);
        }
        
        .p-dialog-footer {
          padding: var(--space-3) var(--space-4);
        }
      }
      
      .header-icon {
        width: 44px;
        height: 44px;
        font-size: 22px;
      }
      
      .dialog-title {
        font-size: var(--font-size-xl);
      }
      
      .prompt-text {
        font-size: var(--font-size-sm);
        line-height: var(--leading-normal);
      }
      
      .dialog-footer {
        flex-direction: column-reverse;
        gap: var(--space-2);
        
        ::ng-deep button {
          width: 100%;
          min-height: 48px;
          justify-content: center;
          font-size: var(--font-size-base);
        }
      }
    }
    
    /* Small mobile devices */
    @media (max-width: 480px) {
      :host ::ng-deep .hypothesis-save-prompt-dialog {
        .p-dialog {
          width: 100vw !important;
          margin: 0;
          border-radius: 16px 16px 0 0 !important;
          position: fixed !important;
          bottom: 0 !important;
          top: auto !important;
          left: 0 !important;
          right: 0 !important;
          transform: none !important;
          border: none !important;
          max-height: 85vh !important;
        }
        
        .p-dialog-header {
          padding: var(--space-4);
          border-radius: 16px 16px 0 0 !important;
        }
        
        .p-dialog-content {
          padding: var(--space-3) var(--space-4);
        }
        
        .p-dialog-footer {
          padding: var(--space-3);
          border-radius: 0 !important;
        }
      }
      
      .header-icon {
        width: 40px;
        height: 40px;
        font-size: 20px;
      }
      
      .dialog-title {
        font-size: var(--font-size-lg);
      }
      
      .prompt-text {
        font-size: var(--font-size-sm);
        line-height: var(--leading-normal);
      }
    }
    
    /* Touch device optimizations */
    @media (hover: none) and (pointer: coarse) {
      :host ::ng-deep .hypothesis-save-prompt-dialog {
        .p-button {
          min-height: 48px;
          
          &:active {
            transform: scale(0.98);
          }
        }
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HypothesisSavePromptComponent implements OnInit, OnDestroy {
  @Input() visible = false;
  @Input() hypothesisId: string | null = null;
  @Output() saved = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  isProcessing = false;

  ngOnInit(): void {
    // Component initialization
  }

  ngOnDestroy(): void {
    // Cleanup if needed
  }

  onSave(): void {
    if (!this.hypothesisId) return;
    this.isProcessing = true;
    this.saved.emit(this.hypothesisId);
    setTimeout(() => {
      this.isProcessing = false;
      this.visible = false;
    }, 500);
  }

  onCancel(): void {
    this.cancelled.emit();
    this.visible = false;
  }

  onClose(): void {
    this.closed.emit();
  }
}

