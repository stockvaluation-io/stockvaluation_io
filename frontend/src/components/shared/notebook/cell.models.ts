/**
 * Cell and session models for Jupyter-style notebook.
 * TypeScript interfaces matching the Python backend models.
 */

// ============================================
// Cell Types
// ============================================

export type CellType = 'reasoning' | 'calibration' | 'visualization' | 'computation' | 'system';
export type AuthorType = 'ai' | 'user' | 'system';

export interface CellContent {
    message?: string;
    type?: string;
    changes?: Record<string, number>;
    rationale?: string;
    dcf_summary?: DCFSummary;
    code?: string;  // For computation cells
    // Agentic response properties
    rewritten_query?: string;
    tool_results?: ToolResult[];
    code_execution?: CodeExecution;
}

export interface ToolResult {
    tool_name: string;
    status: 'pending' | 'running' | 'success' | 'error' | 'skipped';
    data?: Record<string, any>;
    error?: string;
    execution_time_ms?: number;
    metadata?: Record<string, any>;
}

export interface CodeExecution {
    code: string;
    result?: ToolResult;
}

export interface Cell {
    id: string;
    session_id: string;
    sequence_number: number;
    cell_type: CellType;
    // Unified cell structure
    user_input?: string;      // User's question/prompt (editable)
    ai_output?: CellContent;  // AI's response (regeneratable)
    user_notes?: string;      // User annotations
    // Legacy content field
    content?: CellContent;
    dcf_snapshot_id?: string;
    changed_parameters?: Record<string, number>;
    author_type: AuthorType;
    parent_cell_id?: string;
    created_at: string;
    execution_time_ms?: number;
    is_streaming?: boolean;
}

// ============================================
// DCF Types
// ============================================

export interface DCFSummary {
    fair_value: number;
    current_price: number;
    upside_pct: number;
    revenue_growth?: number[];
    operating_margin?: number[];
    wacc?: number[];
    terminal_growth?: number;
}

export interface DCFSnapshot {
    id: string;
    ticker: string;
    calculation_date: string;
    fair_value: number;
    current_price: number;
    upside_pct: number;
    created_at: string;
}

// ============================================
// Session Types
// ============================================

export interface AnalysisSession {
    id: string;
    ticker: string;
    company_name: string;
    user_id?: string;
    title: string;
    created_at: string;
    updated_at: string;
    is_public: boolean;
    parent_session_id?: string;
    base_analysis_json?: Record<string, any>;
    cells: Cell[];
    scenarios?: Scenario[];
}

// ============================================
// Scenario Types
// ============================================

export interface Scenario {
    id: string;
    session_id: string;
    name: string;
    description: string;
    cell_id: string;
    dcf_snapshot_id: string;
    created_at: string;
    is_active: boolean;
    scenario_type: 'base' | 'bull' | 'bear' | 'custom' | 'reference';
    is_predefined?: boolean;
    assumptions_summary?: {
        revenue_growth_avg?: number;
        operating_margin_avg?: number;
        wacc_avg?: number;
        terminal_growth?: number;
        fair_value?: number;
    };
    fair_value?: number;
}

// ============================================
// Thesis Types
// ============================================

export interface Thesis {
    id: string;
    session_id?: string;
    ticker: string;
    company_name: string;
    title: string;
    summary: string;
    user_id?: string;
    cells_snapshot: Cell[];
    scenarios_snapshot: Scenario[];
    dcf_snapshot: Record<string, any>;
    created_at: string;
}

export interface GroupedTheses {
    [ticker: string]: {
        [monthYear: string]: Thesis[];
    };
}

// ============================================
// Tab Types
// ============================================

export type TabType = 'session' | 'thesis';

export interface NotebookTab {
    id: string;
    type: TabType;
    title: string;
    sessionId?: string;
    thesisId?: string;
    isActive: boolean;
    isDirty?: boolean;
}

// ============================================
// Cell Action Types
// ============================================

export interface CellActionCallbacks {
    onDelete?: (cellId: string) => void;
    onAddNote?: (cellId: string) => void;
    onUpdateNotes?: (cellId: string, notes: string) => void;
}

// ============================================
// SSE Event Types
// ============================================

export interface SSEEvent {
    type: 'cell' | 'cell_start' | 'stream' | 'cell_complete' | 'regenerate_start' | 'done';
    cell?: Cell;
    cell_id?: string;
    chunk?: string;
    sequence_number?: number;
    execution_time_ms?: number;
}

// ============================================
// API Response Types
// ============================================

export interface APIResponse<T> {
    success: boolean;
    data?: T;
    error?: string;
}

export interface SessionResponse {
    success: boolean;
    session: AnalysisSession;
}

export interface ThesesListResponse {
    success: boolean;
    grouped: GroupedTheses;
    total: number;
}

export interface ThesisResponse {
    success: boolean;
    thesis: Thesis;
}

export interface CellResponse {
    success: boolean;
    cell: Cell;
}

export interface ScenarioResponse {
    success: boolean;
    scenario: Scenario;
}

// ============================================
// DCF Recalculation Types
// ============================================

export interface DCFOverrides {
    compoundAnnualGrowth2_5?: number;
    targetPreTaxOperatingMargin?: number;
    salesToCapitalYears1To5?: number;
    initialCostCapital?: number;
    terminalGrowthRate?: number;
}

export interface DCFComparison {
    old_fair_value: number;
    new_fair_value: number;
    change_pct: number;
    old_upside_pct: number;
    new_upside_pct: number;
}

export interface DCFRecalcResult {
    snapshot: DCFSnapshot;
    comparison: DCFComparison;
    ticker: string;
    success: boolean;
}

export interface DCFSnapshotsResponse {
    snapshots: DCFSnapshot[];
    count: number;
}

// ============================================
// Thesis Generation Types
// ============================================

export interface ThesisPreview {
    title: string;
    summary: string;
    conviction: number;
    key_assumptions: string[];
    risks: string[];
    fair_value: number;
    current_price: number;
    upside_pct: number;
    target_timeframe: string;
}

export interface ThesisPreviewResponse {
    preview: ThesisPreview;
    session_id: string;
    ticker: string;
}

export interface ThesisSaveRequest {
    title?: string;
    summary?: string;
    conviction?: number;
    key_assumptions?: string[];
    risks?: string[];
    target_timeframe?: string;
}

export interface ThesisSaveResponse {
    thesis: Thesis;
    success: boolean;
}
