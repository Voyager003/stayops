#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${1:-.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "env file not found: ${ENV_FILE}" >&2
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

required_vars=(
  MONGO_REPLICA_SET
  MONGO1_HOST
  MONGO2_HOST
  MONGO3_HOST
  MONGO_INITDB_ROOT_USERNAME
  MONGO_INITDB_ROOT_PASSWORD
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "missing required env: ${var}" >&2
    exit 1
  fi
done

docker compose exec -T \
  -e MONGO_REPLICA_SET="${MONGO_REPLICA_SET}" \
  -e MONGO1_HOST="${MONGO1_HOST}" \
  -e MONGO2_HOST="${MONGO2_HOST}" \
  -e MONGO3_HOST="${MONGO3_HOST}" \
  mongo \
  mongosh \
  -u "${MONGO_INITDB_ROOT_USERNAME}" \
  -p "${MONGO_INITDB_ROOT_PASSWORD}" \
  --authenticationDatabase admin \
  /opt/stayops/init-replica-set.js
