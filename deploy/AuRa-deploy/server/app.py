import base64
import io
import json
import hashlib
import hmac
import os
import socket
import sqlite3
import time
from dataclasses import dataclass
from typing import Any, Dict, Optional

import jwt
import qrcode
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, Response, RedirectResponse
from fastapi.staticfiles import StaticFiles

APP_ROOT = os.path.dirname(os.path.abspath(__file__))
SITE_ROOT = os.path.dirname(APP_ROOT)  # AuRa-deploy/
DB_PATH = os.path.join(APP_ROOT, "aura_site.db")

JWT_SECRET = os.environ.get("AURA_JWT_SECRET", "dev-only-change-me")
JWT_ALG = "HS256"
JWT_TTL_SEC = int(os.environ.get("AURA_JWT_TTL_SEC", "2592000"))  # 30 days
# Админ-вход: на странице логина в поле Node ID — логин, в поле пароля — пароль.
# Переопределение: AURA_ADMIN_LOGIN / AURA_ADMIN_PASSWORD в окружении.
# Пустой/одни пробелы в env = дефолт (иначе админ-ветка отключается, а "root" даст Invalid nodeId: в нём нет hex 0-9a-f).
def _admin_env_or_default(key: str, default: str) -> str:
    raw = os.environ.get(key)
    if raw is None:
        return default
    s = str(raw).strip()
    return s if s else default


ADMIN_LOGIN = _admin_env_or_default("AURA_ADMIN_LOGIN", "root")
ADMIN_PASSWORD = _admin_env_or_default("AURA_ADMIN_PASSWORD", "Aura-Mesh-2026-!}")

# Matches Android NodePasswordGenerator.kt (or set AURA_NODE_HMAC_SECRET and the same value in the app).
HMAC_SECRET = (os.environ.get("AURA_NODE_HMAC_SECRET") or "AuRusMesh_NodeKey_v1_2024").encode("utf-8")
# HMAC profile QR (BuildConfig + local.properties AURA_PROFILE_QR_HMAC). Не публикуйте в JS.
PROFILE_QR_HMAC = (os.environ.get("AURA_PROFILE_QR_HMAC") or "dev-aura-profile-qr-hmac-change-with-deploy").encode("utf-8")
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"


def canonical_node_id_hex_for_hmac(node_id: str) -> str:
    s = (node_id or "").strip()
    if s.lower().startswith("0x"):
        s = s[2:]
    if s.startswith("!"):
        s = s[1:]
    hex_only = "".join(ch for ch in s if ch.isdigit() or ("a" <= ch.lower() <= "f"))
    if not hex_only:
        return ""
    if len(hex_only) > 8:
        core = hex_only[-8:]
    elif len(hex_only) < 8:
        core = hex_only.rjust(8, "0")
    else:
        core = hex_only
    return core.upper()


def normalize_password_input(p: str) -> str:
    return (p or "").strip().upper().replace("-", "").replace(" ", "")


def generate_password(node_id: str) -> str:
    normalized = canonical_node_id_hex_for_hmac(node_id)
    mac = hmac.new(HMAC_SECRET, normalized.encode("utf-8"), hashlib.sha256).digest()
    out = []
    for i in range(8):
        if i == 4:
            out.append("-")
        out.append(ALPHABET[mac[i] % len(ALPHABET)])
    return "".join(out)


def verify_password(node_id: str, password: str) -> bool:
    return normalize_password_input(generate_password(node_id)) == normalize_password_input(password)


def now_ms() -> int:
    return int(time.time() * 1000)


