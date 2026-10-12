#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e MAVEN_CONFIG=/tmp/maven \
  -e MAVEN_OPTS=-Duser.home=/tmp \
  -v "$ROOT:/workspace" \
  -w /workspace \
  "$IMAGE" \
  mvn --batch-mode --no-transfer-progress clean verify

printf 'Built %s\n' "$ROOT/target/awepeeps-navigator-1.6.0.jar"
