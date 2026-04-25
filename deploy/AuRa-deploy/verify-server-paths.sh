#!/usr/bin/env bash
# Проверка согласованности путей на VPS: nginx root, systemd aura-api, rsync-цель (deploy.env).
# Запуск: ./verify-server-paths.sh
# Требуется: deploy.env с AURA_DEPLOY_SSH (и при смене каталога — AURA_DEPLOY_PATH).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ ! -f "$ROOT/deploy.env" ]]; then
  echo "Нет $ROOT/deploy.env — скопируйте deploy.env.example и настройте."
  exit 1
fi
# shellcheck source=/dev/null
source "$ROOT/deploy.env"

SSH_TARGET="${AURA_DEPLOY_SSH:-}"
REMOTE_PATH="${AURA_DEPLOY_PATH:-/opt/aura/AuRa-deploy}"

if [[ -z "$SSH_TARGET" ]]; then
  echo "AURA_DEPLOY_SSH не задан в deploy.env"
  exit 1
fi

SSH_BASE=(ssh -o BatchMode=yes -o ConnectTimeout=15)
if [[ -n "${AURA_DEPLOY_IDENTITY:-}" && -f "${AURA_DEPLOY_IDENTITY/#\~/$HOME}" ]]; then
  _id="${AURA_DEPLOY_IDENTITY/#\~/$HOME}"
  SSH_BASE=(ssh -o BatchMode=yes -o ConnectTimeout=15 -i "$_id")
elif [[ -f "$HOME/.ssh/id_ed25519_aura" ]]; then
  SSH_BASE=(ssh -o BatchMode=yes -o ConnectTimeout=15 -i "$HOME/.ssh/id_ed25519_aura")
fi

echo "==> Локальный deploy: AURA_DEPLOY_PATH=$REMOTE_PATH"
echo "==> SSH: $SSH_TARGET"
echo ""

"${SSH_BASE[@]}" "$SSH_TARGET" bash -s -- "$REMOTE_PATH" <<'REMOTE'
set -euo pipefail
RP="${1:?remote path}"
echo "--- каталог заливки (как в deploy.env AURA_DEPLOY_PATH) ---"
if [[ -d "$RP" ]]; then
  echo "OK: $RP существует"
  ls -ld "$RP" 2>/dev/null
else
  echo "ОШИБКА: $RP — нет такого каталога. Проверьте путь на сервере и AURA_DEPLOY_PATH."
fi
for f in index.html server/app.py; do
  if [[ -f "$RP/$f" ]]; then echo "  OK: $f"; else echo "  НЕТ: $RP/$f"; fi
done
echo ""
echo "--- systemd aura-api (WorkingDirectory = .../server, должен вести в $RP) ---"
if systemctl list-unit-files 2>/dev/null | grep -q '^aura-api.service'; then
  systemctl show -p FragmentPath,WorkingDirectory,ExecStart aura-api 2>/dev/null || true
  echo "  state: $(systemctl is-active aura-api 2>/dev/null || echo '?')"
  wd=$(systemctl show -p WorkingDirectory --value aura-api 2>/dev/null || true)
  expect="${RP}/server"
  if [[ -n "$wd" && "$wd" != "$expect" ]]; then
    echo "  ВНИМАНИЕ: WorkingDirectory=$wd — ожидается $expect (иначе app.py смотрит не в ту папку)."
  else
    echo "  OK: WorkingDirectory совпадает с ожидаемым $expect (или пусто)."
  fi
else
  echo "  (aura-api.service не найден в systemctl list-unit-files)"
fi
echo ""
echo "--- порт 8080 (uvicorn) ---"
code=$(curl -sS -o /dev/null -w "%{http_code}" "http://127.0.0.1:8080/health" 2>/dev/null || echo "ERR")
if [[ "$code" == "200" ]]; then
  echo "  OK: HTTP 200 /health"
else
  echo "  Проблема: /health -> $code (сервис не слушает 8080 или не тот venv/путь)"
fi
echo ""
echo "--- nginx: root (должен быть $RP для try_files) ---"
if command -v nginx >/dev/null 2>&1; then
  if out=$(nginx -T 2>/dev/null); then
    echo "$out" | grep -E '^\s*root\s+|server_name|listen\s|location\s' | head -n 50
  else
    echo "  (нужен sudo для nginx -T) — выполните на сервере: sudo nginx -T | grep -E root\\|server_name"
  fi
  if [[ -d "$RP" && -f "$RP/index.html" ]]; then
    if sudo -u www-data test -r "$RP/index.html" 2>/dev/null; then
      echo "  OK: www-data читает index.html (nginx отдаст 200)"
    else
      echo "  ОШИБКА: www-data НЕ читает index.html — типично chmod 600 + mktemp; chmod 644 и см. upload.sh"
    fi
  fi
else
  echo "  (nginx не найден в PATH)"
fi
REMOTE

echo ""
echo "==> Сводка: upload.sh заливает в $REMOTE_PATH. На сервере тот же путь в nginx root"
echo "   и $REMOTE_PATH/server в systemd. Если папку переносили — обновите все три."
exit 0
