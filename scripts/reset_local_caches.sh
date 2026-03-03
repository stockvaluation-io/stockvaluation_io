#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CACHE_PATHS=(
  "$ROOT_DIR/local_data/valuation-agent/cache"
  "$ROOT_DIR/valuation-agent/local_data/cache"
)

for cache_dir in "${CACHE_PATHS[@]}"; do
  if [[ -d "$cache_dir" ]]; then
    rm -f "$cache_dir"/cache.db "$cache_dir"/cache.db-shm "$cache_dir"/cache.db-wal
    echo "Cleared SQLite cache files in: $cache_dir"
  fi
done

if command -v docker >/dev/null 2>&1; then
  if docker ps --format '{{.Names}}' | grep -q '^sv-local-redis$'; then
    docker exec sv-local-redis redis-cli FLUSHALL >/dev/null
    echo "Flushed Redis cache in container: sv-local-redis"
  else
    echo "Redis container sv-local-redis not running; skipped Redis flush"
  fi
else
  echo "Docker not available; skipped Redis flush"
fi

echo "Local cache reset complete"
