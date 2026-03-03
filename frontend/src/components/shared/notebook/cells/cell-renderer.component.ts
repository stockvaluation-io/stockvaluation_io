import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Cell } from '../cell.models';
import { ReasoningCellComponent } from './reasoning-cell.component';

/**
 * Cell Renderer Component
 * Dispatches to the appropriate cell type component.
 */
@Component({
    selector: 'app-cell-renderer',
    standalone: true,
    imports: [CommonModule, ReasoningCellComponent],
    template: `
    @switch (cell.cell_type) {
      @case ('reasoning') {
        <app-reasoning-cell
          [cell]="cell"
          [sessionId]="sessionId"
          [isFirst]="isFirst"
          [isLast]="isLast"
          [isStreaming]="isStreaming"
          (delete)="onDelete.emit($event)"
          (updateNotes)="onUpdateNotes.emit($event)"
        />
      }
      @case ('system') {
        <app-reasoning-cell
          [cell]="cell"
          [sessionId]="sessionId"
          [isFirst]="isFirst"
          [isLast]="isLast"
          [isStreaming]="false"
          (delete)="onDelete.emit($event)"
          (updateNotes)="onUpdateNotes.emit($event)"
        />
      }
      @case ('calibration') {
        <!-- TODO: CalibrationCellComponent -->
        <div class="calibration-cell-placeholder">
          <div class="cell-header">
            <span class="cell-type-badge calibration">Calibration</span>
            <span class="sequence">[{{ cell.sequence_number }}]</span>
          </div>
          <div class="cell-content">
            <p class="placeholder-text">DCF Parameter Calibration</p>
          </div>
        </div>
      }
      @case ('visualization') {
        <!-- TODO: VisualizationCellComponent -->
        <div class="visualization-cell-placeholder">
          <div class="cell-header">
            <span class="cell-type-badge visualization">Visualization</span>
            <span class="sequence">[{{ cell.sequence_number }}]</span>
          </div>
          <div class="cell-content">
            <p class="placeholder-text">Chart/Graph Visualization</p>
          </div>
        </div>
      }
      @case ('computation') {
        <!-- TODO: CodeCellComponent -->
        <div class="computation-cell-placeholder">
          <div class="cell-header">
            <span class="cell-type-badge computation">Code</span>
            <span class="sequence">[{{ cell.sequence_number }}]</span>
          </div>
          <div class="cell-content">
            <pre class="code-block">{{ cell.content?.code || '# No code' }}</pre>
          </div>
        </div>
      }
      @default {
        <div class="unknown-cell">
          <p class="error-text">Unknown cell type: {{ cell.cell_type }}</p>
        </div>
      }
    }
  `,
    styles: [`
    .calibration-cell-placeholder,
    .visualization-cell-placeholder,
    .computation-cell-placeholder,
    .unknown-cell {
      margin-bottom: 1rem;
      border-radius: 0.5rem;
      background: #161b22;
      border: 1px solid #30363d;
      overflow: hidden;
    }
    
    .cell-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 1rem;
      background: #0d1117;
      border-bottom: 1px solid #21262d;
    }
    
    .cell-type-badge {
      padding: 0.125rem 0.5rem;
      border-radius: 0.25rem;
      font-size: 0.75rem;
      font-weight: 500;
    }
    
    .cell-type-badge.calibration {
      background: rgba(168, 85, 247, 0.2);
      color: #a855f7;
    }
    
    .cell-type-badge.visualization {
      background: rgba(59, 130, 246, 0.2);
      color: #3b82f6;
    }
    
    .cell-type-badge.computation {
      background: rgba(34, 197, 94, 0.2);
      color: #22c55e;
    }
    
    .sequence {
      font-family: monospace;
      font-size: 0.75rem;
      color: #6b7280;
    }
    
    .cell-content {
      padding: 1rem;
    }
    
    .placeholder-text {
      color: #9ca3af;
      font-style: italic;
    }
    
    .code-block {
      background: #0d1117;
      padding: 1rem;
      border-radius: 0.375rem;
      font-family: monospace;
      font-size: 0.875rem;
      color: #e5e7eb;
      overflow-x: auto;
    }
    
    .error-text {
      color: #ef4444;
      padding: 1rem;
    }
  `]
})
export class CellRendererComponent {
    @Input({ required: true }) cell!: Cell;
    @Input() sessionId?: string;
    @Input() isFirst = false;
    @Input() isLast = false;
    @Input() isStreaming = false;

    @Output() onDelete = new EventEmitter<string>();
    @Output() onUpdateNotes = new EventEmitter<{ cellId: string; notes: string }>();
}
