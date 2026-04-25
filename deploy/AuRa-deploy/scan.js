const LS_TOKEN = "aura_site_token_v1";
const LS_PAIR = "aura_site_pair_v1";

function showErr(msg) {
  const box = document.getElementById("errBox");
  if (!box) return;
  box.style.display = msg ? "block" : "none";
  box.textContent = msg || "";
}

function parseQuery(qs) {
  const q = {};
  const s = (qs || "").replace(/^\?/, "");
  s.split("&").forEach((kv) => {
    if (!kv) return;
    const [k, v] = kv.split("=");
    q[decodeURIComponent(k)] = decodeURIComponent(v || "");
  });
  return q;
}

function parseAuraUri(raw) {
  const t = String(raw || "").trim();
  if (!t) return null;
  if (t.startsWith("aura://pair")) {
    const q = parseQuery(t.split("?")[1] || "");
    return { kind: "pair", pairId: q.pairId || "", secret: q.secret || "" };
  }
  if (t.startsWith("aura://profile")) {
    const q = parseQuery(t.split("?")[1] || "");
    return {
      kind: "profile",
      nodeId: q.nodeId || "",
      profile: q.profile || "",
      sig: (q.sig || "").trim(),
    };
  }
  if (t.startsWith("aura://node/")) {
    return { kind: "node", nodeId: t.substring("aura://node/".length) };
  }
  if (/^![0-9a-fA-F]{1,8}$/.test(t)) return { kind: "node", nodeId: t };
  return { kind: "unknown", raw: t };
}

function b64urlToStr(b64url) {
  const s = String(b64url || "").replace(/-/g, "+").replace(/_/g, "/");
  const pad = s.length % 4 === 0 ? "" : "=".repeat(4 - (s.length % 4));
  const bin = atob(s + pad);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder("utf-8").decode(bytes);
}

function safeJsonParse(s) {
  try {
    return JSON.parse(s);
  } catch {
    return null;
  }
}

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

function profileStats(profileObj) {
  const o = profileObj && typeof profileObj === "object" ? profileObj : {};
  const vipUntil = vipEndMsFromProfile(o);
  const lastSync = Number(o.lastSyncMs ?? 0) || 0;
  return { vipUntil, lastSync };
}

