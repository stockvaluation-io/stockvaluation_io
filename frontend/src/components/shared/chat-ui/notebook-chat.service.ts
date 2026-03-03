import { Injectable, signal } from '@angular/core';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import { ChatService } from './chat.service';
import { ChatMessage } from './chat-message.interface';
import { MarkdownRendererService } from './markdown-renderer.service';

/**
 * Notebook-based Chat Service (migrated from Socket.IO to SSE)
 * 
 * This adapter uses the notebook API endpoints with SSE streaming
 * while maintaining compatibility with existing chat UI components.
 * 
 * KEY CHANGES FROM Socket.IO VERSION:
 * - Uses fetch() with SSE instead of Socket.IO
 * - Calls /bullbeargpt/api/notebook/sessions instead of /chat
 * - Handles cells internally, exposes as messages to UI
 */

export interface SocketConfig {
    url?: string;
    reconnectionAttempts?: number;
    reconnectionDelay?: number;
    timeout?: number;
}

export interface ConnectionStatus {
    connected: boolean;
    connecting: boolean;
    error: string | null;
    reconnectAttempt: number;
}

export interface MessageChunk {
    content: string;
    isComplete: boolean;
    messageId: string;
}

export interface ToolResult {
    toolName?: string;
    tool_name?: string;
    result: any;
    success: boolean;
    error?: string;
}

export interface ToolPlan {
    session_id: string;
    tool_id: string;
    tool_name: string;
    tool_input: any;
    plan: string;
    timestamp: string;
}

export interface Hypothesis {
    ticker: string;
    thesis_statement: string;
    key_assumptions: string[];
    timeframe: string;
    conviction: number;
    fair_value: number;
    current_price: number;
    catalysts?: string[];
    risks?: string[];
}

export interface HypothesisReady {
    session_id: string;
    hypothesis_id: string;
    hypothesis: Hypothesis;
    confidence: number;
    reasons: string[];
    timestamp: string;
}

export interface HypothesisSavePrompt {
    session_id: string;
    hypothesis_id: string;
    hypothesis: Hypothesis;
    timestamp: string;
}

export interface ChatRequest {
    message: string;
    userId?: string;
    ticker?: string;
    sessionId?: string;
    context?: any;
}

@Injectable({
    providedIn: 'root',
})
export class NotebookChatService {
    private currentUserId: string | null = null;
    private currentTicker: string | null = null;
    private currentSessionId: string | null = null;
    private abortController: AbortController | null = null;

    /** Get current session ID for session handoff (e.g., to notebook) */
    public getCurrentSessionId(): string | null {
        return this.currentSessionId;
    }

    // Observables for backward compatibility
    private connectionStatusSubject = new BehaviorSubject<ConnectionStatus>({
        connected: false,
        connecting: false,
        error: null,
        reconnectAttempt: 0,
    });

    private messageChunkSubject = new Subject<MessageChunk>();
    private toolResultSubject = new Subject<ToolResult>();
    private toolPlanSubject = new Subject<ToolPlan>();
    private hypothesisReadySubject = new Subject<HypothesisReady>();
    private hypothesisSavePromptSubject = new Subject<HypothesisSavePrompt>();
    private hypothesisSavedSubject = new Subject<any>();
    private valuationUpdateApprovalRequestSubject = new Subject<any>();
    private errorSubject = new Subject<string>();
    private completionSubject = new Subject<void>();
    private suggestionsSubject = new Subject<any[]>();
    private openingMessageSubject = new Subject<string>();
    private existingCellsSubject = new Subject<any[]>(); // For session resumption

