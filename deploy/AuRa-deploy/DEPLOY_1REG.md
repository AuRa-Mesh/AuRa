## Деплой AuRa-deploy на 1reg (VPS — рекомендовано)

### 1) Что у вас должно быть

- VPS в 1reg (Linux Ubuntu 22.04/24.04)
- Домен (например `example.ru`)
- Доступ по SSH (логин/пароль или SSH ключ)

Почему **VPS**: FastAPI/uvicorn — это backend. На обычном “shared hosting” (только статический сайт / PHP) это обычно не запускается.

---

### 2) Заливка файлов на сервер

На VPS создайте папку приложения, например:

```bash
mkdir -p /opt/aura
```

Скопируйте папку **`AuRa-deploy/`** на сервер (через `scp` или SFTP).
В итоге на сервере должно быть:

```
/opt/aura/AuRa-deploy/
  index.html
  auth.html
  profile.html
  scan.html
  landing.css
  landing.js
  assets/
  server/
    app.py
    requirements.txt
```

В браузере используются короткие URL: `/`, `/auth`, `/profile`, `/scan` (nginx: `try_files $uri $uri.html`). Старые адреса `…/auth.html` и `/site/…` перенаправляются на короткие (см. `app.py`).

---

### 3) Установка зависимостей и запуск API

```bash
cd /opt/aura/AuRa-deploy/server
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Обязательно задайте секрет JWT. Для продакшена задайте и секрет проверки пароля ноды (тот же байтовый смысл, что в прошивке/приложении — см. `NodePasswordGenerator.kt`); иначе используется значение по умолчанию из репозитория.

```bash
export AURA_JWT_SECRET="ВАШ_ДЛИННЫЙ_СЕКРЕТ"
export AURA_NODE_HMAC_SECRET="ВАШ_СЕКРЕТ_СОВПАДАЮЩИЙ_С_КЛИЕНТАМИ"
```

Проверка локально на сервере:

```bash
uvicorn app:app --host 127.0.0.1 --port 8080
curl http://127.0.0.1:8080/health
```

---

### 4) Systemd сервис (автозапуск)

Создайте файл:

`/etc/systemd/system/aura-api.service`

Содержимое:

```ini
[Unit]
Description=AuRa API (FastAPI)
After=network.target

[Service]
WorkingDirectory=/opt/aura/AuRa-deploy/server
Environment=AURA_JWT_SECRET=ВАШ_ДЛИННЫЙ_СЕКРЕТ
Environment=AURA_NODE_HMAC_SECRET=ВАШ_СЕКРЕТ_СОВПАДАЮЩИЙ_С_КЛИЕНТАМИ
# Опционально: защита скачивания APK (/api/apk?sig=...) — тот же ключ, что в deploy.env для upload.sh
# Environment=AURA_APK_DOWNLOAD_HMAC=ДЛИННЫЙ_СЛУЧАЙНЫЙ_КЛЮЧ
ExecStart=/opt/aura/AuRa-deploy/server/.venv/bin/uvicorn app:app --host 127.0.0.1 --port 8080
Restart=always
RestartSec=2

[Install]
WantedBy=multi-user.target
```

Запуск:

```bash
sudo systemctl daemon-reload
sudo systemctl enable aura-api
sudo systemctl start aura-api
sudo systemctl status aura-api
```

---

### 5) Nginx (домен + HTTPS + статический сайт + прокси /api)

Установка:

```bash
sudo apt update
sudo apt install -y nginx
```

Конфиг, например:

`/etc/nginx/sites-available/aura`

```nginx
limit_req_zone $binary_remote_addr zone=aura_api:10m rate=30r/s;
limit_req_zone $binary_remote_addr zone=aura_login:10m rate=2r/s;

