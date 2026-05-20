#!/usr/bin/env bash
set -euo pipefail

ROLE="${1:?role is required}"
ARTIFACT_BUCKET="${2:?artifact bucket is required}"
ARTIFACT_KEY="${3:?artifact key is required}"
PARAMETER_PATH="${4:?parameter path is required}"
STACK_DIR="${5:?stack dir is required}"
COMPOSE_FILES="${6:?compose files are required}"
IMAGE_OVERRIDE="${7:-}"

ARCHIVE_PATH="/tmp/stayops-${ROLE}.tgz"
PARAMETER_PATH="${PARAMETER_PATH%/}"
DOCKER_COMPOSE_VERSION="v2.39.4"

log() {
  printf '[stayops-bootstrap] %s\n' "$*"
}

ensure_command() {
  if command -v "$1" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

ensure_curl() {
  if ensure_command curl; then
    return 0
  fi

  if ensure_command dnf; then
    dnf install -y curl
  elif ensure_command yum; then
    yum install -y curl
  elif ensure_command apt-get; then
    apt-get update
    apt-get install -y curl
  else
    log "cannot install curl: no supported package manager"
    exit 1
  fi
}

install_docker_compose() {
  local arch plugin_dir

  ensure_curl

  arch="$(uname -m)"
  case "${arch}" in
    x86_64) arch="x86_64" ;;
    aarch64 | arm64) arch="aarch64" ;;
    *)
      log "unsupported architecture for docker compose: ${arch}"
      exit 1
      ;;
  esac

  plugin_dir="/usr/local/lib/docker/cli-plugins"
  mkdir -p "${plugin_dir}"
  curl -fsSL \
    "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-linux-${arch}" \
    -o "${plugin_dir}/docker-compose"
  chmod +x "${plugin_dir}/docker-compose"
}

ensure_runtime_tools() {
  if ! ensure_command aws; then
    if ensure_command dnf; then
      dnf install -y awscli || dnf install -y awscli-2
    elif ensure_command yum; then
      yum install -y awscli
    elif ensure_command apt-get; then
      apt-get update
      apt-get install -y awscli
    else
      log "cannot install aws cli: no supported package manager"
      exit 1
    fi
  fi

  if ! ensure_command docker; then
    log "docker is not installed"
    exit 1
  fi

  if ! docker compose version >/dev/null 2>&1; then
    install_docker_compose
  fi

  if ! docker compose version >/dev/null 2>&1; then
    log "docker compose plugin is not available"
    exit 1
  fi

  systemctl enable docker
  systemctl start docker
}

metadata_token() {
  curl -fsS -X PUT "http://169.254.169.254/latest/api/token" \
    -H "X-aws-ec2-metadata-token-ttl-seconds: 21600" || true
}

metadata_get() {
  local path="$1"
  local token="$2"
  if [[ -n "${token}" ]]; then
    curl -fsS -H "X-aws-ec2-metadata-token: ${token}" "http://169.254.169.254/latest/meta-data/${path}"
  else
    curl -fsS "http://169.254.169.254/latest/meta-data/${path}"
  fi
}

detect_region() {
  local token az
  token="$(metadata_token)"
  az="$(metadata_get "placement/availability-zone" "${token}")"
  printf '%s\n' "${az::-1}"
}

detect_instance_name() {
  local region token instance_id name
  region="$1"
  token="$(metadata_token)"
  instance_id="$(metadata_get "instance-id" "${token}")"
  name="$(aws ec2 describe-tags \
    --region "${region}" \
    --filters "Name=resource-id,Values=${instance_id}" "Name=key,Values=Name" \
    --query "Tags[0].Value" \
    --output text 2>/dev/null || true)"

  if [[ -z "${name}" || "${name}" == "None" ]]; then
    printf '%s\n' "${instance_id}"
  else
    printf '%s\n' "${name}"
  fi
}