    // Public observables
    public connectionStatus$ = this.connectionStatusSubject.asObservable();
    public messageChunk$ = this.messageChunkSubject.asObservable();
    public toolResult$ = this.toolResultSubject.asObservable();
    public toolPlan$ = this.toolPlanSubject.asObservable();
    public hypothesisReady$ = this.hypothesisReadySubject.asObservable();
    public hypothesisSavePrompt$ = this.hypothesisSavePromptSubject.asObservable();
    public hypothesisSaved$ = this.hypothesisSavedSubject.asObservable();
    public error$ = this.errorSubject.asObservable();
    public completion$ = this.completionSubject.asObservable();
    public suggestions$ = this.suggestionsSubject.asObservable();
    public openingMessage$ = this.openingMessageSubject.asObservable();
    public existingCells$ = this.existingCellsSubject.asObservable(); // For session resumption

    // Signals for reactive state
    public isConnected = signal(false);
    public isConnecting = signal(false);
    public hasError = signal(false);
    public currentSuggestions = signal<any[]>([]);

    // Configuration
    private config: SocketConfig = {
        url: typeof window !== 'undefined' ? window.location.origin : 'http://localhost:5000',
        reconnectionAttempts: 5,
        reconnectionDelay: 1000,
        timeout: 20000,
    };

    // Current streaming message
    private currentStreamingMessageId: string | null = null;
    private streamingBuffer: string = '';

    constructor(
        private chatService: ChatService,
        private markdownRenderer: MarkdownRendererService
    ) { }

    private async getAuthToken(): Promise<string | null> {
        return null;
    }

    /**
     * Set backend URL (for compatibility, config.url is already set)
     */
    setBackendUrl(url: string): void {
        this.config.url = url;
        console.log(`[NotebookChat] Backend URL set to: ${url}`);
    }

    /**
     * Reconnect (for compatibility - just reconnect)
     */
    async reconnect(): Promise<void> {
        console.log('[NotebookChat] Reconnecting...');
        if (this.currentTicker) {
            await this.connect(this.currentTicker, undefined);
        }
    }

    /**
     * Connect (create session) using notebook API.
     */
    async connect(
        ticker: string,
        valuationId?: string,
        customConfig?: Partial<SocketConfig>
    ): Promise<void> {
        if (customConfig) {
            this.config = { ...this.config, ...customConfig };
        }

        // Update state
        this.currentTicker = ticker;
        this.isConnecting.set(true);
        this.updateConnectionStatus({ connecting: true, error: null });

        try {
            // Build request body
            const requestBody: any = {
                ticker: ticker,
            };

            if (valuationId) {
                requestBody.valuation_id = valuationId;
                console.log(`[NotebookChat] Starting session with valuation context: ${valuationId}`);
            }

            // Get auth token
            const authToken = await this.getAuthToken();
            const headers: HeadersInit = {
                'Content-Type': 'application/json',
            };

            if (authToken) {
                headers['Authorization'] = `Bearer ${authToken}`;
            } else {
                console.warn('[NotebookChat] No auth token available - session will be anonymous');
            }

            // Call notebook API instead of chat API
            const response = await fetch(`${this.config.url}/bullbeargpt/api/notebook/sessions`, {
                method: 'POST',
                headers,
                body: JSON.stringify(requestBody),
            });

            if (!response.ok) {
                throw new Error(`Failed to create session: ${response.statusText}`);
            }

            const data = await response.json();

            // Handle both wrapped and unwrapped responses
            const session = data.session || data;
            this.currentSessionId = session.id;

            if (session.user_id) {
                this.currentUserId = session.user_id;
            }

            // Check if this is a resumed existing session
            if (session.is_existing && session.cells && session.cells.length > 0) {
                console.log(`[NotebookChat] Resuming existing session with ${session.cells.length} cells`);
                // Emit existing cells for the UI to load
                this.existingCellsSubject.next(session.cells);
            } else {
                console.log(`[NotebookChat] Created new session: ${this.currentSessionId}`);

                // Add opening message only for NEW sessions (not resumed ones)
                if (session.opening_message) {
                    console.log(`[NotebookChat] Received opening message`);
                    this.chatService.addAiMessage(session.opening_message);
                    // Emit for cell-based UI
                    this.openingMessageSubject.next(session.opening_message);
                }
            }

            // Handle suggestions (for both new and existing sessions)
            if (session.suggestions && Array.isArray(session.suggestions)) {
                console.log(`[NotebookChat] Received ${session.suggestions.length} suggestions`);
                this.currentSuggestions.set(session.suggestions);
                this.suggestionsSubject.next(session.suggestions);
            }

            // Mark as connected
            this.isConnected.set(true);
            this.isConnecting.set(false);
            this.updateConnectionStatus({
                connected: true,
                connecting: false,
                error: null,
                reconnectAttempt: 0,
            });

        } catch (error: any) {
            console.error('[NotebookChat] Failed to create session:', error);
            this.isConnecting.set(false);
            this.hasError.set(true);
            this.updateConnectionStatus({
                connected: false,
                connecting: false,
                error: 'Failed to create chat session',
            });
            throw error;
        }
    }

