const LS_TOKEN = "aura_site_token_v1";
const LS_PAIR = "aura_site_pair_v1";

function showErr(msg) {
  const inAdmin = typeof isAdminSession !== "undefined" && isAdminSession;
  const box = inAdmin ? document.getElementById("adminErrBox") : document.getElementById("errBox");
  if (!box) return;
  box.style.display = msg ? "block" : "none";
  box.textContent = msg || "";
  if (inAdmin) {
    const u = document.getElementById("errBox");
    if (u) u.style.display = "none";
  }
}

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val == null ? "—" : String(val);
}

function safeJsonParse(s) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

/** Абсолютное время окончания VIP: untilMs, иначе lastSyncMs + remainingMs (остаток на момент синхронизации в приложении). */
function vipEndMsFromProfile(obj) {
  const o = obj && typeof obj === "object" ? obj : {};
  const v = o.vip;
  if (!v || typeof v !== "object") return 0;
  const until = Number(v.untilMs);
  if (Number.isFinite(until) && until > 0) return until;
  const last = Number(o.lastSyncMs) || 0;
  const rem = v.remainingMs != null ? Number(v.remainingMs) : NaN;
  if (last > 0 && Number.isFinite(rem) && rem > 0) return last + rem;
  return 0;
}

/** Старый кэш HTML мог оставлять карточку «Достижения» — убираем, если есть. */
function removeLegacyAchievementsBlock() {
  const byId = document.getElementById("achCountText");
  if (byId) {
    byId.closest(".statCard")?.remove();
  }
  document.querySelectorAll(".statLabel").forEach((el) => {
    if (el.textContent && el.textContent.includes("Достиж")) {
      el.closest(".statCard")?.remove();
    }
  });
}

/**
 * LongName / отображаемое имя из JSON профиля (как в приложении — приоритет text fields).
 * Поддерживаем несколько вариантов ключей на случай разных версий клиента/ручного JSON.
 */
function profileLongNameFromObject(obj) {
  const o = obj && typeof obj === "object" ? obj : {};
  const raw = o.longName ?? o.LongName ?? o.long_name;
  if (typeof raw === "string" && raw.trim()) return raw.trim();
  return "";
}

/** Имя в шапке профиля: LongName из JSON, иначе Node ID. */
function profileDisplayName(obj, fallbackNodeId) {
  const o = obj && typeof obj === "object" ? obj : {};
  const ln = profileLongNameFromObject(o);
  if (ln) return ln;
  const id = String(o.nodeId || o.nodeIdHex || fallbackNodeId || "").trim();
  return id || "—";
}

async function apiJson(path, opts = {}) {
  const token = localStorage.getItem(LS_TOKEN);
  if (!token) throw new Error("Нет авторизации. Войдите заново.");
  const headers = { "Content-Type": "application/json", Authorization: `Bearer ${token}`, ...(opts.headers || {}) };
  const res = await fetch(path, { ...opts, headers });
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!res.ok) {
    const msg = data && (data.detail || data.error) ? String(data.detail || data.error) : `${res.status} ${res.statusText}`;
    throw new Error(msg);
  }
  return data;
}

let pair = null; // { pairId, secret, qrText, expiresAtMs, status }
let pairPollTimer = 0;
let vipCountdownTimer = 0;
let currentVipUntilMs = null;
let isAdminSession = false;
let adminListItems = [];

function stopVipCountdown() {
  if (vipCountdownTimer) {
    clearInterval(vipCountdownTimer);
    vipCountdownTimer = 0;
  }
  currentVipUntilMs = null;
}

