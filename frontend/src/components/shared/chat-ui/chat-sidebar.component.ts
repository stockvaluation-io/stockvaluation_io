import {
  AfterViewInit,
  Component,
  ElementRef,
  OnInit,
  OnDestroy,
  OnChanges,
  SimpleChanges,
  Input,
  Output,
  EventEmitter,
  signal,
  computed,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { trigger, state, style, transition, animate } from '@angular/animations';
import { ChatService } from './chat.service';
import { NotebookChatService, ToolResult, HypothesisReady, HypothesisSavePrompt } from './notebook-chat.service';
import { ChatConfig } from './chat-message.interface';
import { CellRendererComponent } from '../notebook/cells/cell-renderer.component';
import { Cell, CellType } from '../notebook/cell.models';
import { ChatSuggestionsComponent } from './chat-suggestions.component';
import { ChatSuggestion } from './suggestion-chip.component';
// ToolPlanDialogComponent removed - using chat-based approval instead
import { HypothesisReviewDialogComponent } from './hypothesis-review-dialog.component';
import { HypothesisSavePromptComponent } from './hypothesis-save-prompt.component';
import { ValuationUpdateApprovalDialogComponent, ValuationUpdateRequest } from './valuation-update-approval-dialog.component';
import { ValuationUpdateResultDialogComponent, ValuationUpdateResult } from './valuation-update-result-dialog.component';
import { ScenarioHeatmapDialogComponent, ScenarioData } from './scenario-heatmap-dialog.component';

/**
 * Chat Sidebar Component
 * Collapsible sidebar chat panel for integration into existing pages
 * Desktop: Slides in from right as sidebar
 * Mobile: Opens as full-screen modal
 */
@Component({
  selector: 'app-chat-sidebar',
  standalone: true,
  imports: [CommonModule, FormsModule, ChatSuggestionsComponent, HypothesisReviewDialogComponent, HypothesisSavePromptComponent, ValuationUpdateApprovalDialogComponent, ValuationUpdateResultDialogComponent, ScenarioHeatmapDialogComponent, CellRendererComponent],
  templateUrl: './chat-sidebar.component.html',
  styleUrls: ['./chat-sidebar.component.scss'],
  animations: [
    trigger('slideInOut', [
      state(
        'in',
        style({
          transform: 'translateX(0)',
          opacity: 1,
        })
      ),
      state(
        'out',
        style({
          transform: 'translateX(100%)',
          opacity: 0,
        })
      ),
      transition('out => in', [
        style({
          transform: 'translateX(100%)',
          opacity: 0,
        }),
        animate(
          '400ms cubic-bezier(0.4, 0.0, 0.2, 1)',
          style({
            transform: 'translateX(0)',
            opacity: 1,
          })
        ),
      ]),
      transition('in => out', [
        animate(
          '300ms cubic-bezier(0.4, 0.0, 1, 1)',
          style({
            transform: 'translateX(100%)',
            opacity: 0,
          })
        ),
      ]),
    ]),
    trigger('fadeInOut', [
      state(
        'in',
        style({
          opacity: 1,
        })
      ),
      state(
        'out',
        style({
          opacity: 0,
          display: 'none',
        })
      ),
      transition('out => in', [
        style({ opacity: 0 }),
        animate('250ms cubic-bezier(0.4, 0.0, 0.2, 1)', style({ opacity: 1 })),
      ]),
      transition('in => out', [
        animate('200ms cubic-bezier(0.4, 0.0, 1, 1)', style({ opacity: 0 })),
      ]),
    ]),
  ],
})
export class ChatSidebarComponent implements OnInit, OnDestroy, OnChanges, AfterViewInit {
  @Input() userId?: string;
  @Input() ticker?: string;
  @Input() valuationId?: string; // Valuation ID from DCF analysis response
  @Input() backendUrl: string = 'http://localhost:5000';
  @Input() autoConnect: boolean = true;
  @Input() initiallyOpen: boolean = false;
  @Input() isOpenExternal?: boolean; // External control from parent component
  @Input() showToggleButton: boolean = true;
  @Input() width: string = '400px'; // Desktop width
  @Input() contextData?: any; // Additional context (e.g., valuation data)

  @Output() opened = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();
  @Output() toolResultReceived = new EventEmitter<ToolResult>();
  @Output() expandRequested = new EventEmitter<string>(); // Emits sessionId for notebook continuity
  @ViewChild('cellsContainer') cellsContainerRef?: ElementRef<HTMLDivElement>;

  isOpen = signal(true);  // Chat window open by default
  isMinimized = signal(false);
  unreadCount = signal(0);
  isMobile = signal(false);
  suggestions = signal<ChatSuggestion[]>([]);

  // Cell-based state for unified UI
  cells = signal<Cell[]>([]);
  streamingCellId = signal<string | null>(null);
  nextSequenceNumber = 1;

  /** Prevents double-initialization when valuationId arrives after ngOnInit */
  private chatInitialized = false;

  // Message input for custom textarea
  messageInput = '';

  chatConfig: ChatConfig = {
    placeholder: 'Ask about this valuation...',
    maxLength: 2000,
    autoScroll: true,
    showTimestamp: true,
    allowMultiline: true,
    submitOnEnter: true,
  };

  latestToolResult = signal<ToolResult | null>(null);
  // Tool plan dialog removed - using chat-based approval instead
  currentHypothesis = signal<HypothesisReady | null>(null);
  showHypothesisReviewDialog = signal(false);
  currentSavePrompt = signal<HypothesisSavePrompt | null>(null);
  showHypothesisSavePrompt = signal(false);
  // Valuation update approval dialog
  currentValuationUpdate = signal<ValuationUpdateRequest | null>(null);
  showValuationUpdateDialog = signal(false);
  // Valuation update result dialog
  currentValuationResult = signal<ValuationUpdateResult | null>(null);
  showValuationResultDialog = signal(false);
  currentScenarioData = signal<ScenarioData | null>(null);
  showScenarioDialog = signal(false);
  connectionStatus = computed(() => {
    return {
      connected: this.socketService.isConnected(),
      connecting: this.socketService.isConnecting(),
      error: this.socketService.hasError(),
    };
  });

  private destroy$ = new Subject<void>();
  private lastMessageCount = 0;
  private isUserNearBottom = true;
  private scrollAnimationFrame?: number;
  private readonly onWindowResize = () => this.checkIfMobile();

  constructor(
    public chatService: ChatService,
    public socketService: NotebookChatService  // Migrated from Socket.IO to SSE
  ) { }

  ngOnInit(): void {
    // Detect mobile
    this.checkIfMobile();
    window.addEventListener('resize', this.onWindowResize);

    // Set initial open state - prefer external control if provided, otherwise use initiallyOpen
    const initialState = this.isOpenExternal !== undefined ? this.isOpenExternal : this.initiallyOpen;
    this.isOpen.set(initialState);

    // Setup Socket.IO
    if (this.backendUrl) {
      this.socketService.setBackendUrl(this.backendUrl);
    }

    // Tool plan dialog removed - using chat-based approval instead
    // Tool approvals are now handled in the chat conversation

    // Subscribe to hypothesis ready events
    this.socketService.hypothesisReady$
      .pipe(takeUntil(this.destroy$))
      .subscribe((hypothesisReady) => {
        console.log('Hypothesis ready received:', hypothesisReady);
        this.currentHypothesis.set(hypothesisReady);
        this.showHypothesisReviewDialog.set(true);
      });

    // Subscribe to hypothesis save prompt events
    this.socketService.hypothesisSavePrompt$
      .pipe(takeUntil(this.destroy$))
      .subscribe((savePrompt) => {
        console.log('Hypothesis save prompt received:', savePrompt);
        this.currentSavePrompt.set(savePrompt);
        this.showHypothesisSavePrompt.set(true);
      });

    // Subscribe to hypothesis saved events
    this.socketService.hypothesisSaved$
      .pipe(takeUntil(this.destroy$))
      .subscribe((result) => {
        console.log('Hypothesis saved:', result);
        // Show success message or update UI
        if (result.success) {
          this.chatService.addAiMessage(result.message || 'Investment thesis saved successfully!', {
            metadata: { system: true, success: true }
          });
        }
      });

    // Subscribe to tool results
    this.socketService.toolResult$
      .pipe(takeUntil(this.destroy$))
      .subscribe((result) => {
        console.log('Tool result received:', result);
        this.toolResultReceived.emit(result);

        // Check if this is a valuation update result
        if (result.toolName === 'update_valuation' && result.result) {
          this.handleValuationUpdateResult(result.result);
        } else if (result.toolName === 'calculate_scenarios' && result.result) {
          this.handleScenarioResult(result.result);
        } else {
          // Show banner for other tools
          this.latestToolResult.set(result);

          // Auto-hide tool result after 8 seconds
          setTimeout(() => {
            if (this.latestToolResult() === result) {
              this.latestToolResult.set(null);
            }
          }, 8000);
        }
      });

    // Subscribe to errors
    this.socketService.error$
      .pipe(takeUntil(this.destroy$))
      .subscribe((error) => {
        console.error('Socket error:', error);
      });

    // Subscribe to valuation update approval requests
    this.socketService.onValuationUpdateApprovalRequest()
      .pipe(takeUntil(this.destroy$))
      .subscribe((request) => {
        console.log('Valuation update approval request received:', request);
        this.currentValuationUpdate.set(request);
        this.showValuationUpdateDialog.set(true);
      });

    // Subscribe to suggestions from backend
    this.socketService.suggestions$
      .pipe(takeUntil(this.destroy$))
      .subscribe((suggestions) => {
        console.log('Suggestions received:', suggestions);
        this.suggestions.set(suggestions);
      });

    // Track unread messages when closed
    this.chatService.messages$.pipe(takeUntil(this.destroy$)).subscribe((messages) => {
      if (!this.isOpen()) {
        const newMessageCount = messages.filter(m => m.sender === 'ai').length;
        if (newMessageCount > this.lastMessageCount) {
          this.unreadCount.set(this.unreadCount() + (newMessageCount - this.lastMessageCount));
        }
        this.lastMessageCount = newMessageCount;
      }
    });

    // Subscribe to message chunks for cell-based rendering
    this.socketService.messageChunk$
      .pipe(takeUntil(this.destroy$))
      .subscribe((chunk: { content: string; isComplete: boolean; messageId: string }) => {
        this.handleCellStreamChunk(chunk.content);
      });

    // Subscribe to completion events
    this.socketService.completion$
      .pipe(takeUntil(this.destroy$))
      .subscribe(() => {
        this.finalizeCellStream();
      });

    // Subscribe to opening message to create initial AI cell
    this.socketService.openingMessage$
      .pipe(takeUntil(this.destroy$))
      .subscribe((message: string) => {
        if (message) {
          const openingCell: Cell = {
            id: `cell_${Date.now()}_opening`,
            session_id: this.socketService.getCurrentSessionId() || '',
            sequence_number: this.nextSequenceNumber++,
            cell_type: 'reasoning',
            author_type: 'ai',
            ai_output: { message },
            created_at: new Date().toISOString(),
          };
          this.cells.update((cells: Cell[]) => [...cells, openingCell]);
          this.scheduleScrollToBottom(true);
        }
      });

    // Subscribe to existing cells when resuming a session
    this.socketService.existingCells$
      .pipe(takeUntil(this.destroy$))
      .subscribe((cells: any[]) => {
        if (cells && cells.length > 0) {
          console.log(`[ChatSidebar] Loading ${cells.length} existing cells from resumed session`);
          // Map backend cells to frontend Cell format
          const mappedCells: Cell[] = cells.map((cell, index) => ({
            id: cell.id,
            session_id: cell.session_id,
            sequence_number: cell.sequence_number || index + 1,
            cell_type: cell.cell_type || 'reasoning',
            author_type: cell.author_type || (cell.user_input ? 'user' : 'ai'),
            user_input: cell.user_input,
            ai_output: cell.ai_output,
            content: cell.content,
            created_at: cell.created_at,
            execution_time_ms: cell.execution_time_ms,
          }));
          this.cells.set(mappedCells);
          this.nextSequenceNumber = mappedCells.length + 1;
          this.scheduleScrollToBottom(true);
        }
      });

    // Auto-connect if enabled
    if (this.autoConnect && this.ticker) {
      this.connect();
    }

    // Initialize chat session
    this.initializeChat();
    this.chatInitialized = true;
  }

  ngAfterViewInit(): void {
    this.scheduleScrollToBottom(true);
  }

  ngOnChanges(changes: SimpleChanges): void {
    // Sync external state changes to internal state
    if (changes['isOpenExternal'] && this.isOpenExternal !== undefined) {
      const shouldBeOpen = this.isOpenExternal;
      if (this.isOpen() !== shouldBeOpen) {
        this.isOpen.set(shouldBeOpen);
        if (shouldBeOpen) {
          this.isMinimized.set(false);
          this.unreadCount.set(0);
        }
      }
    }

    // Handle valuationId becoming available or changing
    if (changes['valuationId'] && this.valuationId) {
      const previousValue = changes['valuationId'].previousValue;
      const isFirstTimeSet = previousValue === undefined || previousValue === null;

      console.log(`[ChatSidebar] valuationId update detected: ${previousValue} -> ${this.valuationId}`);

      if (isFirstTimeSet) {
        // valuationId just arrived for the first time.
        // ngOnInit already called initializeChat() without it; reconnect so the
        // backend gets the full valuation context, but DON'T call initializeChat()
        // again — that would send the opening message twice.
        console.log(`[ChatSidebar] valuationId now available: ${this.valuationId}`);
        if (this.socketService.isConnected()) {
          console.log(`[ChatSidebar] Reconnecting with valuationId context...`);
          this.socketService.disconnect();
        }
        this.connect();
        // Only re-init if chat wasn't already fully initialized with this ID
        if (!this.chatInitialized) {
          this.initializeChat();
          this.chatInitialized = true;
        }
      } else if (previousValue !== this.valuationId) {
        // valuationId genuinely changed to a different stock — full reset.
        console.log(`[ChatSidebar] valuationId changed, reconnecting...`);
        this.socketService.disconnect();
        this.connect();
        this.initializeChat();
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.socketService.disconnect();
    window.removeEventListener('resize', this.onWindowResize);
    if (this.scrollAnimationFrame !== undefined) {
      cancelAnimationFrame(this.scrollAnimationFrame);
      this.scrollAnimationFrame = undefined;
    }
  }

  /**
   * Check if device is mobile
   */
  private checkIfMobile(): void {
    this.isMobile.set(window.innerWidth < 768);
  }

  /**
   * Initialize chat session (opening message will come from backend)
   */
  private initializeChat(): void {
    this.chatService.startNewSession({
      ticker: this.ticker,
      userId: this.userId,
      valuationId: this.valuationId,
      context: this.contextData,
    });

    // Clear cells for new session
    this.cells.set([]);
    this.nextSequenceNumber = 1;

    // Don't add generic welcome message here - backend will provide a proactive opening question
    // based on the valuation data if valuationId is provided
  }

  /**
   * Connect to Socket.IO server with valuation context
   */
  connect(): void {
    if (!this.ticker) {
      console.error('Cannot connect: ticker is required');
      return;
    }
    // Pass valuationId to enable backend to load full valuation context
    // userId is extracted from auth token on the backend
    this.socketService.connect(this.ticker, this.valuationId);
  }

  /**
   * Toggle sidebar open/closed
   */
  toggle(): void {
    if (this.isOpen()) {
      this.close();
    } else {
      this.open();
    }
  }

  /**
   * Open sidebar
   */
  open(): void {
    // Only update if not already open to avoid unnecessary updates
    if (!this.isOpen()) {
      this.isOpen.set(true);
      this.isMinimized.set(false);
      this.unreadCount.set(0);
      this.scheduleScrollToBottom(true);
      this.opened.emit();
    }
  }

  /**
   * Close sidebar
   */
  close(): void {
    // Only update if not already closed to avoid unnecessary updates
    if (this.isOpen()) {
      this.isOpen.set(false);
      this.closed.emit();
    }
  }

  /**
   * Minimize sidebar (show only header)
   */
  minimize(): void {
    this.isMinimized.set(!this.isMinimized());
  }

  /**
   * Handle user message - create unified cell with user_input and streaming ai_output
   */
  handleMessage(message: string): void {
    if (!this.socketService.isConnected()) {
      this.chatService.addAiMessage(
        'Not connected to server. Please wait while I reconnect...',
        { error: true }
      );
      this.socketService.reconnect();
      return;
    }

    // Create UNIFIED cell with user input (AI output will be added via streaming)
    // This matches the notebook behavior where user + AI are in the same cell
    const unifiedCell: Cell = {
      id: `cell_${Date.now()}`,
      session_id: this.socketService.getCurrentSessionId() || '',
      sequence_number: this.nextSequenceNumber++,
      cell_type: 'reasoning',
      user_input: message,
      author_type: 'user',
      ai_output: { message: '' },  // Will be populated via streaming
      created_at: new Date().toISOString(),
      is_streaming: true,  // Mark as actively streaming
    };

    this.cells.update((cells: Cell[]) => [...cells, unifiedCell]);
    this.streamingCellId.set(unifiedCell.id);
    this.scheduleScrollToBottom(true);

    // Send message via notebook service
    this.socketService.sendMessage(message, {
      ticker: this.ticker,
      context: this.contextData,
    });
  }

  /**
   * Send message from custom textarea input
   */
  sendMessageFromInput(event: Event): void {
    // Prevent form submission / default behavior
    event.preventDefault();
    event.stopPropagation();

    const message = this.messageInput.trim();
    if (!message) return;

    // Clear input
    this.messageInput = '';

    // Use existing handleMessage
    this.handleMessage(message);
  }

  /**
   * Handle streaming chunk for cell
   */
  private handleCellStreamChunk(chunk: string): void {
    const cellId = this.streamingCellId();
    if (!cellId) return;

    this.cells.update((cells: Cell[]) => {
      const updated = [...cells];
      const cellIndex = updated.findIndex(c => c.id === cellId);

      if (cellIndex >= 0) {
        const cell = { ...updated[cellIndex] };
        // Use 'message' to match CellContent interface
        const currentMessage = cell.ai_output?.message || '';
        cell.ai_output = {
          ...cell.ai_output,
          message: currentMessage + chunk
        };
        updated[cellIndex] = cell;
      }

      return updated;
    });
    this.scheduleScrollToBottom(false);
  }

  /**
   * Finalize cell stream when complete
   */
  private finalizeCellStream(): void {
    const cellId = this.streamingCellId();
    if (!cellId) return;

    this.cells.update((cells: Cell[]) => {
      const updated = [...cells];
      const cellIndex = updated.findIndex(c => c.id === cellId);

      if (cellIndex >= 0) {
        const cell = { ...updated[cellIndex] };
        cell.is_streaming = false;
        updated[cellIndex] = cell;
      }

      return updated;
    });

    this.streamingCellId.set(null);
    this.scheduleScrollToBottom(false);
  }

  onCellsScroll(): void {
    const container = this.cellsContainerRef?.nativeElement;
    if (!container) return;

    const thresholdPx = 120;
    const distanceFromBottom = container.scrollHeight - container.scrollTop - container.clientHeight;
    this.isUserNearBottom = distanceFromBottom <= thresholdPx;
  }

  private scheduleScrollToBottom(force: boolean): void {
    if (this.scrollAnimationFrame !== undefined) {
      cancelAnimationFrame(this.scrollAnimationFrame);
    }

    this.scrollAnimationFrame = requestAnimationFrame(() => {
      this.scrollAnimationFrame = undefined;
      this.scrollToBottom(force);
    });
  }

  private scrollToBottom(force: boolean): void {
    const container = this.cellsContainerRef?.nativeElement;
    if (!container) return;
    if (!force && !this.isUserNearBottom) return;

    container.scrollTop = container.scrollHeight;
  }

  onCellUpdateNotes(event: { cellId: string; notes: string }): void {
    this.socketService.updateCell(event.cellId, { user_notes: event.notes })
      .then((response) => {
        const updatedCell: Cell | null = (response?.cell || response) as Cell | null;
        if (!updatedCell || !updatedCell.id) return;
        this.cells.update((cells: Cell[]) => {
          const idx = cells.findIndex(c => c.id === updatedCell.id);
          if (idx < 0) return cells;
          const next = [...cells];
          next[idx] = { ...next[idx], ...updatedCell };
          return next;
        });
      })
      .catch((error) => {
        console.error('[ChatSidebar] Failed to update cell notes:', error);
        this.chatService.addAiMessage('Failed to save note.', { error: true });
      });
  }

  onCellDelete(cellId: string): void {
    this.socketService.deleteCell(cellId)
      .then(() => {
        this.cells.update((cells: Cell[]) => cells.filter(c => c.id !== cellId));
      })
      .catch((error) => {
        console.error('[ChatSidebar] Failed to delete cell:', error);
        this.chatService.addAiMessage('Failed to delete cell.', { error: true });
      });
  }

  /**
   * Clear chat
   */
  clearChat(): void {
    if (confirm('Clear chat history?')) {
      this.chatService.clearMessages();
      this.initializeChat();
    }
  }

  /**
   * Reconnect to server
   */
  reconnect(): void {
    this.socketService.reconnect();
  }

  /**
   * Expand to full-screen notebook mode in a new browser tab
   * Opens /notebook/:sessionId with ticker as query param
   */
  onExpandClick(): void {
    const sessionId = this.socketService.getCurrentSessionId();
    if (sessionId) {
      // Open notebook in new tab with session ID and ticker
      const url = `/notebook/${sessionId}?ticker=${encodeURIComponent(this.ticker || '')}`;
      window.open(url, '_blank');
    } else {
      console.warn('[ChatSidebar] No active session to expand');
    }
  }

  /**
   * Format tool result for display
   */
  formatToolResult(result: any): string {
    if (typeof result === 'object') {
      // Show key metrics if it's a valuation result
      if (result.intrinsicValue) {
        return `Intrinsic Value: $${result.intrinsicValue.toFixed(2)}`;
      }
      if (result.dcfValue) {
        return `DCF Value: $${result.dcfValue.toFixed(2)}`;
      }
      return JSON.stringify(result, null, 2);
    }
    return String(result);
  }

  formatImpactSummary(oldValue: number, newValue: number, changePercent: number): string {
    if (!oldValue || !newValue) {
      return 'Valuation updated successfully.';
    }

    const direction = changePercent > 0 ? 'increased' : changePercent < 0 ? 'decreased' : 'remained';
    const absChange = Math.abs(changePercent);

    return `The updated valuation ${direction} by ${absChange.toFixed(2)}%, from $${oldValue.toFixed(2)} to $${newValue.toFixed(2)} per share.`;
  }

  /**
   * Get connection status icon
   */
  getConnectionIcon(): string {
    if (this.connectionStatus().connected) return 'pi-check-circle';
    if (this.connectionStatus().connecting) return 'pi-spin pi-spinner';
    return 'pi-exclamation-circle';
  }

  /**
   * Get connection status color
   */
  getConnectionColor(): string {
    if (this.connectionStatus().connected) return '#10b981';
    if (this.connectionStatus().connecting) return '#3b82f6';
    return '#ef4444';
  }

  /**
   * Handle suggestion chip click - send suggestion text as message
   */
  onSuggestionClick(suggestion: ChatSuggestion): void {
    console.log('Suggestion clicked:', suggestion);

    // Clear suggestions after click (one-time use)
    this.suggestions.set([]);

    // Send the suggestion text as if the user typed it
    this.handleMessage(suggestion.text);
  }

  // Tool approval handlers removed - using chat-based approval instead

  /**
   * Handle hypothesis approval
   */
  onHypothesisApproved(hypothesisId?: string): void {
    const hypothesis = this.currentHypothesis();
    if (hypothesis || hypothesisId) {
      const id = hypothesisId || hypothesis?.hypothesis_id;
      if (id) {
        this.socketService.approveHypothesis(id);
        this.showHypothesisReviewDialog.set(false);
      }
    }
  }

  /**
   * Handle hypothesis revision
   */
  onHypothesisRevised(hypothesisId?: string): void {
    const hypothesis = this.currentHypothesis();
    if (hypothesis || hypothesisId) {
      const id = hypothesisId || hypothesis?.hypothesis_id;
      if (id) {
        this.socketService.reviseHypothesis(id);
        this.showHypothesisReviewDialog.set(false);
        this.currentHypothesis.set(null);
      }
    }
  }

  /**
   * Handle hypothesis review dialog close
   */
  onHypothesisReviewClosed(): void {
    this.showHypothesisReviewDialog.set(false);
    // Don't auto-revise on close - let user explicitly choose
  }

  /**
   * Handle hypothesis save
   */
  onHypothesisSave(hypothesisId: string): void {
    this.socketService.saveHypothesis(hypothesisId);
    this.showHypothesisSavePrompt.set(false);
    this.currentSavePrompt.set(null);
  }

  /**
   * Handle hypothesis save cancel
   */
  onHypothesisSaveCancel(): void {
    this.showHypothesisSavePrompt.set(false);
    this.currentSavePrompt.set(null);
  }

  /**
   * Handle valuation update approval
   */
  onValuationUpdateApproved(updateId: string): void {
    this.socketService.approveValuationUpdate(updateId);
    this.showValuationUpdateDialog.set(false);
    this.currentValuationUpdate.set(null);
  }

  /**
   * Handle valuation update denial
   */
  onValuationUpdateDenied(updateId: string): void {
    this.socketService.denyValuationUpdate(updateId);
    this.showValuationUpdateDialog.set(false);
    this.currentValuationUpdate.set(null);
  }

  /**
   * Handle valuation update result - parse and show in dialog
   */
  handleValuationUpdateResult(result: any): void {
    console.log('Parsing valuation update result:', result);

    try {
      // Parse the result string if it's a formatted message
      const valuationResult: ValuationUpdateResult = {
        ticker: this.ticker || 'Stock',
        parameters_changed: {},
        previous_intrinsic_value: 0,
        new_intrinsic_value: 0,
        change_percent: 0,
        impact_summary: '',
        user_intent: ''
      };

      // If result is a string (formatted message), parse it
      if (typeof result === 'string') {
        // Extract parameters changed
        const paramsMatch = result.match(/Parameters Changed:(.*?)Impact:/s);
        if (paramsMatch) {
          const paramsText = paramsMatch[1];
          const paramLines = paramsText.split('\n').filter(l => l.trim().startsWith('-'));

          paramLines.forEach(line => {
            const match = line.match(/-\s*(.+?):\s*(.+)/);
            if (match) {
              const [, key, value] = match;
              valuationResult.parameters_changed[key.trim()] = value.trim();
            }
          });
        }

        // Extract user intent
        const intentMatch = result.match(/Intent:\s*(.+?)(?:\n|$)/);
        if (intentMatch) {
          valuationResult.user_intent = intentMatch[1].trim();
        }

        // Extract previous intrinsic value
        const prevValueMatch = result.match(/Previous Intrinsic Value:\s*\$?([\d,.]+)/);
        if (prevValueMatch) {
          valuationResult.previous_intrinsic_value = parseFloat(prevValueMatch[1].replace(/,/g, ''));
        }

        // Extract new intrinsic value
        const newValueMatch = result.match(/New Intrinsic Value:\s*\$?([\d,.]+)/);
        if (newValueMatch) {
          valuationResult.new_intrinsic_value = parseFloat(newValueMatch[1].replace(/,/g, ''));
        }

        // Extract change percent
        const changeMatch = result.match(/Change:\s*([+-]?[\d.]+)%/);
        if (changeMatch) {
          valuationResult.change_percent = parseFloat(changeMatch[1]);
        }

        // Extract impact summary
        const summaryMatch = result.match(/This shows that(.+?)(?=\n\n|$)/s);
        if (summaryMatch) {
          valuationResult.impact_summary = 'This shows that' + summaryMatch[1].trim();
        }
      } else if (typeof result === 'object') {
        // If result is already an object, map from backend format
        valuationResult.ticker = result.ticker || this.ticker || 'Stock';
        valuationResult.parameters_changed = result.parameters_updated || result.parameters_changed || {};
        valuationResult.previous_intrinsic_value = result.old_intrinsic_value || result.previous_intrinsic_value || 0;
        valuationResult.new_intrinsic_value = result.new_intrinsic_value || result.intrinsicValue || 0;
        valuationResult.change_percent = result.change_percentage || result.change_percent || 0;
        valuationResult.impact_summary = result.impact_summary || result.intent_summary ||
          this.formatImpactSummary(
            result.old_intrinsic_value || 0,
            result.new_intrinsic_value || 0,
            result.change_percentage || 0
          );
        valuationResult.user_intent = result.intent_summary || result.user_intent || '';
      }

      // Show the dialog
      this.currentValuationResult.set(valuationResult);
      this.showValuationResultDialog.set(true);

    } catch (error) {
      console.error('Error parsing valuation update result:', error);
      // Fallback: show in banner
      this.latestToolResult.set({ toolName: 'update_valuation', result, success: true });
    }
  }

  /**
   * Handle valuation result dialog close
   */
  onValuationResultClosed(): void {
    this.showValuationResultDialog.set(false);
    this.currentValuationResult.set(null);
  }

  /**
   * Handle scenario result from calculate_scenarios tool
   */
  handleScenarioResult(result: any): void {
    console.log('📊 [SCENARIO RESULT] Raw result:', result);
    console.log('📊 [SCENARIO RESULT] Result type:', typeof result);

    // Parse if result is a string (backend sends Python dict string, not JSON)
    let parsedResult = result;
    if (typeof result === 'string') {
      try {
        // Backend sends Python dict format {'key': 'value'} instead of JSON {"key": "value"}
        // Convert Python dict to JSON format
        let cleaned = result.trim();

        // Simple approach: Replace single quotes with double quotes
        // This works for most cases since Python dict strings typically don't have apostrophes
        cleaned = cleaned.replace(/'/g, '"');

        // Replace Python boolean/null values with JSON equivalents
        cleaned = cleaned.replace(/\bTrue\b/g, 'true');
        cleaned = cleaned.replace(/\bFalse\b/g, 'false');
        cleaned = cleaned.replace(/\bNone\b/g, 'null');

        // Parse as JSON
        parsedResult = JSON.parse(cleaned);
        console.log('📊 [SCENARIO RESULT] ✅ Parsed Python dict string to object:', parsedResult);
      } catch (e) {
        console.error('📊 [SCENARIO RESULT] ❌ Failed to parse string:', e);
        console.error('📊 [SCENARIO RESULT] String content (first 200 chars):', result.substring(0, 200));
        // Try one more time with a different approach - maybe it's already valid JSON?
        try {
          parsedResult = JSON.parse(result.trim());
          console.log('📊 [SCENARIO RESULT] ✅ Parsed as JSON on second attempt');
        } catch (e2) {
          console.error('📊 [SCENARIO RESULT] ❌ Both parsing attempts failed');
          return;
        }
      }
    } else if (result && typeof result === 'object') {
      // Already an object, use as-is
      parsedResult = result;
      console.log('📊 [SCENARIO RESULT] ✅ Result is already an object');
    } else {
      console.error('📊 [SCENARIO RESULT] ❌ Unexpected result type:', typeof result);
      return;
    }

    console.log('📊 [SCENARIO RESULT] Result keys:', Object.keys(parsedResult || {}));
    console.log('📊 [SCENARIO RESULT] parsedResult.scenarios:', parsedResult.scenarios);
    console.log('📊 [SCENARIO RESULT] parsedResult.heatmap_data:', parsedResult.heatmap_data);
    console.log('📊 [SCENARIO RESULT] parsedResult.heat_map_data:', parsedResult.heat_map_data);

    try {
      // Map the result to ScenarioData format
      const scenarioData: ScenarioData = {
        ticker: parsedResult.ticker || this.ticker || 'Stock',
        scenarios: {
          optimistic: {
            intrinsic_value: parsedResult.scenarios?.optimistic?.intrinsic_value || parsedResult.scenarios?.bull?.intrinsic_value || 0,
            assumptions: parsedResult.scenarios?.optimistic?.assumptions || parsedResult.scenarios?.bull?.assumptions || '',
            probability: parsedResult.scenarios?.optimistic?.probability || parsedResult.scenarios?.bull?.probability || '30%',
            investment_thesis: parsedResult.scenarios?.optimistic?.investment_thesis || parsedResult.scenarios?.bull?.investment_thesis,
            causal_chain: parsedResult.scenarios?.optimistic?.causal_chain || parsedResult.scenarios?.bull?.causal_chain
          },
          base: {
            intrinsic_value: parsedResult.scenarios?.base?.intrinsic_value || parsedResult.scenarios?.base_case?.intrinsic_value || 0,
            assumptions: parsedResult.scenarios?.base?.assumptions || parsedResult.scenarios?.base_case?.assumptions || '',
            probability: parsedResult.scenarios?.base?.probability || parsedResult.scenarios?.base_case?.probability || '50%',
            investment_thesis: parsedResult.scenarios?.base?.investment_thesis || parsedResult.scenarios?.base_case?.investment_thesis,
            causal_chain: parsedResult.scenarios?.base?.causal_chain || parsedResult.scenarios?.base_case?.causal_chain
          },
          pessimistic: {
            intrinsic_value: parsedResult.scenarios?.pessimistic?.intrinsic_value || parsedResult.scenarios?.bear?.intrinsic_value || 0,
            assumptions: parsedResult.scenarios?.pessimistic?.assumptions || parsedResult.scenarios?.bear?.assumptions || '',
            probability: parsedResult.scenarios?.pessimistic?.probability || parsedResult.scenarios?.bear?.probability || '20%',
            investment_thesis: parsedResult.scenarios?.pessimistic?.investment_thesis || parsedResult.scenarios?.bear?.investment_thesis,
            causal_chain: parsedResult.scenarios?.pessimistic?.causal_chain || parsedResult.scenarios?.bear?.causal_chain
          }
        },
        heatmap_data: parsedResult.heatmap_data || parsedResult.heat_map_data,
        summary: parsedResult.summary
      };

      // Debug logging
      console.log('📊 [SCENARIO DEBUG] scenarioData:', scenarioData);
      console.log('📊 [SCENARIO DEBUG] Has heatmap_data?', !!scenarioData.heatmap_data);
      if (scenarioData.heatmap_data) {
        console.log('📊 [SCENARIO DEBUG] Heatmap growth_rates:', scenarioData.heatmap_data.growth_rates);
        console.log('📊 [SCENARIO DEBUG] Heatmap discount_rates:', scenarioData.heatmap_data.discount_rates);
        console.log('📊 [SCENARIO DEBUG] Heatmap valuations grid:', scenarioData.heatmap_data.valuations);
      }

      // Show the dialog - set data first, then open dialog after a microtask to ensure signal propagation
      this.currentScenarioData.set(scenarioData);

      // Use setTimeout to ensure signal has propagated before opening dialog
      setTimeout(() => {
        // Double-check data is still set before opening
        if (this.currentScenarioData()) {
          this.showScenarioDialog.set(true);
        } else {
          console.error('📊 [SCENARIO] Data was cleared before dialog could open, re-setting...');
          this.currentScenarioData.set(scenarioData);
          this.showScenarioDialog.set(true);
        }
      }, 0);

    } catch (error) {
      console.error('Error parsing scenario result:', error);
      // Fallback: show in banner
      this.latestToolResult.set({ toolName: 'calculate_scenarios', result, success: true });
    }
  }

  /**
   * Handle scenario dialog close
   */
  onScenarioDialogClosed(): void {
    this.showScenarioDialog.set(false);
    this.currentScenarioData.set(null);
  }

  /**
   * Handle hypothesis save prompt close
   */
  onHypothesisSavePromptClosed(): void {
    this.showHypothesisSavePrompt.set(false);
    this.currentSavePrompt.set(null);
  }
}
