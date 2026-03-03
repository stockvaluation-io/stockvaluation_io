FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    gcc \
    g++ \
    curl \
    libglib2.0-0 \
    libsm6 \
    libxrender1 \
    libxext6 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . /app

RUN set -eux; \
    if [ -f /app/requirements.txt ]; then \
      req_file=/app/requirements.txt; \
    elif [ -f /app/valuation-agent/requirements.txt ]; then \
      req_file=/app/valuation-agent/requirements.txt; \
    else \
      echo "requirements.txt not found in build context"; \
      exit 1; \
    fi; \
    pip install --no-cache-dir --upgrade pip; \
    pip install --no-cache-dir -r "${req_file}"; \
    mkdir -p /app/logs /app/prompt_dump /app/local_data /app/valuation-agent/logs /app/valuation-agent/prompt_dump /app/valuation-agent/local_data; \
    chmod -R 755 /app

ENV PYTHONPATH=/app:/app/valuation-agent
ENV PYTHONUNBUFFERED=1
ENV FLASK_ENV=production

EXPOSE 5001

HEALTHCHECK --interval=30s --timeout=30s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:5001/health || exit 1

CMD ["sh", "-lc", "if [ -f /app/app.py ]; then python /app/app.py; else python /app/valuation-agent/app.py; fi"]
