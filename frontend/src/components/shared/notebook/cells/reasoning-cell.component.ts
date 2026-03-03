import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Cell, CellContent } from '../cell.models';

/**
 * Reasoning Cell Component
 * Main notebook-style cell for user questions and AI responses.
 * Port of React ReasoningCell to Angular.
 */
@Component({
  selector: 'app-reasoning-cell',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div 
      class="cell-container"
      [class.hovered]="isHovered()"
      [class.collapsed]="isCollapsed()"
      [class.streaming]="isStreaming"
      [class.system-cell]="cell.cell_type === 'system'"
      [class.user-cell]="cell.author_type === 'user'"
      [class.ai-cell]="cell.author_type === 'ai'"
      (mouseenter)="isHovered.set(true)"
      (mouseleave)="isHovered.set(false)"
    >
      <!-- Cell Header -->
      <div class="cell-header">
        <!-- Sequence Number -->
        <div class="sequence-area">
          @if (cell.sequence_number !== null && cell.sequence_number !== undefined) {
            <button class="run-button" title="Re-run cell">
              <i class="pi pi-play"></i>
            </button>
            <span class="sequence-number">[{{ cell.sequence_number }}]</span>
          } @else {
            <span class="sequence-number empty">[ ]</span>
          }
        </div>
        
        <!-- Cell Type Badge -->
        @if (getCellIcon()) {
          <div class="cell-type-info">
            <i [class]="getCellIcon()"></i>
            <span class="cell-type-label">{{ getCellLabel() }}</span>
          </div>
        }
        
        <div class="spacer"></div>
        
        <!-- Execution Time -->
        @if (cell.execution_time_ms) {
          <span class="execution-time">{{ cell.execution_time_ms }}ms</span>
        }
        
        <!-- Cell Actions -->
        @if (showActions()) {
          <div class="cell-actions" [class.visible]="isHovered()">
            <button class="action-btn" title="Add note" (click)="onAddNoteClick()">
              <i class="pi pi-comment"></i>
            </button>
            <button class="action-btn delete" title="Delete" (click)="onDeleteClick()">
              <i class="pi pi-trash"></i>
            </button>
          </div>
        }
        
        <!-- Collapse Toggle -->
        <button class="collapse-btn" [class.visible]="isHovered()" (click)="toggleCollapse()">
          <i [class]="isCollapsed() ? 'pi pi-chevron-right' : 'pi pi-chevron-down'"></i>
        </button>
      </div>
      
      <!-- Cell Content -->
      @if (!isCollapsed()) {
        <div class="cell-body">
          <!-- User Input Section -->
          @if (userInput) {
            <div class="user-section">
              <div class="user-bubble">
                <div class="avatar user-avatar">
                  <i class="pi pi-user"></i>
                </div>
                <div class="message-content user-message">
                  <p>{{ userInput }}</p>
                </div>
              </div>
            </div>
          }
          
          <!-- AI Output Section -->
          @if (rewrittenQuery && rewrittenQuery !== userInput) {
            <div class="rewritten-query-section">
              <div class="rewritten-header">
                <i class="pi pi-refresh"></i>
                <span>Query Clarified</span>
              </div>
              <p class="rewritten-text">{{ rewrittenQuery }}</p>
            </div>
          }
          
          <!-- Tool Results Section -->
          @if (toolResults.length > 0) {
            <div class="tool-results-section">
              @for (result of toolResults; track result.tool_name) {
                <div class="tool-result" [class.success]="result.status === 'success'" [class.error]="result.status === 'error'" [class.skipped]="result.status === 'skipped'">
                  <div class="tool-header">
                    <i [class]="getToolIcon(result.tool_name)"></i>
                    <span class="tool-name">{{ formatToolName(result.tool_name) }}</span>
                    <span class="tool-status">{{ result.status }}</span>
                    @if (result.execution_time_ms) {
                      <span class="tool-time">{{ result.execution_time_ms }}ms</span>
                    }
                  </div>
                  @if (result.status === 'success') {
                    <div class="tool-content">
                      @if (result.tool_name === 'tavily_search') {
                        <p class="search-answer">{{ result.data?.answer }}</p>
                        @if (result.data?.results?.length) {
                          <div class="search-sources">
                            @for (source of result.data.results.slice(0, 3); track source.url) {
                              <a class="source-link" [href]="source.url" target="_blank">
                                <i class="pi pi-external-link"></i>
                                {{ source.title || 'Source' }}
                              </a>
                            }
                          </div>
                        }
                      } @else if (result.tool_name === 'python_interpreter') {
                        @if (result.data?.output) {
                          <pre class="code-output">{{ result.data.output }}</pre>
                        }
                        @if (result.data?.result !== null && result.data?.result !== undefined) {
                          <div class="code-result">Result: <code>{{ formatResult(result.data.result) }}</code></div>
                        }
                      } @else {
                        <pre class="tool-data">{{ result.data | json }}</pre>
                      }
                    </div>
                  } @else if (result.status === 'error') {
                    <p class="error-message">{{ result.error }}</p>
                  } @else if (result.status === 'skipped') {
                    <p class="skipped-message">{{ result.data?.reason || 'Skipped' }}</p>
                  }
                </div>
              }
            </div>
          }
          
          <!-- Code Execution Section (if generated) -->
          @if (codeExecution) {
            <div class="code-section">
              <div class="code-header">
                <i class="pi pi-code"></i>
                <span>Generated Code</span>
                <button class="copy-btn" (click)="copyCode()">
                  <i class="pi pi-copy"></i>
                </button>
              </div>
              <pre class="code-block">{{ codeExecution.code }}</pre>
              @if (codeExecution.result?.data?.output) {
                <div class="code-output-section">
                  <div class="output-header">Output:</div>
                  <pre class="code-output">{{ codeExecution.result.data.output }}</pre>
                </div>
              }
            </div>
          }
          
          @if (aiMessage) {
            <div class="ai-section">
              <div class="ai-bubble">
                <div class="avatar ai-avatar">
                  <i class="pi pi-bolt"></i>
                </div>
                <div class="message-content ai-message">
                  <div class="markdown-content" [innerHTML]="formatMarkdown(aiMessage)"></div>
                  @if (isStreaming) {
                    <span class="typing-cursor"></span>
                  }
                </div>
              </div>
            </div>
          }
          
          <!-- User Notes Section -->
          @if (showActions()) {
            <div class="notes-section">
              @if (isEditingNotes() || cell.user_notes) {
                <div class="notes-container">
                  <div class="notes-header">
                    <i class="pi pi-bookmark"></i>
                    <span>Notes</span>
                  </div>
                  @if (isEditingNotes()) {
                    <textarea 
                      [(ngModel)]="notesValue"
                      class="notes-textarea"
                      rows="2"
                      placeholder="Add your notes..."
                    ></textarea>
                    <div class="notes-actions">
                      <button class="btn-secondary btn-sm" (click)="cancelNotes()">Cancel</button>
                      <button class="btn-primary btn-sm" (click)="saveNotes()">Save</button>
                    </div>
                  } @else {
                    <p class="notes-text" (click)="startEditingNotes()">{{ cell.user_notes }}</p>
                  }
                </div>
              }
            </div>
          }
          
          <!-- Meta Info -->
          <div class="meta-info">
            <span class="timestamp">{{ formatTimeAgo(cell.created_at) }}</span>
            @if (copied()) {
              <span class="copied-badge">
                <i class="pi pi-check"></i> Copied
              </span>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .cell-container {
      position: relative;
      margin-bottom: 1rem;
      border-radius: 0.5rem;
      border-left: 4px solid var(--color-border-secondary, #6b7280);
      background: var(--color-bg-secondary, #0d1117);
      transition: all 0.2s ease;
    }
    
    .cell-container.hovered {
      box-shadow: 0 0 0 1px var(--color-border-primary, #30363d);
    }
    
    .cell-container.user-cell {
      border-left-color: var(--color-brand-primary, #3b82f6);
    }
    
    .cell-container.ai-cell {
      border-left-color: #a855f7;
    }
    
    .cell-container.system-cell {
      border-left-color: #10b981;
    }
    
    .cell-container.streaming {
      border-left-color: #22c55e;
    }
    
    /* Header */
    .cell-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 1rem;
      background: var(--color-bg-tertiary, #161b22);
      border-bottom: 1px solid var(--color-border-tertiary, #21262d);
      border-radius: 0.5rem 0.5rem 0 0;
    }
    
    .sequence-area {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-width: 80px;
    }
    
    .run-button {
      padding: 0.25rem;
      background: transparent;
      border: none;
      color: var(--color-text-tertiary, #6b7280);
      cursor: pointer;
      opacity: 0;
      transition: opacity 0.2s, color 0.2s;
    }
    
    .cell-container.hovered .run-button {
      opacity: 1;
    }
    
    .run-button:hover {
      color: var(--color-status-success, #10b981);
    }
    
    .sequence-number {
      font-family: monospace;
      font-size: 0.75rem;
      color: var(--color-text-tertiary, #6b7280);
    }
    
    .sequence-number.empty {
      color: var(--color-text-muted, #4b5563);
      padding-left: 1.5rem;
    }
    
    .cell-type-info {
      display: flex;
      align-items: center;
      gap: 0.375rem;
    }
    
    .cell-type-info i {
      font-size: 0.875rem;
    }
    
    .user-cell .cell-type-info i { color: #3b82f6; }
    .ai-cell .cell-type-info i { color: #a855f7; }
    .system-cell .cell-type-info i { color: #10b981; }
    
    .cell-type-label {
      font-size: 0.75rem;
      color: var(--color-text-secondary, #9ca3af);
    }
    
    .spacer {
      flex: 1;
    }
    
    .execution-time {
      font-size: 0.75rem;
      color: var(--color-text-tertiary, #6b7280);
    }
    
    /* Cell Actions */
    .cell-actions {
      display: flex;
      gap: 0.25rem;
      opacity: 0;
      transition: opacity 0.2s;
    }
    
    .cell-actions.visible {
      opacity: 1;
    }
    
    .action-btn {
      padding: 0.25rem 0.5rem;
      background: transparent;
      border: none;
      color: var(--color-text-tertiary, #6b7280);
      cursor: pointer;
      border-radius: 0.25rem;
      transition: background 0.2s, color 0.2s;
    }
    
    .action-btn:hover {
      background: var(--color-bg-hover, #21262d);
      color: var(--color-text-primary, #e5e7eb);
    }
    
    .action-btn.delete:hover {
      color: #ef4444;
    }
    
    .collapse-btn {
      padding: 0.25rem;
      background: transparent;
      border: none;
      color: var(--color-text-tertiary, #6b7280);
      cursor: pointer;
      opacity: 0;
      transition: opacity 0.2s;
    }
    
    .collapse-btn.visible {
      opacity: 1;
    }
    
    /* Cell Body */
    .cell-body {
      padding: 1rem;
    }
    
    .user-section,
    .ai-section {
      margin-bottom: 1rem;
    }
    
    .user-bubble,
    .ai-bubble {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
    }
    
    .avatar {
      width: 2rem;
      height: 2rem;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    
    .user-avatar {
      background: linear-gradient(135deg, #3b82f6, #1d4ed8);
    }
    
    .ai-avatar {
      background: linear-gradient(135deg, #a855f7, #7c3aed);
    }
    
    .avatar i {
      color: white;
      font-size: 0.875rem;
    }
    
    .message-content {
      flex: 1;
      padding: 0.75rem 1rem;
      border-radius: 1rem;
    }
    
    .user-message {
      background: var(--color-bg-tertiary, #161b22);
      border-top-left-radius: 0.25rem;
    }
    
    .ai-message {
      background: transparent;
    }
    
    .message-content p {
      margin: 0;
      color: var(--color-text-primary, #e5e7eb);
      line-height: 1.5;
    }
    
    .markdown-content {
      font-size: 0.875rem;
      color: var(--color-text-secondary, #d1d5db);
      line-height: 1.6;
    }
    
    .markdown-content :deep(p) {
      margin-bottom: 0.75rem;
    }
    
    .markdown-content :deep(strong) {
      color: var(--color-text-primary, #f3f4f6);
      font-weight: 600;
    }
    
    .markdown-content :deep(ul),
    .markdown-content :deep(ol),
    .markdown-content :deep(.md-list) {
      margin: 0.5rem 0;
      padding-left: 1.5rem;
    }
    
    .markdown-content :deep(li) {
      margin-bottom: 0.25rem;
    }
    
    .markdown-content :deep(.md-h2),
    .markdown-content :deep(.md-h3),
    .markdown-content :deep(.md-h4) {
      color: var(--color-text-primary, #f3f4f6);
      margin: 0.75rem 0 0.5rem;
      font-weight: 600;
    }
    
    .markdown-content :deep(.md-h2) {
      font-size: 1.25rem;
    }
    
    .markdown-content :deep(.md-h3) {
      font-size: 1.1rem;
    }
    
    .markdown-content :deep(.md-h4) {
      font-size: 1rem;
    }
    
    .markdown-content :deep(.md-code-block) {
      background: var(--color-bg-secondary, #0d1117);
      border: 1px solid var(--color-border-tertiary, #21262d);
      border-radius: 0.375rem;
      padding: 0.75rem;
      margin: 0.5rem 0;
      overflow-x: auto;
    }
    
    .markdown-content :deep(.md-code-block code) {
      font-family: 'Monaco', 'Menlo', monospace;
      font-size: 0.8125rem;
      color: var(--color-text-highlight, #a5d6ff);
    }
    
    .markdown-content :deep(.md-inline-code) {
      background: var(--color-bg-tertiary, #161b22);
      padding: 0.125rem 0.375rem;
      border-radius: 0.25rem;
      font-family: 'Monaco', 'Menlo', monospace;
      font-size: 0.8125rem;
      color: var(--color-status-success, #10b981);
    }
    
    .markdown-content :deep(.md-hr) {
      border: none;
      border-top: 1px solid var(--color-border-tertiary, #21262d);
      margin: 0.75rem 0;
    }
    
    .markdown-content :deep(em) {
      font-style: italic;
      color: var(--color-text-secondary, #d1d5db);
    }
    
    .typing-cursor {
      display: inline-block;
      width: 0.5rem;
      height: 1rem;
      background: #10b981;
      margin-left: 0.25rem;
      animation: blink 1s infinite;
    }
    
    @keyframes blink {
      0%, 50% { opacity: 1; }
      51%, 100% { opacity: 0; }
    }
    
    /* Notes Section */
    .notes-section {
      margin-top: 0.75rem;
    }
    
    .notes-container {
      background: var(--color-bg-tertiary, #161b22);
      border-radius: 0.375rem;
      padding: 0.75rem;
    }
    
    .notes-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
      color: var(--color-text-secondary, #9ca3af);
      font-size: 0.75rem;
    }
    
    .notes-text {
      margin: 0;
      font-size: 0.875rem;
      color: var(--color-text-secondary, #d1d5db);
      cursor: pointer;
    }
    
    .notes-text:hover {
      color: var(--color-text-primary, #f3f4f6);
    }
    
    .notes-textarea {
      width: 100%;
      padding: 0.5rem;
      background: var(--color-bg-secondary, #0d1117);
      border: 1px solid var(--color-border-primary, #30363d);
      border-radius: 0.25rem;
      color: var(--color-text-primary, #e5e7eb);
      font-size: 0.875rem;
      resize: vertical;
      margin-bottom: 0.5rem;
    }
    
    .notes-actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
    }
    
    /* Meta Info */
    .meta-info {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.75rem;
      color: var(--color-text-tertiary, #6b7280);
      padding-top: 0.5rem;
    }
    
    .copied-badge {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      color: #10b981;
    }
    
    /* Buttons */
    .btn-primary,
    .btn-secondary,
    .btn-accent {
      padding: 0.5rem 1rem;
      border-radius: 0.375rem;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      border: none;
      transition: background 0.2s;
    }
    
    .btn-sm {
      padding: 0.375rem 0.75rem;
      font-size: 0.75rem;
    }
    
    .btn-primary {
      background: #3b82f6;
      color: white;
    }
    
    .btn-primary:hover {
      background: #2563eb;
    }
    
    .btn-secondary {
      background: var(--color-bg-tertiary, #374151);
      color: var(--color-text-primary, #e5e7eb);
    }
    
    .btn-secondary:hover {
      background: var(--color-bg-hover, #4b5563);
    }
    
    .btn-accent {
      background: #10b981;
      color: white;
    }
    
    .btn-accent:hover {
      background: #059669;
    }
    
    /* Rewritten Query Section */
    .rewritten-query-section {
      margin-bottom: 1rem;
      padding: 0.75rem;
      background: rgba(59, 130, 246, 0.1);
      border-radius: 0.5rem;
      border-left: 3px solid #3b82f6;
    }
    
    .rewritten-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: #3b82f6;
      font-size: 0.75rem;
      margin-bottom: 0.5rem;
    }
    
    .rewritten-text {
      margin: 0;
      color: #93c5fd;
      font-style: italic;
    }
    
    /* Tool Results Section */
    .tool-results-section {
      margin-bottom: 1rem;
    }
    
    .tool-result {
      margin-bottom: 0.75rem;
      border-radius: 0.5rem;
      background: var(--color-bg-tertiary, #161b22);
      overflow: hidden;
    }
    
    .tool-result.success {
      border-left: 3px solid #22c55e;
    }
    
    .tool-result.error {
      border-left: 3px solid #ef4444;
    }
    
    .tool-result.skipped {
      border-left: 3px solid #6b7280;
    }
    
    .tool-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.75rem;
      background: var(--color-bg-secondary, #0d1117);
      font-size: 0.75rem;
    }
    
    .tool-header i {
      color: var(--color-text-secondary, #9ca3af);
    }
    
    .tool-name {
      font-weight: 500;
      color: var(--color-text-primary, #e5e7eb);
    }
    
    .tool-status {
      margin-left: auto;
      padding: 0.125rem 0.5rem;
      border-radius: 0.25rem;
      font-size: 0.625rem;
      text-transform: uppercase;
    }
    
    .tool-result.success .tool-status {
      background: rgba(34, 197, 94, 0.2);
      color: #22c55e;
    }
    
    .tool-result.error .tool-status {
      background: rgba(239, 68, 68, 0.2);
      color: #ef4444;
    }
    
    .tool-result.skipped .tool-status {
      background: rgba(107, 114, 128, 0.2);
      color: #6b7280;
    }
    
    .tool-time {
      font-size: 0.625rem;
      color: #6b7280;
    }
    
    .tool-content {
      padding: 0.75rem;
    }
    
    .search-answer {
      margin: 0 0 0.5rem;
      color: var(--color-text-secondary, #d1d5db);
    }
    
    .search-sources {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
    }
    
    .source-link {
      display: flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.25rem 0.5rem;
      background: var(--color-bg-hover, #21262d);
      color: var(--color-brand-primary, #3b82f6);
      border-radius: 0.25rem;
      font-size: 0.75rem;
      text-decoration: none;
    }
    
    .source-link:hover {
      background: var(--color-border-primary, #30363d);
    }
    
    .error-message {
      margin: 0;
      padding: 0.75rem;
      color: #ef4444;
    }
    
    .skipped-message {
      margin: 0;
      padding: 0.75rem;
      color: var(--color-text-tertiary, #6b7280);
      font-style: italic;
    }
    
    .tool-data {
      margin: 0;
      padding: 0.5rem;
      background: var(--color-bg-secondary, #0d1117);
      border-radius: 0.25rem;
      font-size: 0.75rem;
      color: var(--color-text-secondary, #9ca3af);
      overflow-x: auto;
    }
    
    /* Code Section */
    .code-section {
      margin-bottom: 1rem;
      border-radius: 0.5rem;
      background: var(--color-bg-tertiary, #161b22);
      overflow: hidden;
    }
    
    .code-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.75rem;
      background: var(--color-bg-secondary, #0d1117);
      font-size: 0.75rem;
      color: var(--color-status-success, #22c55e);
    }
    
    .copy-btn {
      margin-left: auto;
      padding: 0.25rem;
      background: transparent;
      border: none;
      color: var(--color-text-tertiary, #6b7280);
      cursor: pointer;
    }
    
    .copy-btn:hover {
      color: var(--color-text-primary, #e5e7eb);
    }
    
    .code-block {
      margin: 0;
      padding: 1rem;
      background: var(--color-bg-secondary, #0d1117);
      font-family: 'Monaco', 'Menlo', monospace;
      font-size: 0.8125rem;
      color: var(--color-text-primary, #e5e7eb);
      overflow-x: auto;
      line-height: 1.5;
    }
    
    .code-output-section {
      padding: 0.75rem;
      border-top: 1px solid var(--color-border-tertiary, #21262d);
    }
    
    .output-header {
      font-size: 0.75rem;
      color: var(--color-text-secondary, #9ca3af);
      margin-bottom: 0.5rem;
    }
    
    .code-output {
      margin: 0;
      padding: 0.5rem;
      background: var(--color-bg-secondary, #0d1117);
      border-radius: 0.25rem;
      font-family: monospace;
      font-size: 0.8125rem;
      color: var(--color-text-highlight, #a5d6ff);
    }
    
    .code-result {
      margin-top: 0.5rem;
      padding: 0.5rem;
      background: rgba(34, 197, 94, 0.1);
      border-radius: 0.25rem;
      font-size: 0.875rem;
      color: #22c55e;
    }
    
    .code-result code {
      font-family: monospace;
      background: rgba(0, 0, 0, 0.2);
      padding: 0.125rem 0.25rem;
      border-radius: 0.125rem;
    }
  `]
})
export class ReasoningCellComponent {
  @Input({ required: true }) cell!: Cell;
  @Input() sessionId?: string;
  @Input() isFirst = false;
  @Input() isLast = false;
  @Input() isStreaming = false;

  @Output() delete = new EventEmitter<string>();
  @Output() updateNotes = new EventEmitter<{ cellId: string; notes: string }>();

  // Local state signals
  isHovered = signal(false);
  isCollapsed = signal(false);
  isEditingNotes = signal(false);
  copied = signal(false);

  notesValue = '';

  get userInput(): string {
    return this.cell.user_input || this.cell.content?.message || '';
  }

  get aiMessage(): string {
    let aiOutput: any = this.cell.ai_output || (this.cell.author_type === 'ai' ? this.cell.content : null);

    // Handle stringified JSON (for legacy cells)
    if (typeof aiOutput === 'string') {
      try {
        aiOutput = JSON.parse(aiOutput);
      } catch {
        // If it's just a string message, return it directly
        return aiOutput;
      }
    }

    // Check 'message' first (matches CellContent interface), then 'content' (legacy)
    return aiOutput?.message || aiOutput?.content || '';
  }

  get rewrittenQuery(): string {
    return this.cell.ai_output?.rewritten_query || '';
  }

  get toolResults(): any[] {
    return this.cell.ai_output?.tool_results || [];
  }

  get codeExecution(): any {
    return this.cell.ai_output?.code_execution || null;
  }

  showActions(): boolean {
    return this.cell.cell_type !== 'system' && this.cell.author_type !== 'system';
  }

  getCellIcon(): string {
    if (this.cell.cell_type === 'system') return 'pi pi-info-circle';
    if (this.cell.author_type === 'user' || this.userInput) return 'pi pi-user';
    return 'pi pi-bolt';
  }

  getCellLabel(): string {
    if (this.cell.cell_type === 'system') return 'System';
    if (this.cell.author_type === 'user' || this.userInput) return 'User';
    return 'AI Analysis';
  }

  toggleCollapse(): void {
    this.isCollapsed.set(!this.isCollapsed());
  }

  onDeleteClick(): void {
    if (confirm('Delete this cell?')) {
      this.delete.emit(this.cell.id);
    }
  }

  onAddNoteClick(): void {
    this.notesValue = this.cell.user_notes || '';
    this.isEditingNotes.set(true);
  }

  startEditingNotes(): void {
    this.notesValue = this.cell.user_notes || '';
    this.isEditingNotes.set(true);
  }

  cancelNotes(): void {
    this.isEditingNotes.set(false);
    this.notesValue = '';
  }

  saveNotes(): void {
    this.updateNotes.emit({
      cellId: this.cell.id,
      notes: this.notesValue,
    });
    this.isEditingNotes.set(false);
  }

  formatTimeAgo(dateString: string): string {
    if (!dateString) return '';

    const date = new Date(dateString);
    const now = new Date();
    const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

    if (diffInSeconds < 60) return 'just now';
    if (diffInSeconds < 3600) return `${Math.floor(diffInSeconds / 60)}m ago`;
    if (diffInSeconds < 86400) return `${Math.floor(diffInSeconds / 3600)}h ago`;
    return `${Math.floor(diffInSeconds / 86400)}d ago`;
  }

  formatMarkdown(text: string): string {
    if (!text) return '';

    // Enhanced markdown formatting
    let html = text
      // Escape HTML entities first (security)
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      // Headers (must handle before other patterns)
      .replace(/^### (.*$)/gm, '<h4 class="md-h4">$1</h4>')
      .replace(/^## (.*$)/gm, '<h3 class="md-h3">$1</h3>')
      .replace(/^# (.*$)/gm, '<h2 class="md-h2">$1</h2>')
      // Code blocks (triple backticks)
      .replace(/```([a-z]*)\n?([\s\S]*?)```/g, '<pre class="md-code-block"><code>$2</code></pre>')
      // Inline code (single backticks)
      .replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')
      // Bold
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      // Italic
      .replace(/\*(.*?)\*/g, '<em>$1</em>')
      // Horizontal rule
      .replace(/^---$/gm, '<hr class="md-hr">')
      // Line breaks
      .replace(/\\n/g, '<br>')
      .replace(/\n\n/g, '</p><p>')
      .replace(/\n/g, '<br>');

    // Handle lists separately for better structure
    const lines = html.split('<br>');
    let inList = false;
    let listType = '';
    const processedLines: string[] = [];

    for (const line of lines) {
      const trimmed = line.trim();
      const unorderedMatch = trimmed.match(/^(?:-|\u2022)\s+(.*)$/);
      const orderedMatch = trimmed.match(/^(\d+)\. (.*)$/);

      if (unorderedMatch) {
        if (!inList || listType !== 'ul') {
          if (inList) processedLines.push(`</${listType}>`);
          processedLines.push('<ul class="md-list">');
          inList = true;
          listType = 'ul';
        }
        processedLines.push(`<li>${unorderedMatch[1]}</li>`);
      } else if (orderedMatch) {
        if (!inList || listType !== 'ol') {
          if (inList) processedLines.push(`</${listType}>`);
          processedLines.push('<ol class="md-list">');
          inList = true;
          listType = 'ol';
        }
        processedLines.push(`<li>${orderedMatch[2]}</li>`);
      } else {
        if (inList) {
          processedLines.push(`</${listType}>`);
          inList = false;
          listType = '';
        }
        processedLines.push(line);
      }
    }
    if (inList) processedLines.push(`</${listType}>`);

    return `<p>${processedLines.join('<br>')}</p>`
      .replace(/<p><\/p>/g, '')
      .replace(/<p><br>/g, '<p>')
      .replace(/<br><\/p>/g, '</p>');
  }

  getToolIcon(toolName: string): string {
    const icons: Record<string, string> = {
      'tavily_search': 'pi pi-search',
      'python_interpreter': 'pi pi-code',
      'valuation_loader': 'pi pi-database',
      'llm_guard': 'pi pi-shield',
    };
    return icons[toolName] || 'pi pi-cog';
  }

  formatToolName(toolName: string): string {
    const names: Record<string, string> = {
      'tavily_search': 'Web Search',
      'python_interpreter': 'Code Execution',
      'valuation_loader': 'Valuation Data',
      'llm_guard': 'Safety Check',
    };
    return names[toolName] || toolName;
  }

  formatResult(result: any): string {
    if (typeof result === 'object') {
      return JSON.stringify(result, null, 2);
    }
    return String(result);
  }

  copyCode(): void {
    if (this.codeExecution?.code) {
      navigator.clipboard.writeText(this.codeExecution.code);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    }
  }
}