instance_alias_from_name() {
  local name="$1"
  case "${name}" in
    *app-1) printf 'app-1\n' ;;
    *app-2) printf 'app-2\n' ;;
    *mongo-1) printf 'mongo-1\n' ;;
    *mongo-2) printf 'mongo-2\n' ;;
    *mongo-3) printf 'mongo-3\n' ;;
    *minimal-app) printf 'minimal-app\n' ;;
    *minimal-mongo) printf 'minimal-mongo\n' ;;
    *redis) printf 'redis\n' ;;
    *mock-ota) printf 'mock-ota\n' ;;
    *observability) printf 'observability\n' ;;
    *) printf '%s\n' "${name}" ;;
  esac
}

write_env_from_parameter_store() {
  local region="$1"
  local env_file="$2"

  : > "${env_file}"

  aws ssm get-parameters-by-path \
    --region "${region}" \
    --path "${PARAMETER_PATH}" \
    --recursive \
    --with-decryption \
    --query "Parameters[*].[Name,Value]" \
    --output text |
    while IFS=$'\t' read -r name value; do
      [[ -z "${name}" ]] && continue
      printf '%s=%s\n' "${name##*/}" "${value}" >> "${env_file}"
    done
}

append_runtime_identity() {
  local env_file="$1"
  local alias="$2"

  if ! grep -q '^HOSTNAME=' "${env_file}"; then
    printf 'HOSTNAME=%s\n' "${alias}" >> "${env_file}"
  fi

  if [[ "${ROLE}" == "app" ]] && ! grep -q '^STAYOPS_INSTANCE_ID=' "${env_file}"; then
    printf 'STAYOPS_INSTANCE_ID=%s\n' "${alias}" >> "${env_file}"
  fi
}

write_secret_files() {
  local env_file="$1"
  local mongo_keyfile_content mongo_keyfile_b64 mock_ota_htpasswd_content mock_ota_htpasswd_b64

  mongo_keyfile_content="$(env_value "${env_file}" "MONGO_KEYFILE_CONTENT")"
  mongo_keyfile_b64="$(env_value "${env_file}" "MONGO_KEYFILE_B64")"
  mock_ota_htpasswd_content="$(env_value "${env_file}" "MOCK_OTA_HTPASSWD_CONTENT")"
  mock_ota_htpasswd_b64="$(env_value "${env_file}" "MOCK_OTA_HTPASSWD_B64")"

  if [[ -n "${mongo_keyfile_b64}" ]]; then
    printf '%s' "${mongo_keyfile_b64}" | base64 -d > "${STACK_DIR}/mongo-keyfile"
    chown 999:999 "${STACK_DIR}/mongo-keyfile"
    chmod 400 "${STACK_DIR}/mongo-keyfile"
  elif [[ -n "${mongo_keyfile_content}" ]]; then
    printf '%s' "${mongo_keyfile_content}" > "${STACK_DIR}/mongo-keyfile"
    chown 999:999 "${STACK_DIR}/mongo-keyfile"
    chmod 400 "${STACK_DIR}/mongo-keyfile"
  fi

  if [[ -n "${mock_ota_htpasswd_b64}" ]]; then
    printf '%s' "${mock_ota_htpasswd_b64}" | base64 -d > "${STACK_DIR}/.htpasswd"
    chmod 400 "${STACK_DIR}/.htpasswd"
  elif [[ -n "${mock_ota_htpasswd_content}" ]]; then
    printf '%s\n' "${mock_ota_htpasswd_content}" > "${STACK_DIR}/.htpasswd"
    chmod 400 "${STACK_DIR}/.htpasswd"
  fi
}

require_env_value() {
  local env_file="$1"
  local key="$2"
  local value

  value="$(env_value "${env_file}" "${key}")"
  if [[ -z "${value}" ]]; then
    log "missing required parameter: ${PARAMETER_PATH}/${key}"
    exit 1
  fi
}