function formatVipText(vipUntilMs) {
  if (!vipUntilMs || !Number.isFinite(vipUntilMs)) return "не активен";
  const left = vipUntilMs - Date.now();
  if (left <= 0) return "не активен";
  const days = Math.floor(left / 86400000);
  const hours = Math.floor((left % 86400000) / 3600000);
  const mins = Math.floor((left % 3600000) / 60000);
  // Compact and readable: "осталось 3д 4ч" or "осталось 0д 12ч" or "осталось 0д 0ч 15м"
  if (days > 0) return `активен · осталось ${days}д ${hours}ч`;
  if (hours > 0) return `активен · осталось ${hours}ч ${mins}м`;
  return `активен · осталось ${mins}м`;
}

function startVipCountdown(vipUntilMs) {
  stopVipCountdown();
  if (!vipUntilMs || !Number.isFinite(vipUntilMs)) {
    setText("vipText", "не активен");
    return;
  }
  currentVipUntilMs = vipUntilMs;
  const tick = () => {
    setText("vipText", formatVipText(currentVipUntilMs));
    if (currentVipUntilMs - Date.now() <= 0) stopVipCountdown();
  };
  tick();
  // Update every 10 seconds for smoother countdown without being noisy.
  vipCountdownTimer = setInterval(tick, 10000);
}

function stopAutoPoll() {
  if (pairPollTimer) {
    clearInterval(pairPollTimer);
    pairPollTimer = 0;
  }
}

function startAutoPoll() {
  stopAutoPoll();
  if (!pair?.pairId) return;
  const status = String(pair.status || "").toLowerCase();
  if (status && status !== "pending") return;

  pairPollTimer = setInterval(() => {
    void pollPair().catch(() => {});
  }, 2500);
}

function loadPairFromStorage() {
  try {
    const raw = localStorage.getItem(LS_PAIR);
    if (!raw) return null;
    const obj = JSON.parse(raw);
    if (!obj || typeof obj !== "object") return null;
    if (!obj.pairId || !obj.qrText) return null;
    return obj;
  } catch {
    return null;
  }
}

function savePairToStorage(p) {
  if (!p) {
    localStorage.removeItem(LS_PAIR);
    return;
  }
  localStorage.setItem(LS_PAIR, JSON.stringify(p));
}

function renderQr(text) {
  const img = document.getElementById("pairQrImg");
  if (!img) return;
  if (!text) {
    img.removeAttribute("src");
    return;
  }
  // Сбрасываем кэш PNG: иначе браузер мог подставлять старый QR с чужим nodeId.
  img.src = `/api/qr?text=${encodeURIComponent(text)}&_cb=${Date.now()}`;
}

