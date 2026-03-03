import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

export interface ValuationUpdateRequest {
  update_id: string;
  intent_summary: string;
  parameter_updates: Record<string, number>;
  assumptions_made: string[];
}

@Component({
  selector: 'app-valuation-update-approval-dialog',
  standalone: true,
  imports: [CommonModule, DialogModule, ButtonModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [style]="{width: '600px'}"
      [closable]="false"
      header="Approve Valuation Changes">
      
      <div class="approval-content" *ngIf="updateRequest">
        <p class="intent-summary">{{ updateRequest.intent_summary }}</p>
        
        <div class="parameters-section">
          <h4>Parameters to Update:</h4>
          <ul class="parameter-list">
            <li *ngFor="let param of getParametersList()">
              <strong>{{ param.name }}:</strong> {{ param.value }}%
            </li>
          </ul>
        </div>
        
        <div class="assumptions-section" *ngIf="updateRequest && updateRequest.assumptions_made && updateRequest.assumptions_made.length > 0">
          <h4>Assumptions Made:</h4>
          <ul class="assumptions-list">
            <li *ngFor="let assumption of updateRequest.assumptions_made">
              {{ assumption }}
            </li>
          </ul>
        </div>
      </div>
      
      <ng-template pTemplate="footer">
        <button
          pButton
          type="button"
          label="Cancel"
          class="p-button-text"
          (click)="onDeny()">
        </button>
        <button
          pButton
          type="button"
          label="Update Valuation"
          class="p-button-primary"
          (click)="onApprove()">
        </button>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    .approval-content {
      padding: 1rem 0;
    }
    
    .intent-summary {
      font-size: 1.1rem;
      margin-bottom: 1.5rem;
      color: var(--color-text-primary);
    }
    
    .parameters-section, .assumptions-section {
      margin-bottom: 1.5rem;
    }
    
    h4 {
      font-size: 0.9rem;
      font-weight: 600;
      text-transform: uppercase;
      color: var(--color-text-secondary);
      margin-bottom: 0.75rem;
    }
    
    .parameter-list, .assumptions-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    
    .parameter-list li {
      padding: 0.5rem;
      background: var(--color-bg-secondary);
      border-radius: 4px;
      margin-bottom: 0.5rem;
    }
    
    .assumptions-list li {
      padding: 0.5rem 0 0.5rem 1.5rem;
      position: relative;
      color: var(--color-text-secondary);
      font-size: 0.9rem;
    }
    
    .assumptions-list li:before {
      content: '→';
      position: absolute;
      left: 0;
    }
  `]
})
export class ValuationUpdateApprovalDialogComponent {
  @Input() visible = false;
  @Input() updateRequest: ValuationUpdateRequest | null = null;
  
  @Output() approved = new EventEmitter<string>();
  @Output() denied = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();
  
  getParametersList() {
    if (!this.updateRequest?.parameter_updates) return [];
    
    return Object.entries(this.updateRequest.parameter_updates).map(([key, value]) => ({
      name: this.formatParameterName(key),
      value: value
    }));
  }
  
  formatParameterName(key: string): string {
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, str => str.toUpperCase())
      .replace('Next Year', '')
      .trim();
  }
  
  onApprove() {
    if (this.updateRequest) {
      this.approved.emit(this.updateRequest.update_id);
      this.visible = false;
    }
  }
  
  onDeny() {
    if (this.updateRequest) {
      this.denied.emit(this.updateRequest.update_id);
      this.visible = false;
    }
  }
}

