#!/bin/bash
set -euxo pipefail

DOCKER_COMPOSE_VERSION="v2.39.4"

install_docker_compose() {
  local arch plugin_dir

  arch="$(uname -m)"
  case "${arch}" in
    x86_64) arch="x86_64" ;;
    aarch64 | arm64) arch="aarch64" ;;
    *)
      echo "unsupported architecture for docker compose: ${arch}" >&2
      exit 1
      ;;
  esac

  plugin_dir="/usr/local/lib/docker/cli-plugins"
  mkdir -p "${plugin_dir}"
  curl -fsSL \
    "https://github.com/docker/compose/releases/download/${DOCKER_COMPOSE_VERSION}/docker-compose-linux-${arch}" \
    -o "${plugin_dir}/docker-compose"
  chmod +x "${plugin_dir}/docker-compose"
  docker compose version
}

if command -v dnf >/dev/null 2>&1; then
  dnf update -y
  dnf install -y docker git tar gzip
  dnf install -y awscli || dnf install -y awscli-2
elif command -v yum >/dev/null 2>&1; then
  yum update -y
  yum install -y docker git tar gzip awscli
elif command -v apt-get >/dev/null 2>&1; then
  apt-get update
  apt-get install -y docker.io git tar gzip curl awscli
fi

install_docker_compose

systemctl enable docker
systemctl start docker

mkdir -p /opt/stayops