function strToB64url(str) {
  const bytes = new TextEncoder().encode(String(str || ""));
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  const b64 = btoa(bin);
  return b64.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function canonicalNodeId8(raw) {
  const s = String(raw || "").trim().replace(/^0x/i, "").replace(/^!/, "");
  const hexOnly = (s.match(/[0-9a-fA-F]/g) || []).join("");
  if (!hexOnly) return "";
  const core = hexOnly.length > 8 ? hexOnly.slice(-8) : hexOnly.padStart(8, "0");
  return core.toUpperCase();
}

function auraProfileQrText(profileJson, authNodeId) {
  const obj = safeJsonParse(profileJson);
  if (!obj || typeof obj !== "object") return "";
  const auth = canonicalNodeId8(authNodeId || "");
  if (!auth) return "";
  const claimed = canonicalNodeId8(obj.nodeId || obj.nodeIdHex || "");
  if (!claimed || claimed !== auth) return "";
  // В URL и в base64-полезной нагрузке один и тот же nodeId (сессия), иначе приложение/сканер могли бы
  // получить расхождение query vs JSON (старая битая запись в БД, ручной правка JSON).
  const exportObj = { ...obj };
  exportObj.nodeId = auth;
  delete exportObj.nodeIdHex;
  const b64url = strToB64url(JSON.stringify(exportObj));
  return `aura://profile?nodeId=${auth}&profile=${b64url}`;
}

function renderRestoreQr(text) {
  setRestoreQrImage(document.getElementById("restoreQrImg"), text);
}

function setRestoreQrImage(img, text) {
  if (!img) return;
  if (!text) {
    img.removeAttribute("src");
    img.removeAttribute("data-aura-qr");
    return;
  }
  img.setAttribute("data-aura-qr", text);
  // Сбрасываем кэш PNG: иначе браузер мог подставлять старый QR с чужим nodeId.
  img.src = `/api/qr?text=${encodeURIComponent(text)}&_cb=${Date.now()}`;
}

/**
 * Актуализирует QR восстановления с сервера (GET /api/me) — тот же профиль, что в БД, с подписью.
 * Вызывается после отрисовки и при возврате на вкладку, чтобы «показ кода на сайте» совпадал с сервером.
 */
async function syncRestoreQrWithServer() {
  if (typeof isAdminSession !== "undefined" && isAdminSession) return;
  try {
    const me = await apiJson("/api/me");
    if (me.isAdmin) return;
    const newQr = (me.profileExportQr && String(me.profileExportQr).trim()) || "";
    const built = auraProfileQrText(me.profileJson, me.nodeId) || "";
    const effective = (newQr || built).trim();
    if (!effective) return;
    const img = document.getElementById("restoreQrImg");
    if (!img) return;
    const cur = img.getAttribute("data-aura-qr");
    if (cur === effective) return;
    renderRestoreQr(effective);
  } catch (e) {
    /* сеть / сессия */
  }
}

function hsvToRgb(h, s, v) {
  const hh = ((h % 360) + 360) % 360;
  const c = v * s;
  const x = c * (1 - Math.abs(((hh / 60) % 2) - 1));
  const m = v - c;
  let r = 0, g = 0, b = 0;
  if (hh < 60) [r, g, b] = [c, x, 0];
  else if (hh < 120) [r, g, b] = [x, c, 0];
  else if (hh < 180) [r, g, b] = [0, c, x];
  else if (hh < 240) [r, g, b] = [0, x, c];
  else if (hh < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];
  return {
    r: Math.round((r + m) * 255),
    g: Math.round((g + m) * 255),
    b: Math.round((b + m) * 255),
  };
}

function fnv1a64HexLower(str) {
  // FNV-1a 64-bit like NodeAvatarGenerator.kt
  let seed = 1469598103934665603n;
  const prime = 1099511628211n;
  const s = String(str || "").trim().replace(/^!/, "").toLowerCase();
  if (!s) return (seed ^ 0x13579BDF2ECA8647n) & ((1n << 64n) - 1n);
  for (let i = 0; i < s.length; i++) {
    seed ^= BigInt(s.charCodeAt(i));
    seed = (seed * prime) & ((1n << 64n) - 1n);
  }
  return seed & ((1n << 64n) - 1n);
}

function renderNodeAvatarDataUrl(nodeIdHex) {
  const SIZE = 128;
  const seed = fnv1a64HexLower(nodeIdHex);
  const bits = (seed ^ (seed >> 17n) ^ (seed << 5n)) & ((1n << 64n) - 1n);
  const hueBase = Number((seed >> 8n) & 0x3ffn) / 1024 * 360;

  const c = document.createElement("canvas");
  c.width = SIZE;
  c.height = SIZE;
  const ctx = c.getContext("2d");
  if (!ctx) return "";

  // background hsv(hueBase, 0.28, 0.20)
  const bg = hsvToRgb(hueBase, 0.28, 0.20);
  ctx.fillStyle = `rgb(${bg.r},${bg.g},${bg.b})`;
  ctx.fillRect(0, 0, SIZE, SIZE);

  const n = 5;
  const cell = SIZE / n;
  const half = Math.floor((n + 1) / 2);
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < half; x++) {
      const idx = y * half + x;
      const on = ((bits >> BigInt(idx & 63)) & 1n) !== 0n;
      if (!on) continue;
      const hue = (hueBase + ((x * 31 + y * 17) % 80)) % 360;
      const val = 0.62 + Math.abs((idx % 5) * 0.04);
      const col = hsvToRgb(hue, 0.58, val);
      ctx.fillStyle = `rgb(${col.r},${col.g},${col.b})`;
      const left = x * cell;
      const top = y * cell;
      ctx.fillRect(left, top, cell, cell);
      const mx = n - 1 - x;
      if (mx !== x) {
        const lx = mx * cell;
        ctx.fillRect(lx, top, cell, cell);
      }
    }
  }

  // ring
  const ring = hsvToRgb(hueBase, 0.15, 0.88);
  ctx.strokeStyle = `rgba(${ring.r},${ring.g},${ring.b},${100 / 255})`;
  ctx.lineWidth = SIZE * 0.035;
  ctx.beginPath();
  ctx.arc(SIZE / 2, SIZE / 2, SIZE / 2 - SIZE * 0.05, 0, Math.PI * 2);
  ctx.stroke();

  return c.toDataURL("image/png");
}