function shouldWarnOverwrite(currentObj, incomingObj) {
  const cur = profileStats(currentObj);
  const inc = profileStats(incomingObj);
  const now = Date.now();
  const curVipLeft = Math.max(0, cur.vipUntil - now);
  const incVipLeft = Math.max(0, inc.vipUntil - now);

  // Warn if incoming seems older or "weaker" than current.
  if (cur.lastSync > 0 && inc.lastSync > 0 && inc.lastSync < cur.lastSync) return true;
  if (incVipLeft + 60_000 < curVipLeft) return true; // 1 min tolerance
  return false;
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

let stream = null;
let detector = null;
let raf = 0;
let busy = false;

async function stop() {
  cancelAnimationFrame(raf);
  raf = 0;
  busy = false;
  if (stream) {
    stream.getTracks().forEach((t) => t.stop());
    stream = null;
  }
  const v = document.getElementById("v");
  if (v) v.srcObject = null;
}

async function handleDecoded(rawText) {
  if (busy) return;
  busy = true;
  showErr("");
  const parsed = parseAuraUri(rawText);
  if (!parsed) {
    busy = false;
    return;
  }

  if (parsed.kind === "pair") {
    showErr("Это QR синхронизации для приложения (aura://pair). Для импорта на сайт покажите QR ноды или QR профиля.");
    busy = false;
    return;
  }

  const me = await apiJson("/api/me");
  const myNode = String(me.nodeId || "").toUpperCase();
  const qrNode = String(parsed.nodeId || "").replace(/^!/, "").toUpperCase();

  // Case A: full profile export in QR -> import immediately
  if (parsed.kind === "profile") {
    if (!qrNode || qrNode !== myNode) {
      showErr(`Node ID в QR (${qrNode || "—"}) не совпадает с вашим (${myNode || "—"}). Импорт запрещён.`);
      busy = false;
      return;
    }
    if (!parsed.sig) {
      showErr("В QR нет подписи (устаревший или поддельный). Сгенерируйте QR в приложении или на сайте заново.");
      busy = false;
      return;
    }
    const profileJson = b64urlToStr(parsed.profile);
    const incomingObj = safeJsonParse(profileJson);
    const currentObj = safeJsonParse(me.profileJson || "");
    if (incomingObj && currentObj && shouldWarnOverwrite(currentObj, incomingObj)) {
      const ok = window.confirm(
        "ВНИМАНИЕ: этот QR может перезаписать данные на сайте более «слабыми/старыми» данными (например после переустановки приложения).\n\n" +
          "Если вы хотели восстановить данные В ПРИЛОЖЕНИЕ — нужно сканировать QR с сайта В приложении.\n\n" +
          "Импортировать этот QR на сайт всё равно?"
      );
      if (!ok) {
        busy = false;
        return;
      }
    }
    await apiJson("/api/profile/import", {
      method: "POST",
      body: JSON.stringify({
        profileB64Url: parsed.profile,
        profileQrSig: parsed.sig,
      }),
    });
    await stop();
    window.location.href = "/profile";
    return;
  }

  // Case B: user shows "node/profile" QR from app (usually !XXXXXXXX). We start a pair session and show QR for app.
  if (parsed.kind === "node") {
    if (!qrNode || qrNode !== myNode) {
      showErr(`Node ID в QR (${qrNode || "—"}) не совпадает с вашим (${myNode || "—"}). Синхронизация запрещена.`);
      busy = false;
      return;
    }
    const r = await apiJson("/api/pair/start", { method: "POST", body: JSON.stringify({}) });
    try {
      localStorage.setItem(
        LS_PAIR,
        JSON.stringify({ pairId: r.pairId, qrText: r.qrText, expiresAtMs: r.expiresAtMs, status: "pending" })
      );
    } catch (_) {}
    const pairBox = document.getElementById("pairBox");
    const pairImg = document.getElementById("pairImg");
    const pairStatus = document.getElementById("pairStatus");
    const pollBtn = document.getElementById("pairPollBtn");
    if (pairBox) pairBox.style.display = "block";
    if (pairImg) pairImg.src = `/api/qr?text=${encodeURIComponent(r.qrText)}`;
    if (pairStatus) pairStatus.textContent = "pending";

    pollBtn?.addEventListener("click", () => {
      void (async () => {
        try {
          const st = await apiJson(`/api/pair/status?pairId=${encodeURIComponent(r.pairId)}`);
          if (pairStatus) pairStatus.textContent = st.status || "—";
          try {
            const prev = JSON.parse(localStorage.getItem(LS_PAIR) || "{}");
            localStorage.setItem(LS_PAIR, JSON.stringify({ ...prev, pairId: r.pairId, qrText: r.qrText, expiresAtMs: r.expiresAtMs, status: st.status || "pending" }));
          } catch (_) {}
          if (st.status === "completed" || st.status === "claimed") {
            await stop();
            window.location.href = "/profile";
          }
        } catch (e) {
          showErr(e?.message || String(e));
        }
      })();
    });

    // keep camera running; user now scans QR in app
    busy = false;
    return;
  }

  showErr("Это не QR профиля. Для импорта покажите QR ноды (!XXXXXXXX) или QR экспорта профиля (aura://profile...).");
  busy = false;
}

async function loop() {
  const v = document.getElementById("v");
  if (!v || !detector || !stream) return;
  try {
    const barcodes = await detector.detect(v);
    const first = barcodes && barcodes[0] && (barcodes[0].rawValue || barcodes[0].rawData);
    if (first) {
      await handleDecoded(first);
      return;
    }
  } catch (e) {
    // ignore frame errors
  }
  raf = requestAnimationFrame(loop);
}

async function start() {
  showErr("");
  if (!("BarcodeDetector" in window)) {
    showErr("Браузер не поддерживает BarcodeDetector. Откройте в Chrome/Edge.");
    return;
  }
  detector = new BarcodeDetector({ formats: ["qr_code"] });

  const v = document.getElementById("v");
  stream = await navigator.mediaDevices.getUserMedia({
    video: { facingMode: "environment", width: { ideal: 1280 }, height: { ideal: 720 } },
    audio: false,
  });
  v.srcObject = stream;
  await v.play();
  raf = requestAnimationFrame(loop);
}

function boot() {
  const token = localStorage.getItem(LS_TOKEN);
  if (!token) {
    window.location.href = "/auth";
    return;
  }
  document.getElementById("startBtn")?.addEventListener("click", () => void start().catch((e) => showErr(e?.message || String(e))));
  document.getElementById("stopBtn")?.addEventListener("click", () => void stop());
  window.addEventListener("beforeunload", () => void stop());
}

boot();

