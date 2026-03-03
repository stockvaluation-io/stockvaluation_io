import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject, BehaviorSubject, from, of } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';
import {
    Cell,
    AnalysisSession,
    Thesis,
    Scenario,
    GroupedTheses,
    SessionResponse,
    ThesesListResponse,
    ThesisResponse,
    CellResponse,
    ScenarioResponse,
    SSEEvent,
    NotebookTab,
    DCFOverrides,
    DCFRecalcResult,
    DCFSnapshot,
    DCFSnapshotsResponse,
    ThesisPreview,
    ThesisPreviewResponse,
    ThesisSaveRequest,
    ThesisSaveResponse,
} from './cell.models';

/**
 * Service for notebook operations - session management, cell actions, thesis persistence.
 */
@Injectable({
    providedIn: 'root'
})
export class NotebookService {
    private readonly baseUrl = '/bullbeargpt/api/notebook';

    // Current session state
    private _currentSession = signal<AnalysisSession | null>(null);
    private _cells = signal<Cell[]>([]);
    private _scenarios = signal<Scenario[]>([]);
    private _isLoading = signal(false);
    private _isStreaming = signal(false);
    private _streamingCellId = signal<string | null>(null);

    // Thesis state
    private _groupedTheses = signal<GroupedTheses>({});

    // Tab state
    private _tabs = signal<NotebookTab[]>([]);
    private _activeTabId = signal<string | null>(null);

    // Public computed signals
    readonly currentSession = computed(() => this._currentSession());
    readonly cells = computed(() => this._cells());
    readonly scenarios = computed(() => this._scenarios());
    readonly isLoading = computed(() => this._isLoading());
    readonly isStreaming = computed(() => this._isStreaming());
    readonly streamingCellId = computed(() => this._streamingCellId());
    readonly groupedTheses = computed(() => this._groupedTheses());
    readonly tabs = computed(() => this._tabs());
    readonly activeTabId = computed(() => this._activeTabId());

    // Event subjects
    private cellUpdated$ = new Subject<Cell>();
    private streamChunk$ = new Subject<{ cellId: string; chunk: string }>();

    constructor(private http: HttpClient) { }

    // ============================================
    // Session Operations
    // ============================================

    /**
     * Create a new analysis session.
     */
    createSession(ticker: string, valuationId?: string): Observable<AnalysisSession> {
        this._isLoading.set(true);

        return this.http.post<SessionResponse | AnalysisSession>(`${this.baseUrl}/sessions`, {
            ticker,
            valuation_id: valuationId,
        }).pipe(
            map(response => {
                // Handle both wrapped {session: ...} and direct session response
                const session = (response as SessionResponse).session || response as AnalysisSession;
                this._currentSession.set(session);
                this._cells.set(session.cells || []);
                this._scenarios.set(session.scenarios || []);

                // Open tab for this session
                this.openSessionTab(session);

                return session;
            }),
            tap(() => this._isLoading.set(false)),
            catchError(error => {
                this._isLoading.set(false);
                console.error('Failed to create session:', error);
                throw error;
            })
        );
    }

    /**
     * Load an existing session.
     */
    loadSession(sessionId: string): Observable<AnalysisSession> {
        this._isLoading.set(true);

        return this.http.get<SessionResponse | AnalysisSession>(`${this.baseUrl}/sessions/${sessionId}`).pipe(
            map(response => {
                // Handle both wrapped {session: ...} and direct session response
                const session = (response as SessionResponse).session || response as AnalysisSession;
                this._currentSession.set(session);
                this._cells.set(session.cells || []);
                this._scenarios.set(session.scenarios || []);
                return session;
            }),
            tap(() => this._isLoading.set(false)),
            catchError(error => {
                this._isLoading.set(false);
                console.error('Failed to load session:', error);
                throw error;
            })
        );
    }

    /**
     * Send a message to the session (with SSE streaming).
     */
    sendMessage(message: string): void {
        const session = this._currentSession();
        if (!session) {
            console.error('No session loaded');
            return;
        }

        // Use POST with SSE streaming via fetch API
        this.streamMessage(session.id, message);
    }