function setAdminModeUI(on) {
  const userPanel = document.getElementById("userPanel");
  const adminPanel = document.getElementById("adminPanel");
  if (userPanel) userPanel.style.display = on ? "none" : "block";
  if (adminPanel) adminPanel.style.display = on ? "block" : "none";
  const title = document.querySelector(".app-title");
  if (title) title.textContent = on ? "Профили (админ)" : "Профиль";
  document.title = on ? "AuRa — admin-panel" : "AuRa — profile";
}

function filterAdminItems(items, q) {
  const s = (q || "").trim().toLowerCase();
  if (!s) return items;
  return items.filter((it) => {
    if (String(it.nodeId || "").toLowerCase().includes(s)) return true;
    if (String(it.profileJson || "").toLowerCase().includes(s)) return true;
    return false;
  });
}

function createAdminProfileCard() {
  const wrap = document.createElement("div");
  wrap.className = "panel admin-user-card";
  wrap.innerHTML = [
    '<div class="headerRow">',
    '  <div class="avatar pro-avatar"></div>',
    '  <div class="who">',
    '    <div class="nodeId pro-nodeId">—</div>',
    '    <div class="sub pro-sub">Синхронизация с приложением и VIP</div>',
    '    <div class="badgeRow">',
    '      <div class="badge"><strong>VIP</strong> <span class="pro-vip">—</span></div>',
    "    </div>",
    "  </div>",
    "</div>",
    '<p class="muted pro-srvid" style="margin:8px 0 0">Node ID (сервер, 8 hex): <strong class="pro-srv-node">—</strong></p>',
    '<p class="muted pro-login" style="margin:4px 0 0">Последний вход на сайт: —</p>',
    '<p class="muted pro-server" style="margin:4px 0 0">Профиль обновлён на сервере: —</p>',
    '<div class="grid2" style="grid-template-columns:1fr">',
    '  <div class="statCard">',
    '    <div class="statLabel">Синхронизация (профиль, lastSyncMs)</div>',
    '    <div class="statValue pro-sync" style="font-size:1.05rem;font-weight:700">—</div>',
    "  </div>",
    "</div>",
    '<div class="qrWrap">',
    '  <div class="qrBox">',
    '    <img class="pro-restore-qr" alt="QR восстановления" style="width:240px;height:240px;display:block" />',
    "  </div>",
    '  <div class="qrSide">',
    '    <p class="muted" style="margin:0 0 10px">QR восстановления для этой ноды (тот же формат, что в обычном профиле).</p>',
    '    <p class="muted pro-qr-warn" style="margin:0;display:none">nodeId в профиле не совпадает с идентификатором на сервере — QR не сформирован.</p>',
    "  </div>",
    "</div>",
    '<details class="raw-json" style="margin-top:12px">',
    '  <summary style="cursor:pointer;font-weight:600">Полный JSON профиля</summary>',
    '  <pre class="pro-json" style="margin-top:8px;padding:12px;border-radius:12px;background:rgba(0,0,0,0.3);max-height:320px;overflow:auto;font-size:0.82rem"></pre>',
    "</details>",
  ].join("");
  return wrap;
}

