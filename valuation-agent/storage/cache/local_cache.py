"""
Local SQLite-backed cache used by the valuation-agent runtime.

This replaces the old hosted Supabase cache implementation for local-first use.
"""
import json
import logging
import os
import re
import sqlite3
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, Optional

DEFAULT_MAX_SENTENCES = 3

TAVILY_QUERY_CACHE_TTL = {
    "earnings": 7 * 24,
    "news": 24,
    "macro": 12,
    "segments": 14 * 24,
}

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class CacheNamespace:
    key: str
    payload_type: str  # "json" | "text"
    default_ttl_hours: int


CACHE_NAMESPACES: Dict[str, CacheNamespace] = {
    "news_summary": CacheNamespace("narrative_cache", "json", 6),
    "earnings_summary": CacheNamespace("earnings_cache", "json", 7 * 24),
    "macro_news": CacheNamespace("macro_news_cache", "text", 12),
    "segments": CacheNamespace("segments_cache", "json", 365 * 24),
    "tavily_query": CacheNamespace("tavily_query_cache", "text", 24),
}


def _namespace(name: str) -> CacheNamespace:
    return CACHE_NAMESPACES[name]


def _expiry(ttl_hours: int) -> datetime:
    return datetime.utcnow() + timedelta(hours=ttl_hours)


def _safe_identifier(raw: str, fallback: str) -> str:
    value = (raw or "").strip()
    if value and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", value):
        return value
    if value:
        logger.warning("Invalid SQLite identifier '%s'; using '%s'", value, fallback)
    return fallback