    /**
     * Send message using SSE streaming (notebook API).
     */
    async sendMessage(message: string, additionalContext?: any): Promise<void> {
        if (!this.currentSessionId) {
            const error = 'No active session';
            console.error(`[NotebookChat] ${error}`);
            this.errorSubject.next(error);
            this.chatService.addAiMessage('Error: No active session', {
                error: true,
                errorMessage: error,
            });
            return;
        }

        console.log('[NotebookChat] Sending message:', message, 'Session:', this.currentSessionId);

        // Add user message to UI
        this.chatService.addUserMessage(message);

        // Initialize streaming message
        this.currentStreamingMessageId = this.generateMessageId();
        this.streamingBuffer = '';

        // Add typing indicator
        this.chatService.addTypingIndicator();
        this.chatService.isLoading.set(true);

        try {
            // Get auth token
            const authToken = await this.getAuthToken();
            const headers: HeadersInit = {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
            };

            if (authToken) {
                headers['Authorization'] = `Bearer ${authToken}`;
            }

            // Abort previous request if any
            if (this.abortController) {
                this.abortController.abort();
            }
            this.abortController = new AbortController();

            // Call notebook messages endpoint with SSE
            const response = await fetch(
                `${this.config.url}/bullbeargpt/api/notebook/sessions/${this.currentSessionId}/messages`,
                {
                    method: 'POST',
                    headers,
                    body: JSON.stringify({ message, context: additionalContext }),
                    signal: this.abortController.signal,
                }
            );

            if (!response.ok) {
                throw new Error(`Failed to send message: ${response.statusText}`);
            }

            // Process SSE stream
            await this.processSSEStream(response);

        } catch (error: any) {
            if (error.name === 'AbortError') {
                console.log('[NotebookChat] Request aborted');
                return;
            }
            console.error('[NotebookChat] Send error:', error);
            this.chatService.removeTypingIndicator();
            this.chatService.isLoading.set(false);
            this.errorSubject.next(error.message || 'Failed to send message');
            this.chatService.addAiMessage(`Error: ${error.message}`, { error: true });
        }
    }