function applyProfileToAdminCard(wrap, profileJson, serverNodeId, updatedAtMs, lastLoginAtMs, prebuiltExportQr) {
  const raw = profileJson || "";
  const obj = safeJsonParse(raw) || {};
  const displayNodeId = obj.nodeId || obj.nodeIdHex || serverNodeId || "—";
  const titleName = profileDisplayName(obj, serverNodeId);
  const srvN = wrap.querySelector(".pro-srv-node");
  if (srvN) srvN.textContent = serverNodeId || "—";
  const proLogin = wrap.querySelector(".pro-login");
  if (proLogin) {
    proLogin.textContent =
      "Последний вход на сайт: " +
      (lastLoginAtMs != null && Number.isFinite(lastLoginAtMs) && lastLoginAtMs > 0
        ? new Date(lastLoginAtMs).toLocaleString("ru-RU")
        : "—");
  }
  const proNode = wrap.querySelector(".pro-nodeId");
  if (proNode) {
    proNode.textContent = titleName;
    proNode.title = displayNodeId && displayNodeId !== "—" ? String(displayNodeId) : "";
  }
  const vipEnd = vipEndMsFromProfile(obj);
  const vipTextEl = wrap.querySelector(".pro-vip");
  if (vipTextEl) {
    vipTextEl.textContent = formatVipText(vipEnd > 0 ? vipEnd : null);
  }
  const av = wrap.querySelector(".pro-avatar");
  if (av) {
    const vipFrame = (vipEnd > 0 && vipEnd > Date.now()) || obj?.vip?.active === true;
    av.classList.toggle("vip", !!vipFrame);
    const forAvatar = displayNodeId && displayNodeId !== "—" ? displayNodeId : String(serverNodeId || "");
    const png = forAvatar ? renderNodeAvatarDataUrl(forAvatar) : "";
    if (png) {
      av.style.backgroundImage = `url(${png})`;
      av.style.backgroundSize = "cover";
      av.style.backgroundPosition = "center";
    } else {
      av.style.backgroundImage = "";
    }
  }
  const proSync = wrap.querySelector(".pro-sync");
  const ls = obj.lastSyncMs;
  if (proSync) {
    if (typeof ls === "number" && Number.isFinite(ls) && ls > 0) {
      proSync.textContent = new Date(ls).toLocaleString("ru-RU");
    } else {
      proSync.textContent = "—";
    }
  }
  const proServer = wrap.querySelector(".pro-server");
  if (proServer) {
    proServer.textContent =
      "Профиль обновлён на сервере: " +
      (updatedAtMs != null && Number.isFinite(updatedAtMs) && updatedAtMs > 0
        ? new Date(updatedAtMs).toLocaleString("ru-RU")
        : "—");
  }
  const line =
    (prebuiltExportQr && String(prebuiltExportQr).trim()) || auraProfileQrText(raw, serverNodeId);
  setRestoreQrImage(wrap.querySelector(".pro-restore-qr"), line);
  const warn = wrap.querySelector(".pro-qr-warn");
  if (warn) {
    const auth = canonicalNodeId8(serverNodeId);
    const claimed = canonicalNodeId8(obj.nodeId || obj.nodeIdHex || "");
    warn.style.display = auth && claimed && auth !== claimed ? "block" : "none";
  }
  const pre = wrap.querySelector(".pro-json");
  if (pre) {
    if (!raw) {
      pre.textContent = "—";
    } else {
      try {
        pre.textContent = JSON.stringify(JSON.parse(raw), null, 2);
      } catch {
        pre.textContent = raw;
      }
    }
  }
}