    /**
     * Stream message using fetch API for POST with SSE.
     */
    private async streamMessage(sessionId: string, message: string): Promise<void> {
        this._isStreaming.set(true);

        try {
            const response = await fetch(`${this.baseUrl}/sessions/${sessionId}/messages`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                },
                body: JSON.stringify({ message }),
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const reader = response.body?.getReader();
            if (!reader) {
                throw new Error('No response body');
            }

            const decoder = new TextDecoder();
            let buffer = '';
            let currentEventType = ''; // Track event type from "event:" line

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('event: ')) {
                        // Store event type for the next data line
                        currentEventType = line.substring(7).trim();
                        continue;
                    }
                    if (line.startsWith('data: ')) {
                        const data = line.substring(6).trim();
                        if (data) {
                            try {
                                const event = JSON.parse(data) as SSEEvent;
                                // Merge the event type into the data object
                                (event as any).type = currentEventType || event.type;
                                this.handleSSEEvent(event);
                            } catch (e) {
                                console.error('Failed to parse SSE data:', e);
                            }
                        }
                        // Reset event type after processing
                        currentEventType = '';
                    }
                }
            }
        } catch (error) {
            console.error('Stream error:', error);
        } finally {
            this._isStreaming.set(false);
            this._streamingCellId.set(null);
        }
    }

    /**
     * Handle SSE events.
     */
    private handleSSEEvent(event: SSEEvent): void {
        switch (event.type) {
            case 'cell_start':
                if (event.cell_id) {
                    this._streamingCellId.set(event.cell_id);
                    this._isStreaming.set(true);
                }
                break;

            case 'stream':
                // Stream events only have cell_id and chunk, not full cell
                if (event.cell_id && event.chunk) {
                    this.streamChunk$.next({ cellId: event.cell_id, chunk: event.chunk });
                    // Update the cell's AI output incrementally
                    this.appendToStreamingCell(event.cell_id, event.chunk);
                }
                break;

            case 'cell':
                if (event.cell) {
                    this.updateOrAddCell(event.cell);
                }
                break;

            case 'cell_complete':
                if (event.cell) {
                    this.updateOrAddCell(event.cell);
                    this.cellUpdated$.next(event.cell);
                }
                this._isStreaming.set(false);
                this._streamingCellId.set(null);
                break;

            case 'done':
                this._isStreaming.set(false);
                this._streamingCellId.set(null);
                break;
        }
    }

    /**
     * Append chunk to streaming cell's AI output.
     */
    private appendToStreamingCell(cellId: string, chunk: string): void {
        const cells = this._cells();
        const existingIndex = cells.findIndex((c: Cell) => c.id === cellId);

        if (existingIndex >= 0) {
            const updated = [...cells];
            const cell = { ...updated[existingIndex] } as any;

            // Initialize or append to ai_output content
            if (!cell.ai_output) {
                cell.ai_output = { content: chunk };
            } else if (typeof cell.ai_output === 'object') {
                cell.ai_output = {
                    ...cell.ai_output,
                    content: ((cell.ai_output as any).content || '') + chunk
                };
            }

            updated[existingIndex] = cell;
            this._cells.set(updated);
        }
    }

    /**
     * Update existing cell or add new one.
     */
    private updateOrAddCell(cell: Cell): void {
        const cells = this._cells();
        const existingIndex = cells.findIndex(c => c.id === cell.id);

        if (existingIndex >= 0) {
            const updated = [...cells];
            updated[existingIndex] = cell;
            this._cells.set(updated);
        } else {
            this._cells.set([...cells, cell]);
        }
    }

    // ============================================
    // Cell Operations
    // ============================================

    /**
     * Update cell input or notes.
     */
    updateCell(cellId: string, updates: { user_input?: string; user_notes?: string }): Observable<Cell> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        return this.http.patch<CellResponse | Cell>(
            `${this.baseUrl}/sessions/${session.id}/cells/${cellId}`,
            updates
        ).pipe(
            map(response => {
                const cell = (response as CellResponse).cell || (response as Cell);
                this.updateOrAddCell(cell);
                return cell;
            })
        );
    }

    /**
     * Regenerate AI output for a cell.
     */
    async regenerateCell(cellId: string): Promise<void> {
        const session = this._currentSession();
        if (!session) {
            console.error('No session loaded');
            return;
        }

        this._isStreaming.set(true);
        this._streamingCellId.set(cellId);

        try {
            const response = await fetch(
                `${this.baseUrl}/sessions/${session.id}/cells/${cellId}/regenerate`,
                {
                    method: 'POST',
                    headers: {
                        'Accept': 'text/event-stream',
                    },
                }
            );

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const reader = response.body?.getReader();
            if (!reader) {
                throw new Error('No response body');
            }

            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                    if (line.startsWith('data: ')) {
                        const data = line.substring(6).trim();
                        if (data) {
                            try {
                                const event = JSON.parse(data) as SSEEvent;
                                this.handleSSEEvent(event);
                            } catch (e) {
                                console.error('Failed to parse SSE data:', e);
                            }
                        }
                    }
                }
            }
        } catch (error) {
            console.error('Regenerate error:', error);
        } finally {
            this._isStreaming.set(false);
            this._streamingCellId.set(null);
        }
    }

    /**
     * Delete a cell.
     */
    deleteCell(cellId: string): Observable<void> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        return this.http.delete<{ success: boolean }>(
            `${this.baseUrl}/sessions/${session.id}/cells/${cellId}`
        ).pipe(
            map(() => {
                // Remove cell and re-sequence
                const cells = this._cells();
                const deletedCell = cells.find(c => c.id === cellId);
                if (deletedCell) {
                    const remaining = cells.filter(c => c.id !== cellId);
                    const resequenced = remaining.map(c => {
                        if (c.sequence_number > deletedCell.sequence_number) {
                            return { ...c, sequence_number: c.sequence_number - 1 };
                        }
                        return c;
                    });
                    this._cells.set(resequenced);
                }
            })
        );
    }

    /**
     * Fork a cell to a new scenario.
     */
    forkCellToScenario(cellId: string, name: string, description?: string): Observable<Scenario> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        return this.http.post<ScenarioResponse>(
            `${this.baseUrl}/sessions/${session.id}/cells/${cellId}/fork-scenario`,
            { name, description }
        ).pipe(
            map(response => {
                this._scenarios.set([...this._scenarios(), response.scenario]);
                return response.scenario;
            })
        );
    }

    // ============================================
    // DCF Operations
    // ============================================

    /**
     * Recalculate DCF with parameter overrides.
     * Returns new snapshot and comparison with old values.
     */
    recalculateDcf(overrides: DCFOverrides): Observable<DCFRecalcResult> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        this._isLoading.set(true);

        return this.http.post<DCFRecalcResult>(
            `${this.baseUrl}/sessions/${session.id}/recalculate-dcf`,
            { overrides, save_snapshot: true }
        ).pipe(
            tap(() => this._isLoading.set(false)),
            catchError(error => {
                this._isLoading.set(false);
                console.error('Failed to recalculate DCF:', error);
                throw error;
            })
        );
    }

    /**
     * Get all DCF snapshots for current session.
     */
    getDcfSnapshots(): Observable<DCFSnapshot[]> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        return this.http.get<DCFSnapshotsResponse>(
            `${this.baseUrl}/sessions/${session.id}/dcf-snapshots`
        ).pipe(
            map(response => response.snapshots)
        );
    }

    // ============================================
    // Thesis Operations
    // ============================================

    /**
     * Generate thesis preview (non-streaming, full response).
     */
    generateThesisPreview(): Observable<ThesisPreview> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        this._isLoading.set(true);

        return this.http.post<ThesisPreviewResponse>(
            `${this.baseUrl}/sessions/${session.id}/generate-thesis`,
            {}
        ).pipe(
            map(response => response.preview),
            tap(() => this._isLoading.set(false)),
            catchError(error => {
                this._isLoading.set(false);
                console.error('Failed to generate thesis preview:', error);
                throw error;
            })
        );
    }

    /**
     * Generate thesis preview with SSE streaming.
     * Returns a Subject that emits chunks as they arrive, then completes with the full response.
     */
    generateThesisPreviewStream(): { chunks$: Subject<string>, complete$: Promise<string> } {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        const chunks$ = new Subject<string>();
        this._isLoading.set(true);

        const complete$ = new Promise<string>((resolve, reject) => {
            fetch(`${this.baseUrl}/sessions/${session.id}/generate-thesis-stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'text/event-stream',
                },
                credentials: 'include',
            }).then(async response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }

                const reader = response.body?.getReader();
                if (!reader) {
                    throw new Error('No response body');
                }

                const decoder = new TextDecoder();
                let fullResponse = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    const text = decoder.decode(value, { stream: true });
                    const lines = text.split('\n');

                    for (const line of lines) {
                        if (line.startsWith('data: ')) {
                            try {
                                const data = JSON.parse(line.slice(6));
                                if (data.chunk) {
                                    fullResponse += data.chunk;
                                    chunks$.next(data.chunk);
                                } else if (data.full_response) {
                                    fullResponse = data.full_response;
                                } else if (data.error) {
                                    throw new Error(data.error);
                                }
                            } catch (e) {
                                // Skip parse errors for incomplete lines
                            }
                        }
                    }
                }

                this._isLoading.set(false);
                chunks$.complete();
                resolve(fullResponse);
            }).catch(error => {
                this._isLoading.set(false);
                chunks$.error(error);
                reject(error);
                console.error('Thesis streaming error:', error);
            });
        });

        return { chunks$, complete$ };
    }

    /**
     * Save current session as thesis (with optional overrides).
     */
    saveThesis(request?: ThesisSaveRequest): Observable<Thesis> {
        const session = this._currentSession();
        if (!session) {
            throw new Error('No session loaded');
        }

        this._isLoading.set(true);

        return this.http.post<ThesisSaveResponse>(
            `${this.baseUrl}/sessions/${session.id}/save-thesis`,
            request || {}
        ).pipe(
            map(response => {
                // Refresh theses list
                this.loadTheses().subscribe();
                this._isLoading.set(false);
                return response.thesis;
            }),
            catchError(error => {
                this._isLoading.set(false);
                console.error('Failed to save thesis:', error);
                throw error;
            })
        );
    }

    /**
     * Load all theses (grouped by company/date).
     */
    loadTheses(): Observable<GroupedTheses> {
        return this.http.get<ThesesListResponse>(`${this.baseUrl}/theses`).pipe(
            map(response => {
                this._groupedTheses.set(response.grouped);
                return response.grouped;
            })
        );
    }

    /**
     * Load a specific thesis.
     */
    loadThesis(thesisId: string): Observable<Thesis> {
        return this.http.get<ThesisResponse>(`${this.baseUrl}/theses/${thesisId}`).pipe(
            map(response => {
                // Open tab for this thesis
                this.openThesisTab(response.thesis);
                return response.thesis;
            })
        );
    }

    // ============================================
    // Tab Management
    // ============================================

    /**
     * Open a tab for a session.
     */
    private openSessionTab(session: AnalysisSession): void {
        const tabs = this._tabs();
        const existingTab = tabs.find(t => t.sessionId === session.id && t.type === 'session');

        if (existingTab) {
            this.switchTab(existingTab.id);
            return;
        }

        const newTab: NotebookTab = {
            id: `session-${session.id}`,
            type: 'session',
            title: session.title || `${session.ticker} Analysis`,
            sessionId: session.id,
            isActive: true,
            isDirty: false,
        };

        const updatedTabs = tabs.map(t => ({ ...t, isActive: false }));
        updatedTabs.push(newTab);

        this._tabs.set(updatedTabs);
        this._activeTabId.set(newTab.id);
    }

    /**
     * Open a tab for a thesis.
     */
    private openThesisTab(thesis: Thesis): void {
        const tabs = this._tabs();
        const existingTab = tabs.find(t => t.thesisId === thesis.id && t.type === 'thesis');

        if (existingTab) {
            this.switchTab(existingTab.id);
            return;
        }

        const newTab: NotebookTab = {
            id: `thesis-${thesis.id}`,
            type: 'thesis',
            title: thesis.title,
            thesisId: thesis.id,
            isActive: true,
        };

        const updatedTabs = tabs.map(t => ({ ...t, isActive: false }));
        updatedTabs.push(newTab);

        this._tabs.set(updatedTabs);
        this._activeTabId.set(newTab.id);
    }

    /**
     * Switch to a tab.
     */
    switchTab(tabId: string): void {
        const tabs = this._tabs();
        this._tabs.set(tabs.map(t => ({ ...t, isActive: t.id === tabId })));
        this._activeTabId.set(tabId);

        // Load session/thesis data for the tab
        const tab = tabs.find(t => t.id === tabId);
        if (tab?.type === 'session' && tab.sessionId) {
            this.loadSession(tab.sessionId).subscribe();
        }
    }

    /**
     * Close a tab.
     */
    closeTab(tabId: string): void {
        const tabs = this._tabs();
        const tabIndex = tabs.findIndex(t => t.id === tabId);
        if (tabIndex < 0) return;

        const closingTab = tabs[tabIndex];
        const remainingTabs = tabs.filter(t => t.id !== tabId);

        if (closingTab.isActive && remainingTabs.length > 0) {
            // Activate previous or last tab
            const newActiveIndex = Math.min(tabIndex, remainingTabs.length - 1);
            remainingTabs[newActiveIndex].isActive = true;
            this._activeTabId.set(remainingTabs[newActiveIndex].id);

            // Load the newly active tab's data
            const activeTab = remainingTabs[newActiveIndex];
            if (activeTab.type === 'session' && activeTab.sessionId) {
                this.loadSession(activeTab.sessionId).subscribe();
            }
        } else if (remainingTabs.length === 0) {
            this._activeTabId.set(null);
            this._currentSession.set(null);
            this._cells.set([]);
        }

        this._tabs.set(remainingTabs);
    }

    /**
     * Set tab dirty state.
     */
    setTabDirty(tabId: string, isDirty: boolean): void {
        const tabs = this._tabs();
        this._tabs.set(tabs.map(t => t.id === tabId ? { ...t, isDirty } : t));
    }

    // ============================================
    // Event Observables
    // ============================================

    /**
     * Observable for cell updates.
     */
    onCellUpdated(): Observable<Cell> {
        return this.cellUpdated$.asObservable();
    }

    /**
     * Observable for streaming chunks.
     */
    onStreamChunk(): Observable<{ cellId: string; chunk: string }> {
        return this.streamChunk$.asObservable();
    }
}
