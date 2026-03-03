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

            CREATE INDEX IF NOT EXISTS idx_cells_session_id ON cells(session_id);
            CREATE INDEX IF NOT EXISTS idx_sessions_ticker ON sessions(ticker);
            CREATE INDEX IF NOT EXISTS idx_scenarios_session_id ON scenarios(session_id);
        """)
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
                        user_input, ai_output, content, execution_time_ms, created_at)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    (
                        cell_id,
                        cell.session_id,
                        cell.sequence_number if hasattr(cell, 'sequence_number') else 0,
                        getattr(cell, 'cell_type', 'reasoning'),
                        getattr(cell, 'author_type', None),
                        getattr(cell, 'user_input', None),
                        json.dumps(cell.ai_output) if getattr(cell, 'ai_output', None) else None,
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
                       user_input = ?, ai_output = ?, content = ?, execution_time_ms = ?
                       WHERE id = ?""",
                    (
                        getattr(cell, 'user_input', None),
                        json.dumps(cell.ai_output) if getattr(cell, 'ai_output', None) else None,
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
                conn.execute("DELETE FROM cells WHERE id = ?", (cell_id,))
            return True
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
            content=content if content else {},
            execution_time_ms=row.get('execution_time_ms'),
        )
        if row.get('created_at'):
            try:
                cell.created_at = datetime.fromisoformat(row['created_at'])
            except Exception:
                pass
        return cell


# Singleton instance
_notebook_service: Optional[NotebookService] = None


def get_notebook_service() -> NotebookService:
    """Get or create notebook service singleton."""
    global _notebook_service
    if _notebook_service is None:
        _notebook_service = NotebookService()
    return _notebook_service