    /**
     * Process SSE stream (similar to notebook.service.ts)
     */
    private async processSSEStream(response: Response): Promise<void> {
        const reader = response.body?.getReader();
        if (!reader) {
            throw new Error('No response body');
        }

        const decoder = new TextDecoder();
        let buffer = '';
        let currentEventType = ''; // Track the event type from "event:" line

        try {
            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('event: ')) {
                        // Store event type for when we process the data line
                        currentEventType = line.substring(7).trim();
                        continue;
                    }

                    if (line.startsWith('data: ')) {
                        const data = line.substring(6).trim();
                        if (data) {
                            try {
                                const event = JSON.parse(data);
                                // Merge the event type into the data object
                                event.type = currentEventType || event.type;
                                this.handleSSEEvent(event);
                            } catch (e) {
                                console.error('[NotebookChat] Failed to parse SSE data:', e);
                            }
                        }
                        // Reset event type after processing
                        currentEventType = '';
                    }
                }
            }
        } finally {
            reader.releaseLock();
        }
    }

    /**
     * Handle SSE events (maps cells to chat messages)
     */
    private handleSSEEvent(event: any): void {
        if (event.type === 'stream' && event.chunk) {
            // Streaming chunk
            this.streamingBuffer += event.chunk;

            // Render markdown
            const renderedContent = this.enhanceMessageWithQuestions(
                this.markdownRenderer.render(this.streamingBuffer) as string
            );

            // Update UI
            const messages = this.chatService.getMessages();
            const typingMessageIndex = messages.findIndex((m) => m.isTyping);

            if (typingMessageIndex >= 0) {
                // First chunk
                this.chatService.removeTypingIndicator();
                const msg = this.chatService.addAiMessage(renderedContent as string);
                this.currentStreamingMessageId = msg.id;
            } else if (this.currentStreamingMessageId) {
                // Subsequent chunks
                this.chatService.updateMessage(this.currentStreamingMessageId, {
                    content: renderedContent as string,
                });
            }

            // Emit chunk event
            this.messageChunkSubject.next({
                content: event.chunk,
                isComplete: false,
                messageId: this.currentStreamingMessageId || '',
            });

        } else if (event.type === 'cell_complete' || event.type === 'complete' || event.type === 'done') {
            // Streaming complete
            console.log('[NotebookChat] Message streaming complete');
            this.chatService.removeTypingIndicator();
            this.chatService.isLoading.set(false);

            // Reset streaming state
            this.streamingBuffer = '';
            this.currentStreamingMessageId = null;

            // Emit completion
            this.completionSubject.next();

            // Save session
            const session = this.chatService.getCurrentSession();
            if (session) {
                this.chatService.saveSession(session);
            }

        } else if (event.type === 'error') {
            // Error event
            console.error('[NotebookChat] Stream error:', event.error);
            this.chatService.removeTypingIndicator();
            this.chatService.isLoading.set(false);
            this.errorSubject.next(event.error || 'Unknown error');
            this.chatService.addAiMessage(`Error: ${event.error}`, { error: true });
        }
    }

    /**
     * Enhance message content with question highlighting
     */
    private enhanceMessageWithQuestions(content: string): string {
        const questionRegex = /([^.!]*\?[\s]*)$/;
        return content.replace(questionRegex, '<span class="question-highlight">$1</span>');
    }

    /**
     * Generate unique message ID
     */
    private generateMessageId(): string {
        return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    /**
     * Disconnect (no-op for HTTP/SSE, added for compatibility)
     */
    disconnect(): void {
        console.log('[NotebookChat] Disconnecting...');

        // Abort any ongoing requests
        if (this.abortController) {
            this.abortController.abort();
            this.abortController = null;
        }

        this.isConnected.set(false);
        this.currentSessionId = null;
        this.updateConnectionStatus({ connected: false, connecting: false });
    }

    /**
     * Check if connected
     */
    isSocketConnected(): boolean {
        return this.isConnected();
    }

    /**
     * Get connection status
     */
    getConnectionStatus(): ConnectionStatus {
        return this.connectionStatusSubject.value;
    }

    /**
     * Update connection status
     */
    private updateConnectionStatus(updates: Partial<ConnectionStatus>): void {
        const current = this.connectionStatusSubject.value;
        this.connectionStatusSubject.next({ ...current, ...updates });
    }

    // ============================================
    // Backward Compatibility Methods
    // ============================================

    /**
     * Get valuation update approval requests (for compatibility)
     */
    onValuationUpdateApprovalRequest(): Subject<any> {
        return this.valuationUpdateApprovalRequestSubject;
    }

    /**
     * Save hypothesis (HTTP POST to notebook API)
     */
    async saveHypothesis(hypothesisId: string): Promise<void> {
        console.log(`[NotebookChat] Saving hypothesis: ${hypothesisId}`);

        if (!this.currentSessionId) {
            console.error('[NotebookChat] No active session');
            return;
        }

        try {
            const authToken = await this.getAuthToken();
            const headers: HeadersInit = {
                'Content-Type': 'application/json',
            };

            if (authToken) {
                headers['Authorization'] = `Bearer ${authToken}`;
            }

            const response = await fetch(
                `${this.config.url}/bullbeargpt/api/notebook/sessions/${this.currentSessionId}/save-thesis`,
                {
                    method: 'POST',
                    headers,
                    body: JSON.stringify({ user_id: this.currentUserId || 'local' }),
                }
            );

            if (response.ok) {
                const result = await response.json();
                this.hypothesisSavedSubject.next({
                    success: true,
                    hypothesis_id: hypothesisId,
                    ...result
                });
            } else {
                throw new Error(`Failed to save hypothesis: ${response.statusText}`);
            }
        } catch (error: any) {
            console.error('[NotebookChat] Error saving hypothesis:', error);
            this.errorSubject.next(error.message);
        }
    }

    /**
     * Update notebook cell fields (notes/input) for current session.
     */
    async updateCell(cellId: string, updates: { user_notes?: string; user_input?: string }): Promise<any> {
        if (!this.currentSessionId) {
            throw new Error('No active session');
        }

        const authToken = await this.getAuthToken();
        const headers: HeadersInit = {
            'Content-Type': 'application/json',
        };
        if (authToken) {
            headers['Authorization'] = `Bearer ${authToken}`;
        }

        const response = await fetch(
            `${this.config.url}/bullbeargpt/api/notebook/sessions/${this.currentSessionId}/cells/${cellId}`,
            {
                method: 'PATCH',
                headers,
                body: JSON.stringify(updates),
            }
        );

        if (!response.ok) {
            throw new Error(`Failed to update cell (${response.status})`);
        }

        return response.json();
    }

    /**
     * Delete a notebook cell in current session.
     */
    async deleteCell(cellId: string): Promise<void> {
        if (!this.currentSessionId) {
            throw new Error('No active session');
        }

        const authToken = await this.getAuthToken();
        const headers: HeadersInit = {};
        if (authToken) {
            headers['Authorization'] = `Bearer ${authToken}`;
        }

        const response = await fetch(
            `${this.config.url}/bullbeargpt/api/notebook/sessions/${this.currentSessionId}/cells/${cellId}`,
            {
                method: 'DELETE',
                headers,
            }
        );

        if (!response.ok) {
            throw new Error(`Failed to delete cell (${response.status})`);
        }
    }

    /**
     * Approve valuation update (HTTP POST to notebook API)
     */
    async approveValuationUpdate(updateId: string): Promise<void> {
        console.log(`[NotebookChat] Approving valuation update: ${updateId}`);
        // TODO: Implement via HTTP POST to notebook API when available
        this.errorSubject.next('Valuation update approval not yet implemented');
    }

    /**
     * Deny valuation update (HTTP POST to notebook API)
     */
    async denyValuationUpdate(updateId: string): Promise<void> {
        console.log(`[NotebookChat] Denying valuation update: ${updateId}`);
        // TODO: Implement via HTTP POST to notebook API when available
        this.errorSubject.next('Valuation update denial not yet implemented');
    }

    approveTool(toolId: string, approved: boolean): void {
        console.warn('[NotebookChat] approveTool not fully implemented for SSE');
        // TODO: Implement via HTTP POST to notebook API
    }

    approveHypothesis(hypothesisId: string): void {
        console.warn('[NotebookChat] approveHypothesis not fully implemented for SSE');
        // TODO: Implement via HTTP POST to notebook API
    }

    reviseHypothesis(hypothesisId: string): void {
        console.warn('[NotebookChat] reviseHypothesis not fully implemented for SSE');
        // TODO: Implement via HTTP POST to notebook API
    }

    formatToolName(toolName: string): string {
        return toolName
            .split('_')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' ');
    }
}
