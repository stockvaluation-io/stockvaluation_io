"""
Local valuation persistence service.

Stores canonical valuation payloads as JSON blobs and keeps queryable metadata in
SQLite for local-first usage (no Supabase/account-service dependency).
"""
import json
import logging
import os
import re
import sqlite3
import uuid
from datetime import date, datetime
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

logger = logging.getLogger(__name__)


class ValuationPersistenceService:
    """Persist and retrieve valuations using local SQLite + JSON blobs."""

    def __init__(self) -> None:
        self.data_dir = Path(os.getenv("LOCAL_DATA_DIR", "local_data"))
        self.db_path = self.data_dir / "app.db"
        self.valuations_dir = self.data_dir / "valuations"
        self.tables = self._resolve_table_names()
        self.indexes = self._resolve_index_names()

        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.valuations_dir.mkdir(parents=True, exist_ok=True)
        self._init_store()
        logger.info("Valuation persistence initialized (local SQLite + JSON)")

    def _conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _resolve_table_names(self) -> Dict[str, str]:
        table_prefix = os.getenv("LOCAL_DB_TABLE_PREFIX", "").strip()
        if table_prefix and not table_prefix.endswith("_"):
            table_prefix = f"{table_prefix}_"
        valuations_default = f"{table_prefix}valuations" if table_prefix else "valuations"
        return {
            "valuations": self._safe_identifier(
                os.getenv("VALUATIONS_TABLE_NAME", valuations_default).strip() or valuations_default,
                valuations_default,
            ),
        }

    def _resolve_index_names(self) -> Dict[str, str]:
        index_prefix = os.getenv("LOCAL_DB_INDEX_PREFIX", "").strip()
        if index_prefix and not index_prefix.endswith("_"):
            index_prefix = f"{index_prefix}_"
        ticker_date_default = (
            f"{index_prefix}idx_valuations_ticker_date"
            if index_prefix else "idx_valuations_ticker_date"
        )
        return {
            "valuations_ticker_date": self._safe_identifier(
                os.getenv("VALUATIONS_TICKER_DATE_INDEX_NAME", ticker_date_default).strip()
                or ticker_date_default,
                ticker_date_default,
            ),
        }

    @staticmethod
    def _safe_identifier(candidate: str, fallback: str) -> str:
        if candidate and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", candidate):
            return candidate
        if candidate and candidate != fallback:
            logger.warning("Invalid SQLite identifier '%s'; using '%s'", candidate, fallback)
        return fallback

    def _init_store(self) -> None:
        valuations_table = self.tables["valuations"]

        with self._conn() as conn:
            conn.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {valuations_table} (
                    id TEXT PRIMARY KEY,
                    ticker TEXT NOT NULL,
                    company_name TEXT,
                    valuation_date TEXT NOT NULL,
                    fair_value REAL,
                    current_price REAL,
                    upside_percentage REAL,
                    blob_path TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(ticker, valuation_date)
                )
                """
            )
            conn.execute(
                f"CREATE INDEX IF NOT EXISTS {self.indexes['valuations_ticker_date']} "
                f"ON {valuations_table}(ticker, valuation_date)"
            )
            conn.commit()

    @staticmethod
    def _extract_metrics(valuation_data: Dict[str, Any]) -> Tuple[Optional[float], Optional[float], Optional[float]]:
        dcf_analysis = valuation_data.get("dcf_analysis") or (
            valuation_data.get("merged_result", {}) or {}
        ).get("dcf_analysis", {}) or {}
        financials = valuation_data.get("financials") or (
            valuation_data.get("merged_result", {}) or {}
        ).get("financials", {}) or {}

        fair_value = dcf_analysis.get("fair_value") or dcf_analysis.get("intrinsic_value")
        current_price = financials.get("current_price") or financials.get("stock_price")

        if fair_value is None or current_price is None:
            java_output = valuation_data.get("java_valuation_output") or {}
            company_dto = java_output.get("companyDTO") or java_output.get("company_dto") or {}
            if fair_value is None:
                fair_value = (
                    company_dto.get("estimatedValuePerShare")
                    or company_dto.get("estimated_value_per_share")
                )
            if current_price is None:
                current_price = company_dto.get("price")

        upside_percentage = None
        try:
            if fair_value is not None and current_price not in (None, 0):
                upside_percentage = ((float(fair_value) - float(current_price)) / float(current_price)) * 100
        except Exception:
            upside_percentage = None

        return fair_value, current_price, upside_percentage

    def _blob_path(self, valuation_id: str) -> Path:
        return self.valuations_dir / f"{valuation_id}.json"

    def _write_blob(self, valuation_id: str, payload: Dict[str, Any]) -> str:
        path = self._blob_path(valuation_id)
        with path.open("w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, default=str)
        return str(path)

    def _read_blob(self, blob_path: str) -> Dict[str, Any]:
        path = Path(blob_path)
        if not path.exists():
            logger.warning("Valuation blob missing: %s", blob_path)
            return {}
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)

    def _build_record(self, row: sqlite3.Row) -> Dict[str, Any]:
        return {
            "id": row["id"],
            "ticker": row["ticker"],
            "company_name": row["company_name"],
            "valuation_date": row["valuation_date"],
            "valuation_data": self._read_blob(row["blob_path"]),
            "fair_value": row["fair_value"],
            "current_price": row["current_price"],
            "upside_percentage": row["upside_percentage"],
            "created_at": row["created_at"],
            "updated_at": row["updated_at"],
        }

    def save_valuation(
        self,
        ticker: str,
        company_name: str,
        valuation_data: Dict[str, Any],
        valuation_date: Optional[date] = None,
    ) -> Optional[str]:
        """Save or update a valuation."""
        try:
            valuations_table = self.tables["valuations"]
            ticker = ticker.upper()
            valuation_date = valuation_date or date.today()
            fair_value, current_price, upside_percentage = self._extract_metrics(valuation_data)
            now = datetime.utcnow().isoformat()

            with self._conn() as conn:
                existing = conn.execute(
                    f"SELECT id, created_at FROM {valuations_table} WHERE ticker = ? AND valuation_date = ?",
                    (ticker, valuation_date.isoformat()),
                ).fetchone()

                valuation_id = str(existing["id"]) if existing else str(uuid.uuid4())
                created_at = str(existing["created_at"]) if existing else now
                blob_path = self._write_blob(valuation_id, valuation_data)

                conn.execute(
                    f"""
                    INSERT INTO {valuations_table} (
                        id, ticker, company_name, valuation_date,
                        fair_value, current_price, upside_percentage,
                        blob_path, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(ticker, valuation_date) DO UPDATE SET
                        company_name=excluded.company_name,
                        fair_value=excluded.fair_value,
                        current_price=excluded.current_price,
                        upside_percentage=excluded.upside_percentage,
                        blob_path=excluded.blob_path,
                        updated_at=excluded.updated_at
                    """,
                    (
                        valuation_id,
                        ticker,
                        company_name,
                        valuation_date.isoformat(),
                        fair_value,
                        current_price,
                        upside_percentage,
                        blob_path,
                        created_at,
                        now,
                    ),
                )

                conn.commit()

            logger.info("Saved local valuation for %s (%s)", ticker, valuation_id)
            return valuation_id
        except Exception as exc:
            logger.error("Error saving valuation: %s", exc, exc_info=True)
            return None

    def get_valuation_by_id(self, valuation_id: str) -> Optional[Dict[str, Any]]:
        try:
            valuations_table = self.tables["valuations"]
            with self._conn() as conn:
                row = conn.execute(f"SELECT * FROM {valuations_table} WHERE id = ?", (valuation_id,)).fetchone()
            return self._build_record(row) if row else None
        except Exception as exc:
            logger.error("Error retrieving valuation by ID: %s", exc)
            return None


valuation_service = ValuationPersistenceService()