require_one_env_value() {
  local env_file="$1"
  shift
  local key value candidates

  candidates="$*"
  for key in "$@"; do
    value="$(env_value "${env_file}" "${key}")"
    if [[ -n "${value}" ]]; then
      return 0
    fi
  done

  log "missing required parameter: one of ${candidates} under ${PARAMETER_PATH}"
  exit 1
}

validate_required_env() {
  local env_file="$1"

  case "${ROLE}" in
    app)
      require_env_value "${env_file}" "SPRING_MONGODB_URI"
      require_env_value "${env_file}" "SPRING_DATA_REDIS_HOST"
      require_env_value "${env_file}" "TOSS_SECRET_KEY"
      require_env_value "${env_file}" "MOCK_OTA_ENDPOINT"
      require_env_value "${env_file}" "LOKI_URL"
      ;;
    mongodb-rss)
      require_env_value "${env_file}" "MONGO_INITDB_ROOT_USERNAME"
      require_env_value "${env_file}" "MONGO_INITDB_ROOT_PASSWORD"
      require_env_value "${env_file}" "MONGO_APP_USERNAME"
      require_env_value "${env_file}" "MONGO_APP_PASSWORD"
      require_env_value "${env_file}" "MONGO_EXPORTER_USERNAME"
      require_env_value "${env_file}" "MONGO_EXPORTER_PASSWORD"
      require_env_value "${env_file}" "MONGO_REPLICA_SET"
      require_env_value "${env_file}" "MONGO1_HOST"
      require_env_value "${env_file}" "MONGO2_HOST"
      require_env_value "${env_file}" "MONGO3_HOST"
      require_env_value "${env_file}" "LOKI_URL"
      require_one_env_value "${env_file}" "MONGO_KEYFILE_B64" "MONGO_KEYFILE_CONTENT"
      ;;
    mock-ota)
      require_env_value "${env_file}" "MOCK_OTA_PMS_WEBHOOK_URL"
      require_env_value "${env_file}" "LOKI_URL"
      require_one_env_value "${env_file}" "MOCK_OTA_HTPASSWD_B64" "MOCK_OTA_HTPASSWD_CONTENT"
      ;;
    minimal-app)
      require_env_value "${env_file}" "API_DOMAIN"
      require_env_value "${env_file}" "SPRING_MONGODB_URI"
      require_env_value "${env_file}" "TOSS_SECRET_KEY"
      require_env_value "${env_file}" "MOCK_OTA_ENDPOINT"
      require_env_value "${env_file}" "MOCK_OTA_PMS_WEBHOOK_URL"
      require_one_env_value "${env_file}" "MOCK_OTA_HTPASSWD_B64" "MOCK_OTA_HTPASSWD_CONTENT"
      ;;
    minimal-mongodb)
      require_env_value "${env_file}" "MONGO_REPLICA_SET"
      require_env_value "${env_file}" "MONGO_HOST"
      require_env_value "${env_file}" "MONGO_INITDB_ROOT_USERNAME"
      require_env_value "${env_file}" "MONGO_INITDB_ROOT_PASSWORD"
      require_env_value "${env_file}" "MONGO_APP_USERNAME"
      require_env_value "${env_file}" "MONGO_APP_PASSWORD"
      require_env_value "${env_file}" "MONGO_EXPORTER_USERNAME"
      require_env_value "${env_file}" "MONGO_EXPORTER_PASSWORD"
      ;;
    redis)
      require_env_value "${env_file}" "LOKI_URL"
      ;;
    observability)
      require_env_value "${env_file}" "GRAFANA_PASSWORD"
      ;;
    *)
      log "unknown role for env validation: ${ROLE}"
      exit 1
      ;;
  esac
}

