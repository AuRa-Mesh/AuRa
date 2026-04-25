#!/usr/bin/env bash
# Отправка правок в GitHub и обновление копии на VPS (git pull + restart API).
# Настройка: cp deploy.env.example deploy.env
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/deploy.env" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT/deploy.env"
fi

SSH_TARGET="${AURA_DEPLOY_SSH:-}"
REMOTE_PATH="${AURA_DEPLOY_PATH:-/opt/aura/AuRa-deploy}"
BRANCH="${AURA_DEPLOY_BRANCH:-main}"

if [[ -z "$SSH_TARGET" ]]; then
  echo "Error: AURA_DEPLOY_SSH is not set. Copy deploy.env.example to deploy.env and set root@server-ip"
  exit 1
fi

if [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
  echo "Error: uncommitted changes. Commit or stash first:"
  git status -s
  exit 1
fi

echo "==> push → origin/$BRANCH"
git push "origin" "$BRANCH"

# Без явного -i ssh берёт «первый подходящий» ключ — для root@IP часто не подходит id_ed25519_aura.
SSH_ARGS=(-o BatchMode=yes)
if [[ -n "${AURA_DEPLOY_IDENTITY:-}" && -f "${AURA_DEPLOY_IDENTITY/#\~/$HOME}" ]]; then
  _id="${AURA_DEPLOY_IDENTITY/#\~/$HOME}"
  SSH_ARGS+=(-i "$_id")
elif [[ -f "$HOME/.ssh/id_ed25519_aura" ]]; then
  SSH_ARGS+=(-i "$HOME/.ssh/id_ed25519_aura")
fi

echo "==> ssh: pull + systemctl restart aura-api ($SSH_TARGET:$REMOTE_PATH)"
ssh "${SSH_ARGS[@]}" "$SSH_TARGET" bash -s -- "$REMOTE_PATH" "$BRANCH" <<'REMOTE'
set -euo pipefail
cd "$1"
git fetch origin
git pull --ff-only origin "$2"
systemctl restart aura-api
if systemctl is-active --quiet aura-api; then
  echo "aura-api: active"
else
  systemctl --no-pager status aura-api || true
  exit 1
fi
REMOTE

echo "==> done"
