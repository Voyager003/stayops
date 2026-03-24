#!/bin/bash
set -euo pipefail

COMPOSE_FILE="docker-compose.prod.yml"

echo "==> Pulling latest source..."
git pull origin main

echo "==> Pulling latest app image..."
docker compose -f "$COMPOSE_FILE" pull app

echo "==> Restarting app service..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps app

echo "==> Cleaning up old images..."
docker image prune -f

echo "==> Current service status:"
docker compose -f "$COMPOSE_FILE" ps

echo "==> Health check (max 30s)..."
for i in $(seq 1 10); do
    if curl -sf http://localhost/ > /dev/null 2>&1; then
        echo "==> Health check passed"
        exit 0
    fi
    sleep 3
done
echo "==> Health check FAILED"
exit 1
