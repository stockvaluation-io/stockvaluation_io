"""
NotebookService - Manages notebook sessions and cells via local SQLite.
Local-first: no Supabase dependency.
"""
import json
import logging
import os
import sqlite3
import uuid
from contextlib import contextmanager
from datetime import datetime
from typing import Any, Dict, List, Optional

from models.notebook_cell import NotebookCell
from models.notebook_session import NotebookSession

logger = logging.getLogger(__name__)


def _get_db_path() -> str:
    local_data_dir = os.getenv('LOCAL_DATA_DIR', 'local_data')
    os.makedirs(local_data_dir, exist_ok=True)
    return os.path.join(local_data_dir, 'bullbeargpt.db')


@contextmanager
def _get_conn():
    """Context manager for SQLite connection with WAL mode for concurrency."""
    db_path = _get_db_path()
    conn = sqlite3.connect(db_path, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def _init_db():
    """Create tables if they don't exist."""
    with _get_conn() as conn:
        conn.executescript("""
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                ticker TEXT,
                company_name TEXT,
                user_id TEXT,
                valuation_id TEXT,
                currency TEXT,
                base_analysis_json TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS cells (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                cell_type TEXT NOT NULL DEFAULT 'reasoning',
                author_type TEXT,
                user_input TEXT,
                ai_output TEXT,
                user_notes TEXT,
                content TEXT,
                execution_time_ms INTEGER,
                created_at TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS scenarios (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                name TEXT NOT NULL,
                scenario_type TEXT,
                description TEXT,
                cell_id TEXT,
                dcf_snapshot_id TEXT,
                probability REAL,
                fair_value REAL,
                assumptions_summary TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS theses (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                ticker TEXT NOT NULL,
                company_name TEXT,
                title TEXT NOT NULL,
                summary TEXT,
                user_id TEXT,
                cells_snapshot TEXT NOT NULL,
                scenarios_snapshot TEXT,
                dcf_snapshot TEXT,
                valuation_id TEXT,
                created_at TEXT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_cells_session_id ON cells(session_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_ticker ON sessions(ticker);
            CREATE INDEX IF NOT EXISTS idx_scenarios_session_id ON scenarios(session_id);
            CREATE INDEX IF NOT EXISTS idx_theses_user_id ON theses(user_id);
            CREATE INDEX IF NOT EXISTS idx_theses_ticker ON theses(ticker);
        """)

        # Lightweight schema migrations for existing local DB files.
        # Older local stacks may have these tables but without newer columns.
        def ensure_column(table: str, column_name: str, column_def: str) -> None:
            existing = {
                row["name"]
                for row in conn.execute(f"PRAGMA table_info({table})").fetchall()
            }
            if column_name not in existing:
                conn.execute(f"ALTER TABLE {table} ADD COLUMN {column_def}")

        ensure_column("sessions", "valuation_id", "valuation_id TEXT")
        ensure_column("sessions", "currency", "currency TEXT")
        ensure_column("sessions", "base_analysis_json", "base_analysis_json TEXT")

        ensure_column("cells", "sequence_number", "sequence_number INTEGER")
        ensure_column("cells", "cell_type", "cell_type TEXT DEFAULT 'reasoning'")
        ensure_column("cells", "author_type", "author_type TEXT")
        ensure_column("cells", "user_input", "user_input TEXT")
        ensure_column("cells", "ai_output", "ai_output TEXT")
        ensure_column("cells", "user_notes", "user_notes TEXT")
        ensure_column("cells", "content", "content TEXT")
        ensure_column("cells", "execution_time_ms", "execution_time_ms INTEGER")
        ensure_column("cells", "created_at", "created_at TEXT")

        ensure_column("scenarios", "scenario_type", "scenario_type TEXT")
        ensure_column("scenarios", "description", "description TEXT")
        ensure_column("scenarios", "cell_id", "cell_id TEXT")
        ensure_column("scenarios", "dcf_snapshot_id", "dcf_snapshot_id TEXT")
        ensure_column("scenarios", "probability", "probability REAL")
        ensure_column("scenarios", "fair_value", "fair_value REAL")
        ensure_column("scenarios", "assumptions_summary", "assumptions_summary TEXT")
        ensure_column("scenarios", "created_at", "created_at TEXT")
        ensure_column("scenarios", "updated_at", "updated_at TEXT")

        ensure_column("theses", "scenarios_snapshot", "scenarios_snapshot TEXT")
        ensure_column("theses", "dcf_snapshot", "dcf_snapshot TEXT")
        ensure_column("theses", "valuation_id", "valuation_id TEXT")

        # Enforce one thesis per notebook session for local-first behavior.
        # For older DBs that may contain duplicates, keep only the newest row.
        duplicate_sessions = conn.execute(
            """
            SELECT session_id
            FROM theses
            GROUP BY session_id
            HAVING COUNT(*) > 1
            """
        ).fetchall()
        for row in duplicate_sessions:
            session_id = row["session_id"]
            ids = conn.execute(
                """
                SELECT id
                FROM theses
                WHERE session_id = ?
                ORDER BY created_at DESC, rowid DESC
                """,
                (session_id,),
            ).fetchall()
            duplicate_ids = [thesis_row["id"] for thesis_row in ids[1:]]
            if duplicate_ids:
                placeholders = ",".join("?" for _ in duplicate_ids)
                conn.execute(
                    f"DELETE FROM theses WHERE id IN ({placeholders})",
                    duplicate_ids,
                )

        conn.execute(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_theses_session_id_unique
            ON theses(session_id)
            """
        )
    logger.info(f"SQLite DB initialized at {_get_db_path()}")


class NotebookService:
    """
    Service for managing notebook sessions and cells.
    Uses SQLite for local persistence.
    """

    def __init__(self):
        _init_db()

    # ===== SESSION OPERATIONS =====

    def create_session(
        self,
        ticker: str,
        company_name: Optional[str] = None,
        user_id: Optional[str] = None,
        valuation_data: Optional[Dict[str, Any]] = None,
        currency: Optional[str] = None,
        valuation_id: Optional[str] = None,
        session_id: Optional[str] = None
    ) -> NotebookSession:
        """
        Create a new notebook session.

        Args:
            ticker: Stock ticker
            company_name: Company name
            user_id: User ID (defaults to 'local' in local-first mode)
            valuation_data: Initial DCF/valuation data
            currency: Currency code (SEK, EUR, etc.)
            valuation_id: Reference to valuation-agent generated base valuation
            session_id: Optional deterministic session ID (for persistence across reloads)

        Returns:
            Created NotebookSession
        """
        session = NotebookSession.create(
            ticker=ticker,
            company_name=company_name,
            user_id=user_id or 'local',
            valuation_data=valuation_data,
            currency=currency,
            valuation_id=valuation_id,
            session_id=session_id
        )

        now = datetime.utcnow().isoformat()
        with _get_conn() as conn:
            conn.execute(
                """INSERT OR IGNORE INTO sessions
                   (id, ticker, company_name, user_id, valuation_id, currency,
                    base_analysis_json, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                (
                    session.id,
                    ticker,
                    company_name,
                    user_id or 'local',
                    valuation_id,
                    currency,
                    json.dumps(valuation_data) if valuation_data else None,
                    now,
                    now,
                )
            )

        logger.info(f"Created notebook session {session.id} for {ticker}")
        return session

    def get_session(self, session_id: str) -> Optional[NotebookSession]:
        """Get a session by ID."""
        with _get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM sessions WHERE id = ?", (session_id,)
            ).fetchone()

        if not row:
            return None

        return self._row_to_session(dict(row))

    def list_sessions(
        self,
        user_id: Optional[str] = None,
        ticker: Optional[str] = None,
        limit: int = 50
    ) -> List[NotebookSession]:
        """List sessions with optional filters."""
        query = "SELECT * FROM sessions WHERE 1=1"
        params: list = []

        if user_id:
            query += " AND user_id = ?"
            params.append(user_id)
        if ticker:
            query += " AND ticker = ?"
            params.append(ticker)

        query += " ORDER BY updated_at DESC LIMIT ?"
        params.append(limit)

        with _get_conn() as conn:
            rows = conn.execute(query, params).fetchall()

        return [self._row_to_session(dict(r)) for r in rows]

    def update_session(self, session: NotebookSession) -> bool:
        """Update a session's metadata."""
        now = datetime.utcnow().isoformat()
        try:
            with _get_conn() as conn:
                conn.execute(
                    "UPDATE sessions SET updated_at = ?, company_name = ?, currency = ? WHERE id = ?",
                    (now, session.company_name, getattr(session, 'currency', None), session.id)
                )
            return True
        except Exception as e:
            logger.error(f"Failed to update session {session.id}: {e}")
            return False

    def delete_session(self, session_id: str) -> bool:
        """Delete a session (cascades to cells via FK)."""
        try:
            with _get_conn() as conn:
                conn.execute("DELETE FROM sessions WHERE id = ?", (session_id,))
            logger.info(f"Deleted notebook session {session_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to delete session {session_id}: {e}")
            return False

    def save_scenario(
        self,
        session_id: str,
        name: str,
        cell_id: Optional[str] = None,
        dcf_snapshot_id: Optional[str] = None,
        assumptions_summary: Optional[Dict[str, Any]] = None,
        description: Optional[str] = None,
        scenario_type: Optional[str] = None,
        probability: Optional[float] = None,
        fair_value: Optional[float] = None,
    ) -> Optional[Dict[str, Any]]:
        """Persist a scenario for a notebook session."""
        scenario_id = str(uuid.uuid4())
        now = datetime.utcnow().isoformat()
        payload = assumptions_summary or {}

        try:
            with _get_conn() as conn:
                conn.execute(
                    """
                    INSERT INTO scenarios
                    (id, session_id, name, scenario_type, description, cell_id, dcf_snapshot_id,
                     probability, fair_value, assumptions_summary, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        scenario_id,
                        session_id,
                        name or "Custom Scenario",
                        scenario_type,
                        description,
                        cell_id,
                        dcf_snapshot_id,
                        probability,
                        fair_value,
                        json.dumps(payload),
                        now,
                        now,
                    ),
                )
                conn.execute(
                    "UPDATE sessions SET updated_at = ? WHERE id = ?",
                    (now, session_id),
                )

            return {
                "id": scenario_id,
                "session_id": session_id,
                "name": name or "Custom Scenario",
                "scenario_type": scenario_type,
                "description": description,
                "cell_id": cell_id,
                "dcf_snapshot_id": dcf_snapshot_id,
                "probability": probability,
                "fair_value": fair_value,
                "assumptions_summary": payload,
                "created_at": now,
                "updated_at": now,
            }
        except Exception as e:
            logger.error(f"Failed to save scenario for session {session_id}: {e}")
            return None

    def get_scenarios(self, session_id: str) -> List[Dict[str, Any]]:
        """Get all saved scenarios for a session."""
        try:
            with _get_conn() as conn:
                rows = conn.execute(
                    """
                    SELECT id, session_id, name, scenario_type, description, cell_id, dcf_snapshot_id,
                           probability, fair_value, assumptions_summary, created_at, updated_at
                    FROM scenarios
                    WHERE session_id = ?
                    ORDER BY created_at ASC
                    """,
                    (session_id,),
                ).fetchall()

            scenarios: List[Dict[str, Any]] = []
            for row in rows:
                assumptions = {}
                if row["assumptions_summary"]:
                    try:
                        assumptions = json.loads(row["assumptions_summary"])
                    except Exception:
                        assumptions = {}
                scenarios.append({
                    "id": row["id"],
                    "session_id": row["session_id"],
                    "name": row["name"],
                    "scenario_type": row["scenario_type"],
                    "description": row["description"],
                    "cell_id": row["cell_id"],
                    "dcf_snapshot_id": row["dcf_snapshot_id"],
                    "probability": row["probability"],
                    "fair_value": row["fair_value"],
                    "assumptions_summary": assumptions,
                    "created_at": row["created_at"],
                    "updated_at": row["updated_at"],
                })
            return scenarios
        except Exception as e:
            logger.error(f"Failed to get scenarios for session {session_id}: {e}")
            return []

    # ===== CELL OPERATIONS =====

    def add_cell(self, cell: NotebookCell) -> NotebookCell:
        """Add a cell to a session."""
        now = datetime.utcnow().isoformat()
        cell_id = cell.id or str(uuid.uuid4())

        try:
            with _get_conn() as conn:
                conn.execute(
                    """INSERT OR IGNORE INTO cells
                       (id, session_id, sequence_number, cell_type, author_type,
                        user_input, ai_output, user_notes, content, execution_time_ms, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        cell_id,
                        cell.session_id,
                        cell.sequence_number if hasattr(cell, 'sequence_number') else 0,
                        getattr(cell, 'cell_type', 'reasoning'),
                        getattr(cell, 'author_type', None),
                        getattr(cell, 'user_input', None),
                        json.dumps(cell.ai_output) if getattr(cell, 'ai_output', None) else None,
                        getattr(cell, 'user_notes', None),
                        json.dumps(cell.content) if getattr(cell, 'content', None) else None,
                        getattr(cell, 'execution_time_ms', None),
                        now,
                    )
                )
                # Update session updated_at
                conn.execute(
                    "UPDATE sessions SET updated_at = ? WHERE id = ?",
                    (now, cell.session_id)
                )
            logger.debug(f"Added cell {cell_id} to session {cell.session_id}")
        except Exception as e:
            logger.error(f"Failed to add cell: {e}")

        return cell

    def get_cells(self, session_id: str) -> List[NotebookCell]:
        """Get all cells for a session, ordered by sequence."""
        with _get_conn() as conn:
            rows = conn.execute(
                "SELECT * FROM cells WHERE session_id = ? ORDER BY sequence_number",
                (session_id,)
            ).fetchall()

        return [self._row_to_cell(dict(r)) for r in rows]

    def get_cell(self, cell_id: str) -> Optional[NotebookCell]:
        """Get a single cell by ID."""
        with _get_conn() as conn:
            row = conn.execute(
                "SELECT * FROM cells WHERE id = ?", (cell_id,)
            ).fetchone()

        if not row:
            return None
        return self._row_to_cell(dict(row))

    def update_cell(self, cell: NotebookCell) -> bool:
        """Update a cell's content."""
        try:
            with _get_conn() as conn:
                conn.execute(
                    """UPDATE cells SET
                       user_input = ?, ai_output = ?, user_notes = ?, content = ?, execution_time_ms = ?
                       WHERE id = ?""",
                    (
                        getattr(cell, 'user_input', None),
                        json.dumps(cell.ai_output) if getattr(cell, 'ai_output', None) else None,
                        getattr(cell, 'user_notes', None),
                        json.dumps(cell.content) if getattr(cell, 'content', None) else None,
                        getattr(cell, 'execution_time_ms', None),
                        cell.id,
                    )
                )
            return True
        except Exception as e:
            logger.error(f"Failed to update cell {cell.id}: {e}")
            return False

    def delete_cell(self, cell_id: str) -> bool:
        """Delete a cell."""
        try:
            with _get_conn() as conn:
                cursor = conn.execute("DELETE FROM cells WHERE id = ?", (cell_id,))
                return cursor.rowcount > 0
        except Exception as e:
            logger.error(f"Failed to delete cell {cell_id}: {e}")
            return False

    def get_next_sequence_number(self, session_id: str) -> int:
        """Get the next sequence number for a session."""
        with _get_conn() as conn:
            row = conn.execute(
                "SELECT MAX(sequence_number) as max_seq FROM cells WHERE session_id = ?",
                (session_id,)
            ).fetchone()

        if row and row['max_seq'] is not None:
            return row['max_seq'] + 1
        return 0

    # ===== THESIS OPERATIONS =====

    def save_thesis(
        self,
        session_id: str,
        ticker: str,
        company_name: str,
        title: str,
        summary: str,
        cells_snapshot: List[Dict[str, Any]],
        dcf_snapshot: Dict[str, Any],
        user_id: Optional[str] = None,
        scenarios_snapshot: Optional[List[Dict[str, Any]]] = None,
        valuation_id: Optional[str] = None,
    ) -> Optional[Dict[str, Any]]:
        """Persist a thesis snapshot for a session.

        Local requirement: one thesis per notebook session.
        Re-saving the same session updates the existing thesis row.
        """
        now = datetime.utcnow().isoformat()
        owner = user_id or "local"

        try:
            with _get_conn() as conn:
                existing_rows = conn.execute(
                    """
                    SELECT id
                    FROM theses
                    WHERE session_id = ?
                    ORDER BY created_at DESC, rowid DESC
                    """,
                    (session_id,),
                ).fetchall()

                if existing_rows:
                    thesis_id = existing_rows[0]["id"]
                    conn.execute(
                        """
                        UPDATE theses
                        SET ticker = ?,
                            company_name = ?,
                            title = ?,
                            summary = ?,
                            user_id = ?,
                            cells_snapshot = ?,
                            scenarios_snapshot = ?,
                            dcf_snapshot = ?,
                            valuation_id = ?,
                            created_at = ?
                        WHERE id = ?
                        """,
                        (
                            ticker,
                            company_name,
                            title,
                            summary,
                            owner,
                            json.dumps(cells_snapshot or []),
                            json.dumps(scenarios_snapshot or []),
                            json.dumps(dcf_snapshot or {}),
                            valuation_id,
                            now,
                            thesis_id,
                        ),
                    )

                    duplicate_ids = [row["id"] for row in existing_rows[1:]]
                    if duplicate_ids:
                        placeholders = ",".join("?" for _ in duplicate_ids)
                        conn.execute(
                            f"DELETE FROM theses WHERE id IN ({placeholders})",
                            duplicate_ids,
                        )
                else:
                    thesis_id = str(uuid.uuid4())
                    conn.execute(
                        """
                        INSERT INTO theses
                        (id, session_id, ticker, company_name, title, summary, user_id,
                         cells_snapshot, scenarios_snapshot, dcf_snapshot, valuation_id, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            thesis_id,
                            session_id,
                            ticker,
                            company_name,
                            title,
                            summary,
                            owner,
                            json.dumps(cells_snapshot or []),
                            json.dumps(scenarios_snapshot or []),
                            json.dumps(dcf_snapshot or {}),
                            valuation_id,
                            now,
                        ),
                    )

            return {
                "id": thesis_id,
                "session_id": session_id,
                "ticker": ticker,
                "company_name": company_name,
                "title": title,
                "summary": summary,
                "user_id": owner,
                "cells_snapshot": cells_snapshot or [],
                "scenarios_snapshot": scenarios_snapshot or [],
                "dcf_snapshot": dcf_snapshot or {},
                "valuation_id": valuation_id,
                "created_at": now,
            }
        except Exception as e:
            logger.error(f"Failed to save thesis for session {session_id}: {e}")
            return None

    def get_thesis(self, thesis_id: str) -> Optional[Dict[str, Any]]:
        """Fetch a thesis by ID."""
        try:
            with _get_conn() as conn:
                row = conn.execute(
                    """
                    SELECT id, session_id, ticker, company_name, title, summary, user_id,
                           cells_snapshot, scenarios_snapshot, dcf_snapshot, valuation_id, created_at
                    FROM theses WHERE id = ?
                    """,
                    (thesis_id,),
                ).fetchone()

            if not row:
                return None
            return self._row_to_thesis(dict(row))
        except Exception as e:
            logger.error(f"Failed to get thesis {thesis_id}: {e}")
            return None

    def list_user_theses(self, user_id: Optional[str], ticker: Optional[str] = None) -> List[Dict[str, Any]]:
        """List theses for a user with optional ticker filter."""
        query = """
            SELECT id, session_id, ticker, company_name, title, summary, user_id,
                   cells_snapshot, scenarios_snapshot, dcf_snapshot, valuation_id, created_at
            FROM theses
            WHERE 1=1
        """
        params: list = []

        if user_id:
            query += " AND user_id = ?"
            params.append(user_id)
        if ticker:
            query += " AND ticker = ?"
            params.append(ticker)

        query += " ORDER BY created_at DESC"

        try:
            with _get_conn() as conn:
                rows = conn.execute(query, params).fetchall()

            # Defensive dedupe for older local DBs that may still contain
            # historical duplicates created before session-level upsert behavior.
            theses: List[Dict[str, Any]] = []
            seen_session_ids = set()
            for row in rows:
                thesis = self._row_to_thesis(dict(row))
                row_session_id = thesis.get("session_id")
                if row_session_id and row_session_id in seen_session_ids:
                    continue
                if row_session_id:
                    seen_session_ids.add(row_session_id)
                theses.append(thesis)

            return theses
        except Exception as e:
            logger.error(f"Failed to list theses for user {user_id}: {e}")
            return []

    def get_grouped_theses(self, user_id: Optional[str]) -> Dict[str, Dict[str, List[Dict[str, Any]]]]:
        """Group theses by ticker, then by YYYY-MM period."""
        grouped: Dict[str, Dict[str, List[Dict[str, Any]]]] = {}
        theses = self.list_user_theses(user_id)

        for thesis in theses:
            ticker = thesis.get("ticker") or "UNKNOWN"
            created_at = thesis.get("created_at") or ""
            month_key = created_at[:7] if len(created_at) >= 7 else "unknown"

            if ticker not in grouped:
                grouped[ticker] = {}
            if month_key not in grouped[ticker]:
                grouped[ticker][month_key] = []
            grouped[ticker][month_key].append(thesis)

        return grouped

    # ===== HELPERS =====

    def _row_to_session(self, row: dict) -> NotebookSession:
        """Convert a DB row to a NotebookSession."""
        session = NotebookSession.create(
            ticker=row.get('ticker', ''),
            company_name=row.get('company_name'),
            user_id=row.get('user_id', 'local'),
            valuation_data=json.loads(row['base_analysis_json']) if row.get('base_analysis_json') else None,
            currency=row.get('currency'),
            valuation_id=row.get('valuation_id'),
            session_id=row['id']
        )
        # Restore timestamps from DB
        if hasattr(session, 'created_at') and row.get('created_at'):
            try:
                session.created_at = datetime.fromisoformat(row['created_at'])
            except Exception:
                pass
        if hasattr(session, 'updated_at') and row.get('updated_at'):
            try:
                session.updated_at = datetime.fromisoformat(row['updated_at'])
            except Exception:
                pass
        return session

    def _row_to_cell(self, row: dict) -> NotebookCell:
        """Convert a DB row to a NotebookCell."""
        ai_output = None
        if row.get('ai_output'):
            try:
                ai_output = json.loads(row['ai_output'])
            except Exception:
                ai_output = {'content': row['ai_output']}

        content = {}
        if row.get('content'):
            try:
                content = json.loads(row['content'])
            except Exception:
                content = {}

        # NotebookCell is a dataclass — use it directly with keyword args
        cell = NotebookCell(
            id=row['id'],
            session_id=row['session_id'],
            sequence_number=row.get('sequence_number', 0),
            cell_type=row.get('cell_type', 'reasoning'),
            author_type=row.get('author_type', 'ai'),
            user_input=row.get('user_input'),
            ai_output=ai_output,
            user_notes=row.get('user_notes'),
            content=content if content else {},
            execution_time_ms=row.get('execution_time_ms'),
        )
        if row.get('created_at'):
            try:
                cell.created_at = datetime.fromisoformat(row['created_at'])
            except Exception:
                pass
        return cell

    def _row_to_thesis(self, row: dict) -> Dict[str, Any]:
        """Convert a thesis DB row to API shape."""
        def _loads(payload: Optional[str], default: Any) -> Any:
            if not payload:
                return default
            try:
                return json.loads(payload)
            except Exception:
                return default

        return {
            "id": row.get("id"),
            "session_id": row.get("session_id"),
            "ticker": row.get("ticker"),
            "company_name": row.get("company_name"),
            "title": row.get("title"),
            "summary": row.get("summary"),
            "user_id": row.get("user_id"),
            "cells_snapshot": _loads(row.get("cells_snapshot"), []),
            "scenarios_snapshot": _loads(row.get("scenarios_snapshot"), []),
            "dcf_snapshot": _loads(row.get("dcf_snapshot"), {}),
            "valuation_id": row.get("valuation_id"),
            "created_at": row.get("created_at"),
        }


# Singleton instance
_notebook_service: Optional[NotebookService] = None


def get_notebook_service() -> NotebookService:
    """Get or create notebook service singleton."""
    global _notebook_service
    if _notebook_service is None:
        _notebook_service = NotebookService()
    return _notebook_service
