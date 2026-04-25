# AuRa-deploy: локальный запуск сайта с авторизацией и QR синхронизацией

Сайт сам по себе статический, но **авторизация и синхронизация через QR требуют локального сервера**.

## Запуск

Переменные окружения (опционально): `AURA_JWT_SECRET`, `AURA_NODE_HMAC_SECRET` (ключ HMAC для пароля ноды; должен совпадать с клиентом, иначе см. значение по умолчанию в `app.py`).

```bash
# Локальная папка деплоя: …/AndroidStudioProjects/AuRa/AuRa/deploy/AuRa-deploy
cd "/Users/percy/AndroidStudioProjects/AuRa/AuRa/deploy/AuRa-deploy"
python3 -m pip install -r server/requirements.txt
python3 -m uvicorn server.app:app --reload --port 8000
```

(Из Android-проекта: `cd deploy/AuRa-deploy` — та же папка. На production см. [DEPLOY_1REG.md](../DEPLOY_1REG.md) — `uvicorn` из каталога `server/`.)

Открыть в браузере (короткие пути; `*.html` редирект на них):

- `http://127.0.0.1:8000/` — лендинг
- `http://127.0.0.1:8000/auth` — вход
- `http://127.0.0.1:8000/profile` — профиль + QR
- `http://127.0.0.1:8000/scan` — сканер

## QR синхронизация (для приложения)

QR содержит строку:

`aura://pair?pairId=...&secret=...`

Приложение должно:

- **перенести данные на сайт**: `POST /api/pair/push` `{ pairId, secret, payloadJson }`
- **забрать данные с сайта**: `POST /api/pair/claim` `{ pairId, secret }`

База данных: `server/aura_site.db` (SQLite).

