#!/bin/bash
set -euxo pipefail

if command -v dnf >/dev/null 2>&1; then
  dnf update -y
  dnf install -y docker git
elif command -v yum >/dev/null 2>&1; then
  yum update -y
  yum install -y docker git
elif command -v apt-get >/dev/null 2>&1; then
  apt-get update
  apt-get install -y docker.io docker-compose-plugin git
fi

systemctl enable docker
systemctl start docker

mkdir -p /opt/stayops
