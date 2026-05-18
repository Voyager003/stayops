#!/usr/bin/env bash
set -euo pipefail

ROLE="${1:?role is required}"
ARTIFACT_BUCKET="${2:?artifact bucket is required}"
ARTIFACT_KEY="${3:?artifact key is required}"
PARAMETER_PATH="${4:?parameter path is required}"
STACK_DIR="${5:?stack dir is required}"
COMPOSE_FILES="${6:?compose files are required}"

ARCHIVE_PATH="/tmp/stayops-${ROLE}.tgz"
PARAMETER_PATH="${PARAMETER_PATH%/}"

log() {
  printf '[stayops-bootstrap] %s\n' "$*"
}

ensure_command() {
  if command -v "$1" >/dev/null 2>&1; then
    return 0
  fi
  return 1
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
    chmod 400 "${STACK_DIR}/mongo-keyfile"
  elif [[ -n "${mongo_keyfile_content}" ]]; then
    printf '%s' "${mongo_keyfile_content}" > "${STACK_DIR}/mongo-keyfile"
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
  chmod 600 "${env_file}"
  write_secret_files "${env_file}"
  docker_login_if_configured "${env_file}"

  cd "${STACK_DIR}"
  docker compose $(compose_args) pull
  docker compose $(compose_args) up -d
  install_systemd_unit

  log "deployment completed for ${ROLE}"
}

main "$@"
