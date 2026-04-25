const LS_TOKEN = "aura_site_token_v1";

function showErr(msg) {
  const box = document.getElementById("errBox");
  if (!box) return;
  box.style.display = msg ? "block" : "none";
  box.textContent = msg || "";
}

async function apiJson(path, opts = {}) {
  const headers = { "Content-Type": "application/json", ...(opts.headers || {}) };
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

function loginErrorMessage(raw) {
  const msg = String(raw || "");
  if (msg.includes("Wrong nodeId")) {
    return "Неверный Node ID или пароль";
  }
  if (msg.includes("Too many attempts") || msg.includes("429")) {
    return "Слишком много попыток. Попробуйте позже.";
  }
  if (msg.includes("Invalid nodeId")) {
    return "Некорректный Node ID: нужны цифры 0–9 и/или буквы a–f (8 hex, можно с ! в начале). Для входа админа в поле Node ID укажите root (если не меняли на сервере).";
  }
  return msg || "Ошибка входа";
}

async function onLogin() {
  showErr("");
  const nodeId = document.getElementById("nodeId")?.value?.trim() || "";
  const password = document.getElementById("password")?.value || "";
  if (!nodeId) return showErr("Введите Node ID");
  if (!password) return showErr("Введите пароль");

  try {
    const r = await apiJson("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ nodeId, password }),
    });
    localStorage.setItem(LS_TOKEN, r.token);
    window.location.href = "/profile";
  } catch (e) {
    showErr(loginErrorMessage(e?.message || e));
  }
}

function boot() {
  const token = localStorage.getItem(LS_TOKEN);
  if (token) {
    window.location.href = "/profile";
    return;
  }
  const btn = document.getElementById("loginBtn");
  btn?.addEventListener("click", () => void onLogin());
  document.addEventListener("keydown", (e) => {
    if (e.key === "Enter") void onLogin();
  });
}

boot();
