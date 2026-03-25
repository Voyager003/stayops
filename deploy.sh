#!/bin/bash
set -euo pipefail

COMPOSE_FILE="docker-compose.prod.yml"

echo "==> Pulling latest app image..."
docker compose -f "$COMPOSE_FILE" pull app

echo "==> Restarting app service..."
docker compose -f "$COMPOSE_FILE" up -d --no-deps app

echo "==> Cleaning up old images..."
docker image prune -f

echo "==> Current service status:"
docker compose -f "$COMPOSE_FILE" ps

echo "==> Health check (max 90s)..."
for i in $(seq 1 30); do
    if curl -so /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>&1 | grep -q "200"; then
        echo "==> Health check passed"
        exit 0
    fi
    sleep 3
done
echo "==> Health check FAILED"
exit 1