docker_login_if_configured() {
  local env_file="$1"
  local ghcr_username ghcr_token

  ghcr_username="$(env_value "${env_file}" "GHCR_USERNAME")"
  ghcr_token="$(env_value "${env_file}" "GHCR_TOKEN")"

  if [[ -n "${ghcr_username}" && -n "${ghcr_token}" ]]; then
    printf '%s' "${ghcr_token}" | docker login ghcr.io -u "${ghcr_username}" --password-stdin
  fi
}

env_value() {
  local env_file="$1"
  local key="$2"
  grep -E "^${key}=" "${env_file}" | head -n 1 | cut -d= -f2- || true
}

set_env_value() {
  local env_file="$1"
  local key="$2"
  local value="$3"

  if grep -q "^${key}=" "${env_file}"; then
    sed -i.bak "s|^${key}=.*|${key}=${value}|" "${env_file}"
    rm -f "${env_file}.bak"
  else
    printf '%s=%s\n' "${key}" "${value}" >> "${env_file}"
  fi
}

compose_args() {
  local file
  IFS=':' read -ra files <<< "${COMPOSE_FILES}"
  for file in "${files[@]}"; do
    printf -- '-f\n%s\n' "${file}"
  done
}

install_systemd_unit() {
  local wrapper="/usr/local/bin/stayops-${ROLE}-compose"
  local unit="/etc/systemd/system/stayops-${ROLE}.service"

  {
    printf '#!/usr/bin/env bash\n'
    printf 'set -euo pipefail\n'
    printf 'cd %q\n' "${STACK_DIR}"
    printf 'action="${1:-up}"\n'
    printf 'case "${action}" in\n'
    printf '  up) exec docker compose '
    while read -r arg; do
      printf '%q ' "${arg}"
    done < <(compose_args)
    printf 'up -d ;;\n'
    printf '  stop) exec docker compose '
    while read -r arg; do
      printf '%q ' "${arg}"
    done < <(compose_args)
    printf 'stop ;;\n'
    printf '  *) echo "unknown action: ${action}" >&2; exit 2 ;;\n'
    printf 'esac\n'
  } > "${wrapper}"
  chmod 755 "${wrapper}"

  cat > "${unit}" <<UNIT
[Unit]
Description=StayOps ${ROLE} compose stack
Requires=docker.service
After=docker.service network-online.target
Wants=network-online.target

[Service]
Type=oneshot
WorkingDirectory=${STACK_DIR}
ExecStart=${wrapper}
ExecStop=${wrapper} stop
RemainAfterExit=yes
TimeoutStartSec=0

[Install]
WantedBy=multi-user.target
UNIT

  systemctl daemon-reload
  systemctl enable "stayops-${ROLE}.service"
}

main() {
  local region instance_name instance_alias env_file

  ensure_runtime_tools

  region="$(detect_region)"
  instance_name="$(detect_instance_name "${region}")"
  instance_alias="$(instance_alias_from_name "${instance_name}")"
  env_file="${STACK_DIR}/.env"

  log "deploying role=${ROLE} instance=${instance_name} alias=${instance_alias} region=${region}"

  mkdir -p "${STACK_DIR}"
  aws s3 cp "s3://${ARTIFACT_BUCKET}/${ARTIFACT_KEY}" "${ARCHIVE_PATH}" --region "${region}"
  tar -xzf "${ARCHIVE_PATH}" -C "${STACK_DIR}"

  write_env_from_parameter_store "${region}" "${env_file}"
  append_runtime_identity "${env_file}" "${instance_alias}"
  if [[ -n "${IMAGE_OVERRIDE}" ]]; then
    set_env_value "${env_file}" "STAYOPS_IMAGE" "${IMAGE_OVERRIDE}"
  fi
  chmod 600 "${env_file}"
  validate_required_env "${env_file}"
  write_secret_files "${env_file}"
  docker_login_if_configured "${env_file}"

  cd "${STACK_DIR}"
  docker compose $(compose_args) pull
  docker compose $(compose_args) up -d
  install_systemd_unit

  log "deployment completed for ${ROLE}"
}

main "$@"
