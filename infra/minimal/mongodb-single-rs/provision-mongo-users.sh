#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${1:-${SCRIPT_DIR}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "env file not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

required_vars=(
  MONGO_INITDB_ROOT_USERNAME
  MONGO_INITDB_ROOT_PASSWORD
  MONGO_APP_USERNAME
  MONGO_APP_PASSWORD
  MONGO_EXPORTER_USERNAME
  MONGO_EXPORTER_PASSWORD
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "missing required env: ${var}" >&2
    exit 1
  fi
done

docker compose --env-file "${ENV_FILE}" -f "${SCRIPT_DIR}/docker-compose.yml" exec -T \
  -e MONGO_APP_USERNAME="${MONGO_APP_USERNAME}" \
  -e MONGO_APP_PASSWORD="${MONGO_APP_PASSWORD}" \
  -e MONGO_EXPORTER_USERNAME="${MONGO_EXPORTER_USERNAME}" \
  -e MONGO_EXPORTER_PASSWORD="${MONGO_EXPORTER_PASSWORD}" \
  mongo \
  mongosh \
  -u "${MONGO_INITDB_ROOT_USERNAME}" \
  -p "${MONGO_INITDB_ROOT_PASSWORD}" \
  --authenticationDatabase admin \
  /opt/stayops/create-users.js
