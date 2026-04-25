#!/usr/bin/env bash
# Быстрая заливка текущей папки на VPS (rsync), без git.
# Настройка: cp deploy.env.example deploy.env
# Запуск:  ./upload.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -f "$ROOT/deploy.env" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT/deploy.env"
else
  echo "Скопируйте deploy.env.example в deploy.env и укажите AURA_DEPLOY_SSH (например root@aura-mesh.ru)"
  exit 1
fi

SSH_TARGET="${AURA_DEPLOY_SSH:-}"
REMOTE_PATH="${AURA_DEPLOY_PATH:-/opt/aura/AuRa-deploy}"

if [[ -z "$SSH_TARGET" ]]; then
  echo "Error: AURA_DEPLOY_SSH is not set in deploy.env"
  exit 1
fi

SSH_BASE=(ssh -o BatchMode=yes)
if [[ -n "${AURA_DEPLOY_IDENTITY:-}" && -f "${AURA_DEPLOY_IDENTITY/#\~/$HOME}" ]]; then
  _id="${AURA_DEPLOY_IDENTITY/#\~/$HOME}"
  SSH_BASE=(ssh -o BatchMode=yes -i "$_id")
elif [[ -f "$HOME/.ssh/id_ed25519_aura" ]]; then
  SSH_BASE=(ssh -o BatchMode=yes -i "$HOME/.ssh/id_ed25519_aura")
fi

echo "==> prepare index + landing (номер сборки, ссылка /api/apk?…)"
APK_FILE="$ROOT/assets/AuRa.APK"
APK_STATE="$ROOT/.apk_last_upload_sha"
APK_N_FILE="$ROOT/apk_build.txt"
INDEX_TMP=$(mktemp)
LANDING_TMP=$(mktemp)
APK_BUILD=0
if [[ -f "$APK_N_FILE" ]]; then
  read -r APK_BUILD < "$APK_N_FILE" || true
fi
[[ "$APK_BUILD" =~ ^[0-9]+$ ]] || APK_BUILD=0
if [[ -f "$APK_FILE" ]]; then
  SHA=$(shasum -a 256 "$APK_FILE" | awk '{print $1}')
  LAST=$(cat "$APK_STATE" 2>/dev/null | head -1 | tr -d ' \n' || true)
  if [[ "$SHA" != "$LAST" ]]; then
    APK_BUILD=$((APK_BUILD + 1))
    echo "$APK_BUILD" > "$APK_N_FILE"
    echo "$SHA" > "$APK_STATE"
    echo "    => новый AuRa.APK — номер сборки на сайте: $APK_BUILD"
  else
    echo "    (тот же AuRa.APK по SHA — номер сборки $APK_BUILD)"
  fi
else
  echo "    (нет локального $APK_FILE — номер $APK_BUILD в тексте кнопки не меняли)"
fi
# Кэш ссылки на скачивание: v=<номер сборки>; HMAC-опция без изменений.
APK_QS="v=$APK_BUILD"
if [[ -n "${AURA_APK_DOWNLOAD_HMAC:-}" ]]; then
  SIG=$(AURA_APK_DOWNLOAD_HMAC="$AURA_APK_DOWNLOAD_HMAC" python3 -c "import hmac,hashlib,os; k=os.environ.get('AURA_APK_DOWNLOAD_HMAC','').encode('utf-8'); print(hmac.new(k, b'aura-apk-dl-v1', hashlib.sha256).hexdigest())")
  APK_QS="v=$APK_BUILD&sig=${SIG}"
  echo "    (HMAC: в index подставляется &sig=…)"
else
  echo "    (без AURA_APK_DOWNLOAD_HMAC: только v=$APK_BUILD в query)"
fi
export ROOT APK_QS APK_BUILD INDEX_TMP LANDING_TMP
python3 - <<'PY'
import os, pathlib
root = pathlib.Path(os.environ["ROOT"])
qs = os.environ.get("APK_QS", "")
b = os.environ.get("APK_BUILD", "0")
for name, key in (("index.html", "INDEX_TMP"), ("landing.js", "LANDING_TMP")):
    t = (root / name).read_text(encoding="utf-8")
    t = t.replace("__APK_QS__", qs)
    t = t.replace("__APK_BUILD__", b)
    pathlib.Path(os.environ[key]).write_text(t, encoding="utf-8")
PY
chmod 644 "$INDEX_TMP" "$LANDING_TMP"

echo "==> rsync → $SSH_TARGET:$REMOTE_PATH"
# APK/прошивки не в git; без P --delete с машины без этих файлов сотрёт их на сервере.
# БД SQLite: не в git; --delete иначе УДАЛИТ БД на сервере, если локально нет файла; локальная
# (dev) копия не должна затирать прод — exclude + protect.
rsync -avz --delete \
  -e "${SSH_BASE[*]}" \
  --filter 'P assets/AuRa.APK' \
  --filter 'P assets/Node_Firmware_for_T114.uf2' \
  --filter 'P assets/Node_Firmware_for_HELTEC_Pocket.uf2' \
  --filter 'P server/aura_site.db' \
  --filter 'P server/aura_site.db-wal' \
  --filter 'P server/aura_site.db-shm' \
  --exclude 'server/aura_site.db' \
  --exclude 'server/aura_site.db-wal' \
  --exclude 'server/aura_site.db-shm' \
  --exclude '.git' \
  --exclude '.venv' \
  --exclude '__pycache__' \
  --exclude 'deploy.env' \
  --exclude '.deploy.env' \
  --exclude '.DS_Store' \
  --exclude '*.pyc' \
  --exclude 'index.html' \
  --exclude 'landing.js' \
  "$ROOT"/ "$SSH_TARGET":"$REMOTE_PATH"/

# Подмена index + landing: __APK_QS__, __APK_BUILD__
rsync -avz -e "${SSH_BASE[*]}" "$INDEX_TMP" "$SSH_TARGET":"$REMOTE_PATH"/index.html
rsync -avz -e "${SSH_BASE[*]}" "$LANDING_TMP" "$SSH_TARGET":"$REMOTE_PATH"/landing.js
rm -f "$INDEX_TMP" "$LANDING_TMP"

echo "==> права на статику (чтение для nginx www-data, не 600)"
"${SSH_BASE[@]}" "$SSH_TARGET" bash -s -- "$REMOTE_PATH" <<'EOS'
set -e
D="${1:?}"
for g in "$D"/*.html "$D"/*.js "$D"/*.css; do
  [ -e "$g" ] || continue
  chmod 644 "$g"
done
if [ -d "$D/assets" ]; then
  find "$D/assets" -type f -exec chmod 644 {} \;
  find "$D/assets" -type d -exec chmod 755 {} \;
fi
EOS

echo "==> systemctl restart aura-api"
"${SSH_BASE[@]}" "$SSH_TARGET" 'systemctl restart aura-api && systemctl is-active aura-api && echo "aura-api: active"'

echo "==> done"
