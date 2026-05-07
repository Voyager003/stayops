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
  MONGO_REPLICA_SET
  MONGO_HOST
  MONGO_INITDB_ROOT_USERNAME
  MONGO_INITDB_ROOT_PASSWORD
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "missing required env: ${var}" >&2
    exit 1
  fi
done

docker compose --env-file "${ENV_FILE}" -f "${SCRIPT_DIR}/docker-compose.yml" exec -T \
  -e MONGO_REPLICA_SET="${MONGO_REPLICA_SET}" \
  -e MONGO_HOST="${MONGO_HOST}" \
  mongo \
  mongosh \
  -u "${MONGO_INITDB_ROOT_USERNAME}" \
  -p "${MONGO_INITDB_ROOT_PASSWORD}" \
  --authenticationDatabase admin \
  /opt/stayops/init-single-replica-set.js