def b64url_profile_for_export_qr(node_id_hex: str, profile_json: str) -> str:
    try:
        o = json.loads(profile_json) if (profile_json or "").strip() else {}
    except Exception:
        o = {}
    if not isinstance(o, dict):
        o = {}
    o["nodeId"] = (node_id_hex or "").strip().upper()
    o.pop("nodeIdHex", None)
    s = json.dumps(o, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    return base64.urlsafe_b64encode(s.encode("utf-8")).decode("ascii").rstrip("=")


def _profile_qr_digest(profile_b64url: str) -> bytes:
    msg = ("v1\n" + profile_b64url).encode("utf-8")
    return hmac.new(PROFILE_QR_HMAC, msg, hashlib.sha256).digest()


def profile_qr_sign_b64url(profile_b64url: str) -> str:
    d = _profile_qr_digest(profile_b64url)
    return base64.urlsafe_b64encode(d).decode("ascii").rstrip("=")


def profile_qr_sig_verify(profile_b64url: str, sig_b64url: str) -> bool:
    if not profile_b64url or not sig_b64url:
        return False
    try:
        t = (sig_b64url or "").strip().replace("-", "+").replace("_", "/")
        pad = (4 - len(t) % 4) % 4
        got = base64.b64decode(t + ("=" * pad), validate=True)
    except Exception:
        return False
    return hmac.compare_digest(_profile_qr_digest(profile_b64url), got)


def build_profile_export_qr_line(node_id_hex: str, profile_json: str) -> Optional[str]:
    b64 = b64url_profile_for_export_qr(node_id_hex, profile_json)
    if not b64:
        return None
    sig = profile_qr_sign_b64url(b64)
    nid = (node_id_hex or "").strip().upper()
    return f"aura://profile?nodeId={nid}&profile={b64}&sig={sig}"


def profile_json_without_achievements(s: str) -> str:
    """Сайт не хранит/не отдаёт блок достижений; приложение по-прежнему может прислать поле в JSON."""
    if not s or not str(s).strip():
        return s
    try:
        o = json.loads(s)
    except Exception:
        return s
    if not isinstance(o, dict) or "achievements" not in o:
        return s
    o2 = {k: v for k, v in o.items() if k != "achievements"}
    return json.dumps(o2, ensure_ascii=False, separators=(",", ":"))

def guess_lan_ip() -> Optional[str]:
    """
    Best-effort local LAN IP for QR base url.
    Works without sending packets (connect() on UDP just sets route).
    """
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            if ip and not ip.startswith("127."):
                return ip
        finally:
            s.close()
    except Exception:
        return None
    return None


def db() -> sqlite3.Connection:
    con = sqlite3.connect(DB_PATH)
    con.row_factory = sqlite3.Row
    return con


def init_db() -> None:
    con = db()
    try:
        con.execute(
            """
            create table if not exists users (
              node_id text primary key,
              profile_json text not null,
              updated_at_ms integer not null
            )
            """
        )
        con.execute(
            """
            create table if not exists pairs (
              pair_id text primary key,
              node_id text not null,
              secret_salt text not null,
              secret_hash text not null,
              status text not null,
              payload_json text,
              created_at_ms integer not null,
              expires_at_ms integer not null,
              completed_at_ms integer,
              claimed_at_ms integer
            )
            """
        )
        con.commit()
    finally:
        con.close()
    _migrate_users_table()


def _migrate_users_table() -> None:
    con = db()
    try:
        info = {row[1] for row in con.execute("pragma table_info(users)").fetchall()}
        if "last_login_at_ms" not in info:
            con.execute("alter table users add column last_login_at_ms integer not null default 0")
            con.commit()
    finally:
        con.close()


def json_default_profile(node_id: str) -> str:
    # This JSON is what the app should push/claim. Extend as needed.
    return (
        '{"nodeId":"%s",'
        '"vip":{"active":false,"untilMs":null,"remainingMs":0},'
        '"lastSyncMs":null}'
    ) % canonical_node_id_hex_for_hmac(node_id)


def get_or_create_user(node_id: str) -> Dict[str, Any]:
    nid = canonical_node_id_hex_for_hmac(node_id)
    con = db()
    try:
        row = con.execute("select node_id, profile_json from users where node_id = ?", (nid,)).fetchone()
        if row:
            return {"nodeId": row["node_id"], "profileJson": row["profile_json"]}
        prof = json_default_profile(nid)
        now = now_ms()
        con.execute(
            "insert into users(node_id, profile_json, updated_at_ms, last_login_at_ms) values(?,?,?,?)",
            (nid, prof, now, now),
        )
        con.commit()
        return {"nodeId": nid, "profileJson": prof}
    finally:
        con.close()


def update_user_profile(node_id: str, profile_json: str) -> None:
    profile_json = profile_json_without_achievements(profile_json)
    nid = canonical_node_id_hex_for_hmac(node_id)
    con = db()
    try:
        t = now_ms()
        con.execute(
            "insert into users(node_id, profile_json, updated_at_ms, last_login_at_ms) values(?,?,?,?) "
            "on conflict(node_id) do update set profile_json=excluded.profile_json, updated_at_ms=excluded.updated_at_ms",
            (nid, profile_json, t, t),
        )
        con.commit()
    finally:
        con.close()


def record_user_last_login(node_id_hex: str) -> None:
    con = db()
    try:
        con.execute(
            "update users set last_login_at_ms = ? where node_id = ?",
            (now_ms(), node_id_hex),
        )
        con.commit()
    finally:
        con.close()


def rand_b64url(n_bytes: int = 24) -> str:
    return base64.urlsafe_b64encode(os.urandom(n_bytes)).decode("ascii").rstrip("=")


def pbkdf2_sha256(text: str, salt: str, iters: int = 120_000) -> str:
    dk = hashlib.pbkdf2_hmac("sha256", text.encode("utf-8"), salt.encode("utf-8"), iters, dklen=32)
    return base64.urlsafe_b64encode(dk).decode("ascii").rstrip("=")


@dataclass
class AuthCtx:
    node_id: str
    is_admin: bool = False


def auth_required(authorization: Optional[str] = Header(default=None)) -> AuthCtx:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = authorization.split(" ", 1)[1].strip()
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALG])
    except jwt.PyJWTError:
        raise HTTPException(status_code=401, detail="Invalid token")
    node_id = payload.get("nodeId")
    if not node_id:
        raise HTTPException(status_code=401, detail="Invalid token payload")
    is_admin = bool(payload.get("isAdmin"))
    if is_admin:
        return AuthCtx(node_id=str(node_id), is_admin=True)
    return AuthCtx(node_id=canonical_node_id_hex_for_hmac(str(node_id)))


