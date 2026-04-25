# AuRa — сайт и API (deploy)

Статика (HTML/CSS/JS) и **FastAPI** в `server/`. Деплой на VPS: [DEPLOY_1REG.md](DEPLOY_1REG.md).

**Сборка Android (`AuRa.APK`)** в репозиторий не кладётся (лимит GitHub 100 МБ). Для скачивания со страницы залейте APK на сервер в `assets/` вручную или выложите **GitHub Release** и смените ссылку в `index.html`.

Клон на сервере: `git clone …` → venv в `server/` → переменные `AURA_JWT_SECRET`, `AURA_NODE_HMAC_SECRET` (и при необходимости админ).

## Быстрое обновление сайта после правок (Mac → GitHub → VPS)

Для `deploy.sh` сервер должен принимать **SSH по ключу** (без пароля): [docs/ssh-vps.md](docs/ssh-vps.md) (`authorized_keys`).

1. `cp deploy.env.example deploy.env` — вписать `AURA_DEPLOY_SSH=root@IP-или-домен` (и при необходимости `AURA_DEPLOY_PATH` / `AURA_DEPLOY_BRANCH`).
2. Закоммитить локальные изменения.
3. `chmod +x deploy.sh` (один раз)
4. `./deploy.sh` — сделает `git push`, по SSH: `git pull` на сервере и `systemctl restart aura-api`.

Скрипт **не** запустится, если есть незакоммиченные файлы. `deploy.env` в git не коммитится.