class _LocalCacheStore:
    """Small SQLite cache with TTL support."""

    def __init__(self) -> None:
        data_dir = Path(os.getenv("LOCAL_DATA_DIR", "local_data"))
        self.cache_dir = data_dir / "cache"
        self.db_path = self.cache_dir / "cache.db"
        self.table_name = _safe_identifier(
            os.getenv("LOCAL_CACHE_TABLE_NAME", "cache_entries"),
            "cache_entries",
        )
        self.expiry_index_name = _safe_identifier(
            os.getenv("LOCAL_CACHE_EXPIRY_INDEX_NAME", f"idx_{self.table_name}_expiry"),
            f"idx_{self.table_name}_expiry",
        )
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self._init_store()

    def _conn(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_store(self) -> None:
        with self._conn() as conn:
            conn.execute("PRAGMA journal_mode=WAL")
            conn.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {self.table_name} (
                    namespace TEXT NOT NULL,
                    key1 TEXT NOT NULL,
                    key2 TEXT NOT NULL DEFAULT '',
                    payload_json TEXT,
                    payload_text TEXT,
                    expires_at TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(namespace, key1, key2)
                )
                """
            )
            conn.execute(
                f"CREATE INDEX IF NOT EXISTS {self.expiry_index_name} "
                f"ON {self.table_name}(namespace, expires_at)"
            )
            conn.commit()

    def _purge_expired(self, conn: sqlite3.Connection, namespace: Optional[str] = None) -> None:
        now = datetime.utcnow().isoformat()
        if namespace:
            conn.execute(
                f"DELETE FROM {self.table_name} WHERE namespace = ? AND expires_at <= ?",
                (namespace, now),
            )
            return
        conn.execute(f"DELETE FROM {self.table_name} WHERE expires_at <= ?", (now,))

    def upsert_json(
        self,
        namespace: str,
        key1: str,
        value: Any,
        expires_at: datetime,
        key2: str = "",
    ) -> None:
        now = datetime.utcnow().isoformat()
        payload = json.dumps(value, ensure_ascii=False, default=str)
        with self._conn() as conn:
            self._purge_expired(conn, namespace)
            conn.execute(
                f"""
                INSERT INTO {self.table_name} (
                    namespace, key1, key2, payload_json, payload_text, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)
                ON CONFLICT(namespace, key1, key2) DO UPDATE SET
                    payload_json=excluded.payload_json,
                    payload_text=NULL,
                    expires_at=excluded.expires_at,
                    updated_at=excluded.updated_at
                """,
                (
                    namespace,
                    key1,
                    key2,
                    payload,
                    expires_at.isoformat(),
                    now,
                    now,
                ),
            )
            conn.commit()

    def upsert_text(
        self,
        namespace: str,
        key1: str,
        value: str,
        expires_at: datetime,
        key2: str = "",
    ) -> None:
        now = datetime.utcnow().isoformat()
        with self._conn() as conn:
            self._purge_expired(conn, namespace)
            conn.execute(
                f"""
                INSERT INTO {self.table_name} (
                    namespace, key1, key2, payload_json, payload_text, expires_at, created_at, updated_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, ?)
                ON CONFLICT(namespace, key1, key2) DO UPDATE SET
                    payload_json=NULL,
                    payload_text=excluded.payload_text,
                    expires_at=excluded.expires_at,
                    updated_at=excluded.updated_at
                """,
                (
                    namespace,
                    key1,
                    key2,
                    value,
                    expires_at.isoformat(),
                    now,
                    now,
                ),
            )
            conn.commit()

    def fetch_json(self, namespace: str, key1: str, key2: str = "") -> Optional[Any]:
        now = datetime.utcnow().isoformat()
        with self._conn() as conn:
            self._purge_expired(conn, namespace)
            row = conn.execute(
                f"""
                SELECT payload_json
                FROM {self.table_name}
                WHERE namespace = ? AND key1 = ? AND key2 = ? AND expires_at > ?
                """,
                (namespace, key1, key2, now),
            ).fetchone()
        if not row or row["payload_json"] is None:
            return None
        try:
            return json.loads(row["payload_json"])
        except Exception as exc:
            logger.error("Failed to decode cached JSON for %s/%s/%s: %s", namespace, key1, key2, exc)
            return None

    def fetch_text(self, namespace: str, key1: str, key2: str = "") -> Optional[str]:
        now = datetime.utcnow().isoformat()
        with self._conn() as conn:
            self._purge_expired(conn, namespace)
            row = conn.execute(
                f"""
                SELECT payload_text
                FROM {self.table_name}
                WHERE namespace = ? AND key1 = ? AND key2 = ? AND expires_at > ?
                """,
                (namespace, key1, key2, now),
            ).fetchone()
        if not row:
            return None
        return row["payload_text"]

    def has_unexpired(self, namespace: str, key1: str, key2: str = "") -> bool:
        now = datetime.utcnow().isoformat()
        with self._conn() as conn:
            self._purge_expired(conn, namespace)
            row = conn.execute(
                f"""
                SELECT 1
                FROM {self.table_name}
                WHERE namespace = ? AND key1 = ? AND key2 = ? AND expires_at > ?
                LIMIT 1
                """,
                (namespace, key1, key2, now),
            ).fetchone()
        return bool(row)

    def delete(self, namespace: str, key1: str, key2: Optional[str] = None) -> None:
        with self._conn() as conn:
            if key2 is None:
                conn.execute(
                    f"DELETE FROM {self.table_name} WHERE namespace = ? AND key1 = ?",
                    (namespace, key1),
                )
            else:
                conn.execute(
                    f"DELETE FROM {self.table_name} WHERE namespace = ? AND key1 = ? AND key2 = ?",
                    (namespace, key1, key2),
                )
            conn.commit()


_cache_store = _LocalCacheStore()


def _is_error_payload(payload: Any) -> bool:
    """Return True if payload looks like an error structure we should not cache."""
    try:
        if not isinstance(payload, dict):
            return False
        if "error" in payload and payload.get("error"):
            return True
        serialized = json.dumps(payload).lower()
        return "rate_limit" in serialized or "request too large" in serialized
    except Exception:
        return False


def save_news_summary_to_cache(company: str, result: Dict[str, Any], ttl_hours: Optional[int] = None):
    if not company:
        logger.warning("Company is missing, skipping cache save")
        return
    if result is None:
        logger.warning("Result is None, skipping cache save")
        return
    if _is_error_payload(result):
        logger.warning("Error payload detected for narrative_cache - not caching")
        return
    try:
        namespace = _namespace("news_summary")
        expires_at = _expiry(ttl_hours if ttl_hours is not None else namespace.default_ttl_hours)
        _cache_store.upsert_json(namespace.key, company, result, expires_at)
    except Exception as exc:
        logger.error("Failed to save news summary to local cache: %s", exc)


def fetch_news_summary_from_cache(company: str) -> Optional[Dict[str, Any]]:
    if not company:
        logger.warning("Company is missing, skipping cache fetch")
        return None
    try:
        return _cache_store.fetch_json(_namespace("news_summary").key, company)
    except Exception as exc:
        logger.error("Failed to fetch news summary from local cache: %s", exc)
        return None


def save_earnings_summary_to_cache(
    ticker: str,
    result: Dict[str, Any],
    ttl_days: Optional[int] = None,
):
    if not ticker:
        logger.warning("Ticker is missing, skipping earnings cache save")
        return
    if result is None:
        logger.warning("Result is None, skipping cache save")
        return
    try:
        namespace = _namespace("earnings_summary")
        ttl_hours = namespace.default_ttl_hours if ttl_days is None else int(ttl_days) * 24
        expires_at = _expiry(ttl_hours)
        _cache_store.upsert_json(namespace.key, ticker.upper(), result, expires_at)
    except Exception as exc:
        logger.error("Failed to save earnings summary to local cache: %s", exc)


def fetch_earnings_summary_from_cache(ticker: str) -> Optional[Dict[str, Any]]:
    if not ticker:
        logger.warning("Ticker is missing, skipping earnings cache fetch")
        return None
    try:
        return _cache_store.fetch_json(_namespace("earnings_summary").key, ticker.upper())
    except Exception as exc:
        logger.error("Failed to fetch earnings summary from local cache: %s", exc)
        return None


def save_macro_news_to_cache(country: str, result: str, ttl_hours: Optional[int] = None):
    if not country:
        logger.warning("Country is missing, skipping macro cache save")
        return
    if result is None:
        logger.warning("Result is None, skipping cache save")
        return
    try:
        namespace = _namespace("macro_news")
        expires_at = _expiry(ttl_hours if ttl_hours is not None else namespace.default_ttl_hours)
        _cache_store.upsert_text(namespace.key, country.upper(), result, expires_at)
    except Exception as exc:
        logger.error("Failed to save macro news to local cache: %s", exc)


def fetch_macro_news_from_cache(country: str) -> Optional[str]:
    if not country:
        logger.warning("Country is missing, skipping macro cache fetch")
        return None
    try:
        return _cache_store.fetch_text(_namespace("macro_news").key, country.upper())
    except Exception as exc:
        logger.error("Failed to fetch macro news from local cache: %s", exc)
        return None


def get_cached_segments(company: str, industry: str):
    if not company:
        logger.warning("Company is missing, skipping segments cache fetch")
        return None
    if not industry:
        logger.warning("Industry is missing, skipping segments cache fetch")
        return None
    try:
        return _cache_store.fetch_json(_namespace("segments").key, company, industry)
    except Exception as exc:
        logger.error("Failed to fetch segments from local cache: %s", exc)
        return None


def set_cached_segments(company: str, industry: str, response: dict):
    if not company:
        logger.warning("Company is missing, skipping segments cache save")
        return
    if not industry:
        logger.warning("Industry is missing, skipping segments cache save")
        return
    if response is None:
        logger.warning("Response is None, skipping cache save")
        return
    try:
        namespace = _namespace("segments")
        expires_at = _expiry(namespace.default_ttl_hours)
        _cache_store.upsert_json(namespace.key, company, response, expires_at, key2=industry)
        logger.debug("Cached segments for %s (%s) with 1-year expiry", company, industry)
    except Exception as exc:
        logger.error("Failed to save segments to local cache: %s", exc)


def save_tavily_query_to_cache(
    ticker: str,
    query_type: str,
    generated_query: str,
    ttl_hours: int = None,
) -> None:
    if not ticker or not query_type or not generated_query:
        logger.warning("Missing required fields for Tavily query cache save")
        return
    if ttl_hours is None:
        ttl_hours = TAVILY_QUERY_CACHE_TTL.get(query_type, 24)
    ticker_key = ticker.upper()
    query_key = query_type.lower()
    try:
        namespace = _namespace("tavily_query")
        # Preserve original intent (avoid regenerating) while allowing expired entries to be replaced.
        if _cache_store.has_unexpired(namespace.key, ticker_key, query_key):
            logger.debug(
                "Tavily query cache already exists for %s/%s, skipping save",
                ticker_key,
                query_key,
            )
            return
        expires_at = _expiry(ttl_hours)
        _cache_store.upsert_text(namespace.key, ticker_key, generated_query, expires_at, key2=query_key)
        logger.debug(
            "Cached Tavily query for %s/%s (expires in %sh)",
            ticker_key,
            query_key,
            ttl_hours,
        )
    except Exception as exc:
        logger.error("Failed to save Tavily query to local cache: %s", exc)


def fetch_tavily_query_from_cache(ticker: str, query_type: str) -> Optional[str]:
    if not ticker or not query_type:
        logger.warning("Missing ticker or query_type for Tavily query cache fetch")
        return None
    try:
        return _cache_store.fetch_text(_namespace("tavily_query").key, ticker.upper(), query_type.lower())
    except Exception as exc:
        logger.error("Failed to fetch Tavily query from local cache: %s", exc)
        return None


def invalidate_tavily_query_cache(ticker: str, query_type: str = None) -> None:
    if not ticker:
        logger.warning("Missing ticker for cache invalidation")
        return
    try:
        namespace_key = _namespace("tavily_query").key
        if query_type:
            _cache_store.delete(namespace_key, ticker.upper(), query_type.lower())
        else:
            _cache_store.delete(namespace_key, ticker.upper(), None)
    except Exception as exc:
        logger.error("Failed to invalidate Tavily query cache: %s", exc)