server {
  listen 80;
  server_name example.ru www.example.ru;

  # Статика: «красивые» URL /auth, /profile, /scan (файлы auth.html, …) + корень /
  root /opt/aura/AuRa-deploy;
  index index.html;

  location = / {
    try_files /index.html =404;
  }

  # Короткие пути → якорь на главной (без этого даёт 404: try_files не знает /platform)
  # Вариант 1: готовый файл в репозитории (лучше дублировать явно в 80 и 443):
  #   include /opt/aura/AuRa-deploy/deploy/nginx-aura-shortpaths.conf;
  # Вариант 2: одна regex-строка (заменяет include; тоже ПЕРЕД location /)
  #   location ~ ^/(platform|screenshots|workflow|faq|contacts)$ {
  #     return 301 "$scheme://$host/#$1";
  #   }

  location / {
    try_files $uri $uri.html =404;
  }

  # API проксируем на uvicorn
  # Защита от перебора/флуда:
  # - /api/auth/login: жёстче (2 req/sec + burst)
  # - остальной /api/: мягче (30 req/sec)
  location = /api/auth/login {
    limit_req zone=aura_login burst=10 nodelay;
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location /api/ {
    limit_req zone=aura_api burst=60 nodelay;
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
  }

  location /health {
    proxy_pass http://127.0.0.1:8080/health;
  }
}
```

**404 на `https://сайт/platform`, `/faq`, `/contacts` и т.д.** Nginx сначала ищет **файл** по пути; **не находит** → `404` до Python. Нужен один из вариантов выше **внутри того же `server`**, с которого отвечает сайт (после **Let’s Encrypt** — чаще всего **в блоке `listen 443 ssl`**, иначе HTTP редиректит на HTTPS, а на 443 **нет** `location` для `/platform` → 404). После правок: `sudo nginx -t && sudo systemctl reload nginx`.

**Без настройки nginx** короткие пути **не** заработают: редиректы в `app.py` (uvicorn) срабатывают только если запрос **проксируют** на `8080`, а в типичной схеме **статика** отдаётся nginx с диска.

Если увидите 429/503 при нагрузке — увеличьте `burst` или `rate`.

Активируйте:

```bash
sudo ln -s /etc/nginx/sites-available/aura /etc/nginx/sites-enabled/aura
sudo nginx -t
sudo systemctl reload nginx
```

---

### 6) HTTPS (Let’s Encrypt)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d example.ru -d www.example.ru
```

После `certbot` откройте сгенерированный фрагмент (часто `/etc/nginx/sites-enabled/aura` или `…-le-ssl`) и **добавьте тот же** `include …/nginx-aura-shortpaths.conf` (или те же `location` с `return 301`) **в `server` с `listen 443 ssl`**, иначе с браузера придёте на HTTPS, а редиректы для `/platform` окажутся только в блоке `:80`.

---

### 7) Настройка приложения

В приложении → Настройки → **Отправка сообщений**:

- **URL сервера (VPS)**: `https://example.ru`
- **Режим**: Интернет / Нода / Автоматически

В режиме **Автоматически** приложение ждёт 5 секунд принятия сервером (HTTP 2xx), иначе отправляет через ноду.

---

## 403 на главной (nginx) при `root /opt/aura/AuRa-deploy`

Статика отдаёт **nginx** от имени **www-data**. Если **`index.html` с правом `600`** (часто после `rsync` с временного файла `mktemp` на 600) — **www-data не читает** → **403** на `https://домен/`, при этом **`/api` через proxy** может работать. Исправление: **`./upload.sh`** (в нём `chmod` после заливки) или вручную: `chmod 644 /opt/aura/AuRa-deploy/*.html`. Проверка: `./verify-server-paths.sh`.

## APK / прошивки: не скачивается с сайта (404, пусто)

1. **APK** кладёте в **`/opt/aura/AuRa-deploy/assets/AuRa.APK`** (в репо он в `.gitignore`). Со страницы ведёт ссылка **`/api/apk?...`**, а не прямой `/assets/…`. В `deploy.env` мoжет быть `AURA_APK_DOWNLOAD_HMAC` — `upload.sh` подставит `&sig=` в `index.html`. **Проверка `sig` на API включена только** если в **systemd** задано **два** ключа: `AURA_APK_DOWNLOAD_HMAC` и `AURA_APK_DOWNLOAD_REQUIRE_SIG=1` (по умолчанию 403 за неверный `sig` **не** выдаётся, чтобы не ломать скачивание при рассинхроне).

   Проверка: `curl -sI "https://ваш-домен/api/apk?v=1"`. Старый путь `GET /assets/AuRa.APK` в `nginx-aura-shortpaths.conf` отдаёт **404**.

   Жёсткая защита скачивания: в **override** `aura-api` — `AURA_APK_DOWNLOAD_HMAC=…` (как в `deploy.env` при `upload.sh`) **и** `AURA_APK_DOWNLOAD_REQUIRE_SIG=1`.

2. **Имя файла** на диске: `AuRa.APK` (регистр важен на Linux).

3. **Скрипт `upload.sh`**: `rsync --delete` + **`P assets/AuRa.APK`** — APK на сервере **не** сотрётся, если у вас локально нет копии. Чтобы **перезаписать** APK: положите свежий `assets/AuRa.APK` в локальный `AuRa-deploy/` и снова запустите **`./upload.sh`**. `upload.sh` в конце подменяет `index.html` с актуальным `?v=...&sig=...` (если задан `AURA_APK_DOWNLOAD_HMAC`).

4. **Nginx** отдаёт статику из `root` (см. выше). При необходимости в `server {` можно добавить `types { application/vnd.android.package-archive apk; }`.

5. **reg.ru** здесь обычно только **DNS/домен**; файлы лежат на **VPS** в `assets/`, не в панели «хостинг без сервера».

---

## База профилей (`server/aura_site.db`) и `upload.sh`

Файл **`server/aura_site.db`** (SQLite) хранит **профили, VIP, lastSync** и т.д. Он **не** в git.

При **`rsync --delete`**, если локально **нет** этой БД, файл на сервере **мог удаляться**; если локальная **dev**-копия **есть** — она могла **перезаписать** прод.

В **`upload.sh`** для `server/aura_site.db` (и при необходимости `-wal` / `-shm`) заданы **`--exclude`** (не заливать с ноутбука) и **`P` (protect)** (не удалять на сервере при заливке). После обновления скрипта переливать сайт можно без сброса пользователей.

**Рекомендация:** периодически делайте бэкап БД на VPS, например:  
`sudo cp -a /opt/aura/AuRa-deploy/server/aura_site.db /root/aura_site.db.bak.$(date +%Y%m%d)`

---

## Если у вас только “shared hosting”

На shared-хостинге обычно можно залить **только статический сайт** (HTML/CSS/JS) — он откроется, но API (`/api/*`) работать не будет.
Для полного функционала нужен VPS или другой хостинг, где можно запускать Python (FastAPI) / Docker.