function renderAdminProfileList() {
  const host = document.getElementById("adminProfileList");
  if (!host) return;
  const qInp = document.getElementById("adminSearch");
  const q = qInp?.value;
  host.innerHTML = "";
  const filtered = filterAdminItems(adminListItems, q);
  for (const row of filtered) {
    const card = createAdminProfileCard();
    applyProfileToAdminCard(
      card,
      row.profileJson,
      row.nodeId,
      row.updatedAtMs,
      row.lastLoginAtMs,
      row.profileExportQr,
    );
    host.appendChild(card);
  }
  const empty = document.getElementById("adminEmptyHint");
  if (empty) {
    if (filtered.length) {
      empty.style.display = "none";
    } else {
      empty.style.display = "block";
      if (!adminListItems.length) {
        empty.textContent =
          "Пока нет пользователей: никто ещё не вошёл с ноды (Node ID + пароль). После первого такого входа запись появится здесь.";
      } else {
        empty.textContent = "Нет карточек, подходящих под поиск. Очистите поле или смените запрос.";
      }
    }
  }
}

function setAdminListMeta() {
  const el = document.getElementById("adminListMeta");
  if (el) {
    const n = adminListItems.length;
    el.textContent = n
      ? `Профилей в базе: ${n} (сортировка: сначала недавние входы).`
      : "Профилей в базе: 0";
  }
}

async function loadAdminUsers() {
  showErr("");
  let r;
  try {
    r = await apiJson("/api/admin/users");
  } catch (e) {
    showErr(e?.message || String(e) || "Не удалось загрузить список");
    adminListItems = [];
    setAdminListMeta();
    renderAdminProfileList();
    return;
  }
  adminListItems = Array.isArray(r.items) ? r.items : [];
  setAdminListMeta();
  renderAdminProfileList();
}

function applyProfile(profileJson, authNodeId, prebuiltExportQr) {
  removeLegacyAchievementsBlock();
  const raw = profileJson || "";
  const obj = safeJsonParse(raw) || {};
  const nodeId = obj.nodeId || obj.nodeIdHex || "—";
  setText("nodeIdText", profileDisplayName(obj, authNodeId || nodeId));
  const hexLine = document.getElementById("profileNodeIdHex");
  const c8 = canonicalNodeId8(nodeId !== "—" ? nodeId : authNodeId);
  if (hexLine) {
    const hasNameField = profileLongNameFromObject(obj) !== "";
    if (c8 && hasNameField) {
      hexLine.style.display = "block";
      hexLine.textContent = "Node ID: !" + c8;
    } else {
      hexLine.style.display = "none";
      hexLine.textContent = "";
    }
  }
  const nidEl = document.getElementById("nodeIdText");
  if (nidEl) {
    const hex = String(nodeId !== "—" ? nodeId : authNodeId || "").trim();
    nidEl.title = c8 ? "Node ID: !" + c8 : hex || "";
  }
  const vipEnd = vipEndMsFromProfile(obj);
  startVipCountdown(vipEnd > 0 ? vipEnd : null);
  const avatar = document.getElementById("avatar");
  if (avatar) {
    const vipActive = !!(vipEnd > 0 && vipEnd > Date.now());
    avatar.classList.toggle("vip", vipActive);
    const png = nodeId && nodeId !== "—" ? renderNodeAvatarDataUrl(nodeId) : "";
    if (png) {
      avatar.style.backgroundImage = `url(${png})`;
      avatar.style.backgroundSize = "cover";
      avatar.style.backgroundPosition = "center";
    }
  }
  const ls = obj.lastSyncMs;
  if (typeof ls === "number" && Number.isFinite(ls) && ls > 0) {
    setText("lastSyncText", new Date(ls).toLocaleString("ru-RU"));
  } else {
    setText("lastSyncText", "—");
  }

  const restoreText =
    (prebuiltExportQr && String(prebuiltExportQr).trim()) || auraProfileQrText(raw, authNodeId);
  if (!restoreText) {
    renderRestoreQr("");
    if (authNodeId) {
      const auth = canonicalNodeId8(authNodeId);
      const claimed = canonicalNodeId8(nodeId);
      if (auth && claimed && auth !== claimed) {
        showErr(`nodeId в профиле (${claimed}) не совпадает с авторизацией (${auth}). QR восстановления не показан.`);
      }
    }
  } else {
    renderRestoreQr(restoreText);
  }
  void syncRestoreQrWithServer();
}