def admin_required(ctx: AuthCtx = Depends(auth_required)) -> AuthCtx:
    if not ctx.is_admin:
        raise HTTPException(status_code=403, detail="Admin access required")
    return ctx


init_db()
app = FastAPI(title="AuRa Deploy Site Server", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> Dict[str, Any]:
    return {"ok": True, "ts": now_ms()}

@app.get("/api/qr")
def api_qr(text: str) -> Response:
    # PNG QR, generated locally (no CDN dependency).
    if not text or len(text) > 2048:
        raise HTTPException(status_code=400, detail="Bad text")
    qr = qrcode.QRCode(
        version=None,
        error_correction=qrcode.constants.ERROR_CORRECT_M,
        box_size=8,
        border=2,
    )
    qr.add_data(text)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return Response(content=buf.getvalue(), media_type="image/png")


def _expected_apk_download_sig() -> str:
    """HMAC-hex, если задан AURA_APK_DOWNLOAD_HMAC; иначе пусто = выдача без sig в query."""
    k = (os.environ.get("AURA_APK_DOWNLOAD_HMAC") or "").strip()
    if not k:
        return ""
    return hmac.new(k.encode("utf-8"), b"aura-apk-dl-v1", hashlib.sha256).hexdigest()


def _apk_download_sig_enforcement_enabled() -> bool:
    """
    Секрет в окружении (для build ссылки в upload.sh) не должен сам по себе ломать скачивание:
    403 на /api/apk только если явно AURA_APK_DOWNLOAD_REQUIRE_SIG=1 (и задан AURA_APK_DOWNLOAD_HMAC).
    """
    if not (os.environ.get("AURA_APK_DOWNLOAD_HMAC") or "").strip():
        return False
    v = (os.environ.get("AURA_APK_DOWNLOAD_REQUIRE_SIG") or "").strip().lower()
    return v in ("1", "true", "yes", "on")


@app.get("/api/apk")
def download_aura_apk(request: Request) -> FileResponse:
    """
    Скачивание AuRa.APK.
    Проверка ?sig= — только при AURA_APK_DOWNLOAD_HMAC + AURA_APK_DOWNLOAD_REQUIRE_SIG=1
    (и тот же HMAC, что в deploy.env при upload.sh).
    Прямой путь /assets/AuRa.APK в nginx лучше отключить.
    """
    if _apk_download_sig_enforcement_enabled():
        need = _expected_apk_download_sig()
        if need:
            got = (request.query_params.get("sig") or "").strip()
            if not got or len(got) != len(need) or not hmac.compare_digest(got, need):
                raise HTTPException(
                    status_code=403,
                    detail="APK: нужен корректный sig= или снимите AURA_APK_DOWNLOAD_REQUIRE_SIG / HMAC",
                )
    p = os.path.join(SITE_ROOT, "assets", "AuRa.APK")
    if not os.path.isfile(p):
        raise HTTPException(
            status_code=404,
            detail="Файл assets/AuRa.APK на сервере не найден",
        )
    return FileResponse(
        p,
        filename="AuRa.APK",
        media_type="application/vnd.android.package-archive",
        content_disposition_type="attachment",
    )


@app.post("/api/auth/login")
def login(body: Dict[str, Any]) -> Dict[str, Any]:
    node_id = str(body.get("nodeId") or "").strip()
    password = str(body.get("password") or "")
    if (
        ADMIN_LOGIN
        and ADMIN_PASSWORD
        and node_id.casefold() == ADMIN_LOGIN.casefold()
        and password == ADMIN_PASSWORD
    ):
        token = jwt.encode(
            {
                "nodeId": "ADMIN",
                "isAdmin": True,
                "iat": int(time.time()),
                "exp": int(time.time()) + JWT_TTL_SEC,
            },
            JWT_SECRET,
            algorithm=JWT_ALG,
        )
        return {"token": token, "nodeId": "ADMIN", "isAdmin": True}
    if not canonical_node_id_hex_for_hmac(node_id):
        raise HTTPException(status_code=400, detail="Invalid nodeId")
    if not verify_password(node_id, password):
        raise HTTPException(status_code=401, detail="Wrong nodeId/password")

    u = get_or_create_user(node_id)
    record_user_last_login(u["nodeId"])
    token = jwt.encode(
        {"nodeId": u["nodeId"], "iat": int(time.time()), "exp": int(time.time()) + JWT_TTL_SEC},
        JWT_SECRET,
        algorithm=JWT_ALG,
    )
    return {"token": token, "nodeId": u["nodeId"]}


@app.get("/api/me")
def me(ctx: AuthCtx = Depends(auth_required)) -> Dict[str, Any]:
    if ctx.is_admin:
        return {"nodeId": "ADMIN", "profileJson": "{}", "isAdmin": True}
    u = get_or_create_user(ctx.node_id)
    pj = profile_json_without_achievements(u["profileJson"] or "")
    return {
        "nodeId": u["nodeId"],
        "profileJson": pj,
        "isAdmin": False,
        "profileExportQr": build_profile_export_qr_line(u["nodeId"], pj),
    }


@app.get("/api/admin/users")
def admin_users(_ctx: AuthCtx = Depends(admin_required)) -> Dict[str, Any]:
    con = db()
    try:
        rows = con.execute(
            "select node_id, profile_json, updated_at_ms, coalesce(last_login_at_ms, 0) as last_login "
            "from users order by coalesce(last_login_at_ms, 0) desc, updated_at_ms desc"
        ).fetchall()
        return {
            "items": [
                {
                    "nodeId": row["node_id"],
                    "updatedAtMs": row["updated_at_ms"],
                    "lastLoginAtMs": int(row["last_login"] or 0),
                    "profileJson": profile_json_without_achievements(row["profile_json"] or ""),
                    "profileExportQr": build_profile_export_qr_line(
                        row["node_id"],
                        profile_json_without_achievements(row["profile_json"] or ""),
                    ),
                }
                for row in rows
            ]
        }
    finally:
        con.close()

@app.post("/api/profile/import")
def profile_import(body: Dict[str, Any], ctx: AuthCtx = Depends(auth_required)) -> Dict[str, Any]:
    if ctx.is_admin:
        raise HTTPException(status_code=403, detail="Admin session cannot import profiles")

    b64 = str(body.get("profileB64Url") or body.get("profile") or "").strip()
    sig = str(body.get("profileQrSig") or body.get("sig") or "").strip()
    profile_json: str
    if b64 and sig:
        if not profile_qr_sig_verify(b64, sig):
            raise HTTPException(status_code=400, detail="Invalid profile signature")
        try:
            pad = (4 - len(b64) % 4) % 4
            raw = base64.urlsafe_b64decode(b64 + ("=" * pad))
            profile_json = raw.decode("utf-8")
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid profileB64Url")
    else:
        raise HTTPException(
            status_code=400,
            detail="Need signed profile from QR: profileB64Url + profileQrSig (update app and site)",
        )
    # Only allow writing your own profile; enforce nodeId match inside JSON too.
    try:
        parsed = json.loads(profile_json)
        claimed = canonical_node_id_hex_for_hmac(str((parsed or {}).get("nodeId") or ""))
        if not claimed or claimed != ctx.node_id:
            raise HTTPException(status_code=400, detail="nodeId mismatch")
    except HTTPException:
        raise
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid profile JSON in QR")
    update_user_profile(ctx.node_id, profile_json)
    return {"ok": True}


@app.delete("/api/profile")
def profile_delete(ctx: AuthCtx = Depends(auth_required)) -> Dict[str, Any]:
    """Удаляет строку пользователя и связанные сессии синхронизации (pair) с сервера."""
    if ctx.is_admin:
        raise HTTPException(status_code=403, detail="Admin session cannot delete site profile")
    nid = ctx.node_id
    con = db()
    try:
        con.execute("delete from pairs where node_id = ?", (nid,))
        cur = con.execute("delete from users where node_id = ?", (nid,))
        con.commit()
        return {"ok": True, "deleted": cur.rowcount > 0}
    finally:
        con.close()


@app.post("/api/pair/start")
def pair_start(request: Request, ctx: AuthCtx = Depends(auth_required)) -> Dict[str, Any]:
    if ctx.is_admin:
        raise HTTPException(status_code=403, detail="Admin session cannot start device pairing")
    pair_id = rand_b64url(12)
    secret = rand_b64url(24)
    salt = rand_b64url(12)
    secret_hash = pbkdf2_sha256(secret, salt)
    created = now_ms()
    expires = created + 10 * 60 * 1000  # 10 minutes

    con = db()
    try:
        con.execute(
            "insert into pairs(pair_id,node_id,secret_salt,secret_hash,status,created_at_ms,expires_at_ms) "
            "values(?,?,?,?,?,?,?)",
            (pair_id, ctx.node_id, salt, secret_hash, "pending", created, expires),
        )
        con.commit()
    finally:
        con.close()

    base = str(request.base_url).rstrip("/")
    # If server is opened via localhost, phone can't reach 127.0.0.1 — replace with LAN IP.
    if "://127.0.0.1" in base or "://localhost" in base:
        lan = guess_lan_ip()
        if lan:
            base = base.replace("://127.0.0.1", f"://{lan}").replace("://localhost", f"://{lan}")
    qr_text = f"aura://pair?base={base}&pairId={pair_id}&secret={secret}"
    return {"pairId": pair_id, "secret": secret, "qrText": qr_text, "expiresAtMs": expires}


def verify_pair_secret(pair_row: sqlite3.Row, secret: str) -> bool:
    salt = pair_row["secret_salt"]
    expected = pair_row["secret_hash"]
    got = pbkdf2_sha256(secret, salt)
    return hmac.compare_digest(expected, got)


@app.get("/api/pair/status")
def pair_status(pairId: str, ctx: AuthCtx = Depends(auth_required)) -> Dict[str, Any]:
    if ctx.is_admin:
        raise HTTPException(status_code=403, detail="Admin session cannot poll pairing")
    con = db()
    try:
        row = con.execute("select * from pairs where pair_id = ?", (pairId,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Pair not found")
        if row["node_id"] != ctx.node_id:
            raise HTTPException(status_code=403, detail="Forbidden")
        return {
            "pairId": row["pair_id"],
            "status": row["status"],
            "expiresAtMs": row["expires_at_ms"],
            "completedAtMs": row["completed_at_ms"],
            "claimedAtMs": row["claimed_at_ms"],
        }
    finally:
        con.close()


@app.post("/api/pair/push")
def pair_push(body: Dict[str, Any]) -> Dict[str, Any]:
    pair_id = str(body.get("pairId") or "")
    secret = str(body.get("secret") or "")
    payload_json = body.get("payloadJson")
    if not pair_id or not secret:
        raise HTTPException(status_code=400, detail="Missing pairId/secret")
    if payload_json is None or not isinstance(payload_json, str) or not payload_json.strip():
        raise HTTPException(status_code=400, detail="Missing payloadJson")

    con = db()
    try:
        row = con.execute("select * from pairs where pair_id = ?", (pair_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Pair not found")
        if row["expires_at_ms"] < now_ms():
            raise HTTPException(status_code=410, detail="Pair expired")
        if not verify_pair_secret(row, secret):
            raise HTTPException(status_code=401, detail="Wrong secret")

        # Enforce that payload_json belongs to the same node as this pair.
        try:
            import json

            parsed = json.loads(payload_json)
            claimed = canonical_node_id_hex_for_hmac(str((parsed or {}).get("nodeId") or ""))
            if not claimed or claimed != row["node_id"]:
                raise HTTPException(status_code=400, detail="nodeId mismatch")
        except HTTPException:
            raise
        except Exception:
            raise HTTPException(status_code=400, detail="Invalid payloadJson")

        con.execute(
            "update pairs set status=?, payload_json=?, completed_at_ms=? where pair_id=?",
            ("completed", payload_json, now_ms(), pair_id),
        )
        con.commit()

        update_user_profile(row["node_id"], payload_json)
        return {"ok": True}
    finally:
        con.close()


@app.post("/api/pair/claim")
def pair_claim(body: Dict[str, Any]) -> Dict[str, Any]:
    pair_id = str(body.get("pairId") or "")
    secret = str(body.get("secret") or "")
    if not pair_id or not secret:
        raise HTTPException(status_code=400, detail="Missing pairId/secret")

    con = db()
    try:
        row = con.execute("select * from pairs where pair_id = ?", (pair_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="Pair not found")
        if row["expires_at_ms"] < now_ms():
            raise HTTPException(status_code=410, detail="Pair expired")
        if not verify_pair_secret(row, secret):
            raise HTTPException(status_code=401, detail="Wrong secret")

        node_id = row["node_id"]
        if row["payload_json"]:
            profile_json = row["payload_json"]
        else:
            u = get_or_create_user(node_id)
            profile_json = u["profileJson"]

        con.execute("update pairs set status=?, claimed_at_ms=? where pair_id=?", ("claimed", now_ms(), pair_id))
        con.commit()
        return {"nodeId": node_id, "profileJson": profile_json_without_achievements(profile_json)}
    finally:
        con.close()


# Статика с корня: URL /auth → auth.html, / → index.html (html=True). Маршруты /api/* и /health объявлены выше.
# Старые ссылки /site/*.html и *.html ведём на короткие URL.
@app.get("/site/index.html")
def _legacy_site_index() -> Any:
    return RedirectResponse(url="/", status_code=301)


@app.get("/site/auth.html")
def _legacy_site_auth() -> Any:
    return RedirectResponse(url="/auth", status_code=301)


@app.get("/site/profile.html")
def _legacy_site_profile() -> Any:
    return RedirectResponse(url="/profile", status_code=301)


@app.get("/site/scan.html")
def _legacy_site_scan() -> Any:
    return RedirectResponse(url="/scan", status_code=301)


@app.get("/index.html")
def _legacy_index() -> Any:
    return RedirectResponse(url="/", status_code=301)


@app.get("/auth.html")
def _legacy_auth() -> Any:
    return RedirectResponse(url="/auth", status_code=301)


@app.get("/profile.html")
def _legacy_profile() -> Any:
    return RedirectResponse(url="/profile", status_code=301)


@app.get("/scan.html")
def _legacy_scan() -> Any:
    return RedirectResponse(url="/scan", status_code=301)


def _redirect_landing_anchor(fragment: str) -> RedirectResponse:
    return RedirectResponse(url=f"/#{fragment}", status_code=301)


@app.get("/platform")
def _anchor_platform() -> Any:
    return _redirect_landing_anchor("platform")


@app.get("/screenshots")
def _anchor_screenshots() -> Any:
    return _redirect_landing_anchor("screenshots")


@app.get("/workflow")
def _anchor_workflow() -> Any:
    return _redirect_landing_anchor("workflow")


@app.get("/faq")
def _anchor_faq() -> Any:
    return _redirect_landing_anchor("faq")


@app.get("/contacts")
def _anchor_contacts() -> Any:
    return _redirect_landing_anchor("contacts")


app.mount("/", StaticFiles(directory=SITE_ROOT, html=True), name="static")