async function loadMe() {
  const me = await apiJson("/api/me");
  isAdminSession = !!me.isAdmin;
  if (isAdminSession) {
    setAdminModeUI(true);
    await loadAdminUsers();
    return;
  }
  setAdminModeUI(false);
  applyProfile(me.profileJson, me.nodeId, me.profileExportQr);
}

async function startPair() {
  showErr("");
  const r = await apiJson("/api/pair/start", { method: "POST", body: JSON.stringify({}) });
  pair = { pairId: r.pairId, secret: r.secret, qrText: r.qrText, expiresAtMs: r.expiresAtMs, status: "pending" };
  savePairToStorage(pair);
  setText("pairStatus", pair.status);
  renderQr(pair.qrText);
}

async function pollPair() {
  showErr("");
  if (!pair) throw new Error("Сначала нажмите «Показать QR»");
  const st = await apiJson(`/api/pair/status?pairId=${encodeURIComponent(pair.pairId)}`);
  pair.status = st.status;
  savePairToStorage(pair);
  setText("pairStatus", pair.status);
  if (pair.status === "completed" || pair.status === "claimed") {
    await loadMe();
    stopAutoPoll();
  }
}

function logout() {
  localStorage.removeItem(LS_TOKEN);
  window.location.href = "/auth";
}

async function deleteProfile() {
  showErr("");
  if (
    !confirm(
      "Удалить профиль с сервера? Данные синхронизации и VIP на сайте будут удалены. Войти снова можно по Node ID и паролю ноды (будет создан пустой профиль).",
    )
  ) {
    return;
  }
  try {
    await apiJson("/api/profile", { method: "DELETE" });
  } catch (e) {
    showErr(e?.message || String(e));
    return;
  }
  localStorage.removeItem(LS_TOKEN);
  localStorage.removeItem(LS_PAIR);
  window.location.href = "/auth";
}

async function boot() {
  const token = localStorage.getItem(LS_TOKEN);
  if (!token) {
    window.location.href = "/auth";
    return;
  }

  document.getElementById("logoutTopBtn")?.addEventListener("click", () => logout());
  document.getElementById("homeBtn")?.addEventListener("click", () => {
    window.location.href = "/";
  });
  removeLegacyAchievementsBlock();
  document.getElementById("pairStartBtn")?.addEventListener("click", () => void startPair().catch((e) => showErr(e?.message || String(e))));
  document.getElementById("pairPollBtn")?.addEventListener("click", () => void pollPair().catch((e) => showErr(e?.message || String(e))));
  document.getElementById("deleteProfileBtn")?.addEventListener("click", () => void deleteProfile().catch((e) => showErr(e?.message || String(e))));

  await loadMe();
  if (isAdminSession) {
    const search = document.getElementById("adminSearch");
    search?.addEventListener("input", () => renderAdminProfileList());
    search?.addEventListener("search", () => renderAdminProfileList());
    return;
  }
  pair = loadPairFromStorage();
  if (pair?.qrText) {
    setText("pairStatus", pair.status || "pending");
    renderQr(pair.qrText);
    startAutoPoll();
  } else {
    setText("pairStatus", "—");
    renderQr("");
  }

  window.addEventListener("beforeunload", () => stopAutoPoll());
  window.addEventListener("beforeunload", () => stopVipCountdown());
  document.addEventListener(
    "visibilitychange",
    () => {
      if (document.visibilityState === "visible") {
        void syncRestoreQrWithServer();
      }
    },
    { passive: true },
  );
}

boot().catch((e) => showErr(e?.message || String(e)));

