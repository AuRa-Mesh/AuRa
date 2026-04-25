const menuToggle = document.getElementById("menuToggle");
const mainNav = document.getElementById("mainNav");
const navLinks = Array.from(document.querySelectorAll(".nav a"));
const revealItems = Array.from(document.querySelectorAll(".reveal"));
const counters = Array.from(document.querySelectorAll("[data-count]"));
const moduleTabs = Array.from(document.querySelectorAll(".module-tab"));
const modulePanels = Array.from(document.querySelectorAll(".module-panel"));
const heroVisual = document.getElementById("heroVisual");
const matrixCanvas = document.getElementById("matrixCanvas");
const shotsStage = document.getElementById("shotsStage");
const shots = Array.from(document.querySelectorAll(".shot"));
const shotsDots = document.getElementById("shotsDots");
const shotPrev = document.getElementById("shotPrev");
const shotNext = document.getElementById("shotNext");
const backToTop = document.getElementById("backToTop");
const yearNode = document.getElementById("year");
const langToggle = document.getElementById("langToggle");
const langSelect = document.getElementById("langSelect");
const langMenu = document.getElementById("langMenu");
const overlayLayer = document.getElementById("overlayLayer");

const I18N_STORAGE_KEY = "aura_lang";

const I18N = {
  ru: {
    "lang.code": "ru",
    "lang.label": "RU",
    "lang.ruName": "Русский",
    "lang.enName": "English",
    "a11y.skip": "Перейти к контенту",
    "a11y.menuOpen": "Открыть меню",
    "a11y.menuClose": "Закрыть меню",
    "a11y.langToggle": "Сменить язык",
    "nav.features": "Функции",
    "nav.platform": "Платформа",
    "nav.screenshots": "Скриншоты",
    "nav.workflow": "Сценарии",
    "nav.faq": "Вопросы",
    "nav.contacts": "Контакты",
    "meta.title": "AuRa — связь будущего из прошлого",
    "meta.description":
      "Aura — безопасная mesh-платформа для связи, картографии, телеметрии и управления радио-узлами.",
    "meta.ogTitle": "AuRa — связь будущего из прошлого",
    "meta.ogDescription": "Единая платформа для сообщений, узлов, картографии, телеметрии и управления mesh-радио.",
    "meta.twitterTitle": "AuRa — связь будущего из прошлого",
    "meta.twitterDescription": "Все функции Aura на одной продакшн-странице.",
    "shots.prev": "Предыдущий скриншот",
    "shots.next": "Следующий скриншот",
    "shots.dots": "Навигация по скриншотам",
    "shots.slide": "Слайд {n}",
    "footer.top": "Наверх ↑",
    "a11y.backToTop": "Наверх",
    "hero.pill": "НАБОР СВЯЗИ MESH",
    "hero.title": "Единая платформа <br> для раскрытия полного потенциала вашей ноды",
    "hero.lead":
      "Объедините сообщения, узлы, карту, телеметрию, безопасность, LoRa-настройки и обновление прошивки в одном премиальном интерфейсе. Без компромиссов по скорости и контролю.",
    "hero.ctaPrimary": "Скачать AuRa.APK (сборка 26.04-1)",
    "hero.ctaSecondary": "Смотреть возможности",
    "hero.fwT114": "Node_Firmware_for_T114",
    "hero.fwHeltec": "Node_Firmware_for_HELTEC_Pocket",
    "hero.metric.tabs": "Основные вкладки",
    "hero.metric.settingsModules": "Модулей настроек",
    "hero.metric.keyFeatures": "Ключевых функций",
    "hero.orbit1.title": "Сигнал mesh в реальном времени",
    "hero.orbit1.kv1.k": "Связь:",
    "hero.orbit1.kv1.v": "Bluetooth / USB / Wi‑Fi",
    "hero.orbit1.kv2.k": "Статус:",
    "hero.orbit1.kv2.v": "Подключено к ноде",
    "hero.orbit1.kv3.k": "Маршруты:",
    "hero.orbit1.kv3.v": "маршрутизация + подтверждения",
    "hero.orbit2.title": "Модули в реальном времени",
    "hero.orbit2.li1": "Сообщения и Личные сообщения",
    "hero.orbit2.li2": "Карта и маяки",
    "hero.orbit2.li3": "Телеметрия и обновление",
    "hero.logo1": "СООБЩЕНИЯ",
    "hero.logo2": "УЗЛЫ",
    "hero.logo3": "КАРТА",
    "hero.logo4": "БЕЗОПАСНОСТЬ",
    "hero.logo5": "LORA",
    "hero.logo6": "ОБНОВЛЕНИЕ ПРОШИВКИ",
    "features.pill": "ВОЗМОЖНОСТИ ПРИЛОЖЕНИЯ",
    "features.title": "Все функции Aura.APP на одной странице",
    "features.lead": "Лендинг покрывает фактические возможности приложения: от чатов до системного администрирования ноды.",
    "features.card1.title": "Сообщения и контент",
    "features.card1.li1": "Каналы и личные сообщения",
    "features.card1.li2": "Голосовые и фото-вложения",
    "features.card1.li3": "Реакции, закрепы, поиск по сообщениям",
    "features.card1.li4": "Опросы и чеклисты внутри диалога",
    "features.card1.li5": "Быстрые ответы и история сообщений",
    "features.card2.title": "Узлы и структура сети",
    "features.card2.li1": "Список узлов и детальные карточки",
    "features.card2.li2": "Группы/папки для сегментации нод",
    "features.card2.li3": "Избранное и управление списком нод",
    "features.card2.li4": "QR-инструменты для узлов",
    "features.card2.li5": "Переход в Личные сообщения прямо из профиля узла",
    "features.card3.title": "Карта и гео-сценарии",
    "features.card3.li1": "Сетевая карта с активными узлами",
    "features.card3.li2": "Метки и обмен по ссылке",
    "features.card3.li3": "Импорт меток в карту из чата",
    "features.card3.li4": "Канальная фильтрация карты",
    "features.card3.li5": "Контроль передачи координат в mesh",
    "features.card4.title": "Безопасность и доступ",
    "features.card4.li1": "Экран пароля и сохранение идентичности",
    "features.card4.li2": "Привязка/отвязка Bluetooth-устройства",
    "features.card4.li3": "Оценка безопасности каналов (статус замка)",
    "features.card4.li4": "Управление ключами и параметрами безопасности",
    "features.card4.li5": "Устойчивая связь через фоновый сервис",
    "features.card5.title": "Конфигурация радио",
    "features.card5.li1": "LoRa-параметры и пресеты модема",
    "features.card5.li2": "Каналы: создание и управление PSK",
    "features.card5.li3": "MQTT-конфигурация и внешние уведомления",
    "features.card5.li4": "Пользователь, устройство, уведомления",
    "features.card5.li5": "Матрица/визуальные параметры чата",
    "features.card6.title": "Телеметрия и администрирование",
    "features.card6.li1": "Environment, Air, Power, Health экраны",
    "features.card6.li2": "Обновление прошивки по воздуху",
    "features.card6.li3": "Перезагрузка, выключение, заводской сброс",
    "features.card6.li4": "Очистка списка узлов сети",
    "features.card6.li5": "Системные диалоги состояния и синхронизации",
    "platform.pill": "ИНТЕРАКТИВНЫЙ ОБЗОР",
    "platform.title": "Модульная архитектура платформы",
    "platform.lead": "Выберите блок и посмотрите, как он работает внутри экосистемы Aura.",
    "platform.tab.chat": "Центр сообщений",
    "platform.tab.nodes": "Управление узлами",
    "platform.tab.map": "Возможности карты",
    "platform.tab.settings": "Центр управления",
    "platform.chat.title": "Центр сообщений",
    "platform.chat.lead": "Текст, голос, фото, реакции и интерактивный контент в едином окне разговора.",
    "platform.chat.chip1": "Чат каналов",
    "platform.chat.chip2": "Личные сообщения",
    "platform.chat.chip3": "Голосовые",
    "platform.chat.chip4": "Фото",
    "platform.chat.chip5": "Закрепы",
    "platform.chat.chip6": "Поиск и история",
    "platform.nodes.title": "Управление узлами",
    "platform.nodes.lead": "Полное управление нодами: листинг, профиль, группировка, фильтры, избранное и быстрые действия.",
    "platform.nodes.chip1": "Карточка узла",
    "platform.nodes.chip2": "Папки и группы",
    "platform.nodes.chip3": "Избранное",
    "platform.nodes.chip4": "QR-инструменты",
    "platform.nodes.chip5": "Профиль → Личные сообщения",
    "platform.map.title": "Возможности карты",
    "platform.map.lead": "Канал-ориентированная карта сети с метками, синхронизацией и обменом геоданными.",
    "platform.map.chip1": "Сетевая карта",
    "platform.map.chip2": "Обмен метками",
    "platform.map.chip3": "Импорт по ссылке",
    "platform.map.chip4": "Синхронизация",
    "platform.map.chip5": "Приватность",
    "platform.settings.title": "Центр управления",
    "platform.settings.lead": "10+ экранов конфигурации: LoRa, безопасность, пользователь/устройство, MQTT, телеметрия, прошивка и системное администрирование.",
    "platform.settings.chip1": "Настройки LoRa",
    "platform.settings.chip2": "Безопасность каналов",
    "platform.settings.chip3": "Телеметрия",
    "platform.settings.chip4": "Обновление прошивки",
    "platform.settings.chip5": "Перезагрузка и сброс",
    "shots.pill": "СКРИНШОТЫ",
    "shots.alt1": "Логотип Aura Mesh на стартовом экране",
    "shots.alt2": "Экран подключения устройств в Aura",
    "shots.alt3": "Список узлов сети в Aura",
    "shots.alt4": "Экран чата в Aura",
    "shots.alt5": "Экран карты и узлов в Aura",
    "shots.alt6": "Экран меток на карте в Aura",
    "shots.alt7": "Экран профиля узла в Aura",
    "shots.alt8": "Устройство с прошивкой AuRa: экран приветствия",
    "shots.alt9": "Устройство Heltec: экран приветствия AuRa",
    "shots.alt10": "Устройство Heltec: информационный экран прошивки AuRa",
    "shots.hintStrong": "Подсказка:",
    "shots.hintText": "если изображения не загрузились, проверьте доступ к локальным путям файлов.",
    "flow.pill": "СЦЕНАРИЙ",
    "flow.title": "Сквозной пользовательский сценарий",
    "flow.lead": "От первого запуска до полного контроля mesh-инфраструктуры.",
    "flow.i1.title": "Первый запуск + защита доступа",
    "flow.i1.text": "Инструкция первого запуска, splash-поток, пароль и сохранение идентичности ноды.",
    "flow.i2.title": "Подключение к радио",
    "flow.i2.text": "Bluetooth/USB/Wi‑Fi соединение, контроль соединения и устойчивый фоновый сервис.",
    "flow.i3.title": "Коммуникация и координация",
    "flow.i3.text": "Чаты, Личные сообщения, мультимедиа, карта, маяки, голосование и управление списками задач.",
    "flow.i4.title": "Настройка и обслуживание",
    "flow.i4.text": "LoRa/каналы/безопасность/телеметрия, обновление прошивки, перезагрузка/выключение/сброс.",
    "faq.pill": "ВОПРОСЫ",
    "faq.title": "Частые вопросы",
    "faq.q1.q": "Где и как получить пароль для инициализации приложения ?",
    "faq.q1.a": "Каждый пароль уникален и привязан к вашей ноде, один пароль — одно устройство. Для получения пароля напишите в поддержку.",
    "faq.q2.q": "Будут ли мои сообщения доходить до пользователей у кого нет данной программы и они используют Meshtastic ?",
    "faq.q2.a": "Минимальный функционал который есть в программе Meshtastic мы перенесли полностью, поэтому сообщения из нашего приложения в Meshtastic и наоборот будут приходить, но новый функционал который реализован в программе AuRa в Meshtastic работать никогда не будет.",
    "faq.q3.q": "Можно ли использовать Оффлай карты и ставить на них метки ?",
    "faq.q3.a": "Конечно! Это одна из основных функций на которую мы делали упор.",
    "cta.pill": "КОНТАКТЫ",
    "cta.title": "По всем вопросам пишите в Телеграмм @Aura_Mesh",
    "cta.lead": "В ближайшее время появимся на всех Российских платформах.",
    "cta.toTop": "Поднять наверх",
    "footer.copy": "© <span id=\"year\"></span> Aura. Платформа связи для mesh-сетей. @just_be_freee",
  },
  en: {
    "lang.code": "en",
    "lang.label": "EN",
    "lang.ruName": "Russian",
    "lang.enName": "English",
    "a11y.skip": "Skip to content",
    "a11y.menuOpen": "Open menu",
    "a11y.menuClose": "Close menu",
    "a11y.langToggle": "Switch language",
    "nav.features": "Features",
    "nav.platform": "Platform",
    "nav.screenshots": "Screenshots",
    "nav.workflow": "Flow",
    "nav.faq": "FAQ",
    "nav.contacts": "Contacts",
    "meta.title": "Aura — mesh platform landing",
    "meta.description":
      "Aura is a secure mesh platform for messaging, mapping, telemetry, and radio node management.",
    "meta.ogTitle": "Aura — mesh platform",
    "meta.ogDescription": "One platform for messaging, nodes, mapping, telemetry, and mesh radio management.",
    "meta.twitterTitle": "Aura — mesh platform",
    "meta.twitterDescription": "All Aura features on a single page.",
    "shots.prev": "Previous screenshot",
    "shots.next": "Next screenshot",
    "shots.dots": "Screenshot navigation",
    "shots.slide": "Slide {n}",
    "footer.top": "Back to top ↑",
    "a11y.backToTop": "Back to top",
    "hero.pill": "MESH COMMS KIT",
    "hero.title": "One platform <br> to unlock your node’s full potential",
    "hero.lead":
      "Bring messaging, nodes, maps, telemetry, security, LoRa settings, and firmware updates into one premium interface — without compromising speed or control.",
    "hero.ctaPrimary": "Download AuRa.APK (build 26.04-1)",
    "hero.ctaSecondary": "Explore features",
    "hero.fwT114": "Node_Firmware_for_T114",
    "hero.fwHeltec": "Node_Firmware_for_HELTEC_Pocket",
    "hero.metric.tabs": "Main tabs",
    "hero.metric.settingsModules": "Settings modules",
    "hero.metric.keyFeatures": "Key features",
    "hero.orbit1.title": "Real‑time mesh signal",
    "hero.orbit1.kv1.k": "Link:",
    "hero.orbit1.kv1.v": "Bluetooth / USB / Wi‑Fi",
    "hero.orbit1.kv2.k": "Status:",
    "hero.orbit1.kv2.v": "Connected to node",
    "hero.orbit1.kv3.k": "Routes:",
    "hero.orbit1.kv3.v": "routing + acknowledgements",
    "hero.orbit2.title": "Real‑time modules",
    "hero.orbit2.li1": "Channels & direct messages",
    "hero.orbit2.li2": "Map & beacons",
    "hero.orbit2.li3": "Telemetry & updates",
    "hero.logo1": "MESSAGING",
    "hero.logo2": "NODES",
    "hero.logo3": "MAP",
    "hero.logo4": "SECURITY",
    "hero.logo5": "LORA",
    "hero.logo6": "FIRMWARE UPDATE",
    "features.pill": "APP CAPABILITIES",
    "features.title": "All Aura.APP features on one page",
    "features.lead": "This landing reflects the real app capabilities — from chats to full node administration.",
    "features.card1.title": "Messaging & content",
    "features.card1.li1": "Channels and direct messages",
    "features.card1.li2": "Voice and photo attachments",
    "features.card1.li3": "Reactions, pins, message search",
    "features.card1.li4": "Polls and checklists inside chats",
    "features.card1.li5": "Quick replies and message history",
    "features.card2.title": "Nodes & network structure",
    "features.card2.li1": "Node list and detailed profiles",
    "features.card2.li2": "Groups/folders to segment nodes",
    "features.card2.li3": "Favorites and node list management",
    "features.card2.li4": "QR tools for nodes",
    "features.card2.li5": "Open direct messages from a node profile",
    "features.card3.title": "Map & geo workflows",
    "features.card3.li1": "Network map with active nodes",
    "features.card3.li2": "Marks and sharing via link",
    "features.card3.li3": "Import marks into the map from chat",
    "features.card3.li4": "Channel-based map filtering",
    "features.card3.li5": "Control coordinate sharing in mesh",
    "features.card4.title": "Security & access",
    "features.card4.li1": "Password screen and identity persistence",
    "features.card4.li2": "Bind/unbind Bluetooth device",
    "features.card4.li3": "Channel security assessment (lock status)",
    "features.card4.li4": "Keys and security settings management",
    "features.card4.li5": "Stable connection via background service",
    "features.card5.title": "Radio configuration",
    "features.card5.li1": "LoRa parameters and modem presets",
    "features.card5.li2": "Channels: create and manage PSK",
    "features.card5.li3": "MQTT configuration and external alerts",
    "features.card5.li4": "User, device, notifications",
    "features.card5.li5": "Matrix / chat visual settings",
    "features.card6.title": "Telemetry & administration",
    "features.card6.li1": "Environment, Air, Power, Health screens",
    "features.card6.li2": "Over‑the‑air firmware updates",
    "features.card6.li3": "Reboot, power off, factory reset",
    "features.card6.li4": "Clear network node list",
    "features.card6.li5": "System status and sync dialogs",
    "platform.pill": "INTERACTIVE OVERVIEW",
    "platform.title": "Modular platform architecture",
    "platform.lead": "Pick a module and see how it works inside the Aura ecosystem.",
    "platform.tab.chat": "Messaging hub",
    "platform.tab.nodes": "Node management",
    "platform.tab.map": "Map capabilities",
    "platform.tab.settings": "Control center",
    "platform.chat.title": "Messaging hub",
    "platform.chat.lead": "Text, voice, photos, reactions, and interactive content — all in one conversation view.",
    "platform.chat.chip1": "Channel chat",
    "platform.chat.chip2": "Direct messages",
    "platform.chat.chip3": "Voice",
    "platform.chat.chip4": "Photos",
    "platform.chat.chip5": "Pins",
    "platform.chat.chip6": "Search & history",
    "platform.nodes.title": "Node management",
    "platform.nodes.lead": "Full node control: listing, profile, grouping, filters, favorites, and quick actions.",
    "platform.nodes.chip1": "Node card",
    "platform.nodes.chip2": "Folders & groups",
    "platform.nodes.chip3": "Favorites",
    "platform.nodes.chip4": "QR tools",
    "platform.nodes.chip5": "Profile → direct messages",
    "platform.map.title": "Map capabilities",
    "platform.map.lead": "Channel-oriented network map with marks, sync, and geo data sharing.",
    "platform.map.chip1": "Network map",
    "platform.map.chip2": "Share marks",
    "platform.map.chip3": "Import via link",
    "platform.map.chip4": "Synchronization",
    "platform.map.chip5": "Privacy",
    "platform.settings.title": "Control center",
    "platform.settings.lead": "10+ configuration screens: LoRa, security, user/device, MQTT, telemetry, firmware, and system administration.",
    "platform.settings.chip1": "LoRa settings",
    "platform.settings.chip2": "Channel security",
    "platform.settings.chip3": "Telemetry",
    "platform.settings.chip4": "Firmware update",
    "platform.settings.chip5": "Reboot & reset",
    "shots.pill": "SCREENSHOTS",
    "shots.alt1": "Aura Mesh logo on the start screen",
    "shots.alt2": "Device connection screen in Aura",
    "shots.alt3": "Network node list in Aura",
    "shots.alt4": "Chat screen in Aura",
    "shots.alt5": "Map and nodes screen in Aura",
    "shots.alt6": "Map marks screen in Aura",
    "shots.alt7": "Node profile screen in Aura",
    "shots.alt8": "Device with AuRa firmware: welcome screen",
    "shots.alt9": "Heltec device: AuRa welcome screen",
    "shots.alt10": "Heltec device: AuRa firmware info screen",
    "shots.hintStrong": "Tip:",
    "shots.hintText": "if images didn’t load, check access to local file paths.",
    "flow.pill": "FLOW",
    "flow.title": "End‑to‑end user flow",
    "flow.lead": "From first launch to full control of your mesh infrastructure.",
    "flow.i1.title": "First launch + access protection",
    "flow.i1.text": "First-run guidance, splash flow, password, and preserving node identity.",
    "flow.i2.title": "Connect to radio",
    "flow.i2.text": "Bluetooth/USB/Wi‑Fi link, connection monitoring, and a stable background service.",
    "flow.i3.title": "Communication & coordination",
    "flow.i3.text": "Chats, direct messages, media, map, beacons, voting, and task lists.",
    "flow.i4.title": "Setup & maintenance",
    "flow.i4.text": "LoRa/channels/security/telemetry, firmware updates, reboot/power off/reset.",
    "faq.pill": "FAQ",
    "faq.title": "Frequently asked questions",
    "faq.q1.q": "Where and how can I get an initialization password?",
    "faq.q1.a": "Each password is unique and tied to your node — one password per device. To get a password, contact support.",
    "faq.q2.q": "Will my messages reach users who don’t have this app and use Meshtastic?",
    "faq.q2.a": "We fully replicated the minimum Meshtastic feature set, so messages will work both ways. But new features implemented in AuRa will never work in Meshtastic.",
    "faq.q3.q": "Can I use offline maps and place marks on them?",
    "faq.q3.a": "Absolutely! This is one of the core features we focused on.",
    "cta.pill": "CONTACTS",
    "cta.title": "For any questions, message us on Telegram: @Aura_Mesh",
    "cta.lead": "We’ll be available on all major Russian platforms soon.",
    "cta.toTop": "Back to top",
    "footer.copy": "© <span id=\"year\"></span> Aura. Mesh communications platform. @just_be_freee",
  },
};

function getMeta(nameOrProp, isProperty = false) {
  const selector = isProperty
    ? `meta[property="${nameOrProp}"]`
    : `meta[name="${nameOrProp}"]`;
  return document.querySelector(selector);
}

function t(lang, key, vars) {
  const table = I18N[lang] || I18N.ru;
  const fallback = I18N.ru;
  const raw = table[key] ?? fallback[key];
  if (typeof raw !== "string") return "";
  if (!vars) return raw;
  return raw.replace(/\{(\w+)\}/g, (_, k) => (vars[k] ?? `{${k}}`).toString());
}

function applyLanguage(lang) {
  const safe = lang === "en" ? "en" : "ru";
  document.documentElement.setAttribute("lang", t(safe, "lang.code"));

  const title = t(safe, "meta.title");
  if (title) document.title = title;

  const desc = getMeta("description");
  if (desc) desc.setAttribute("content", t(safe, "meta.description"));

  const ogTitle = getMeta("og:title", true);
  if (ogTitle) ogTitle.setAttribute("content", t(safe, "meta.ogTitle"));
  const ogDesc = getMeta("og:description", true);
  if (ogDesc) ogDesc.setAttribute("content", t(safe, "meta.ogDescription"));

  const twTitle = getMeta("twitter:title", true);
  if (twTitle) twTitle.setAttribute("content", t(safe, "meta.twitterTitle"));
  const twDesc = getMeta("twitter:description", true);
  if (twDesc) twDesc.setAttribute("content", t(safe, "meta.twitterDescription"));

  const nodes = Array.from(document.querySelectorAll("[data-i18n]"));
  nodes.forEach((el) => {
    const key = el.getAttribute("data-i18n");
    if (!key) return;
    const value = t(safe, key);
    if (!value) return;
    el.textContent = value;
  });

  const htmlNodes = Array.from(document.querySelectorAll("[data-i18n-html]"));
  htmlNodes.forEach((el) => {
    const key = el.getAttribute("data-i18n-html");
    if (!key) return;
    const value = t(safe, key);
    if (!value) return;
    el.innerHTML = value;
  });

  const ariaLabelNodes = Array.from(document.querySelectorAll("[data-i18n-aria-label]"));
  ariaLabelNodes.forEach((el) => {
    const key = el.getAttribute("data-i18n-aria-label");
    if (!key) return;
    const value = t(safe, key);
    if (!value) return;
    el.setAttribute("aria-label", value);
  });

  const altNodes = Array.from(document.querySelectorAll("[data-i18n-alt]"));
  altNodes.forEach((el) => {
    const key = el.getAttribute("data-i18n-alt");
    if (!key) return;
    const value = t(safe, key);
    if (!value) return;
    el.setAttribute("alt", value);
  });

  const langLabel = document.querySelector('#langToggle [data-i18n="lang.label"]');
  if (langLabel) langLabel.textContent = t(safe, "lang.label");

  const year = document.getElementById("year");
  if (year) year.textContent = new Date().getFullYear().toString();

  // Update checked state in language menu
  const items = Array.from(document.querySelectorAll('#langMenu [data-lang]'));
  items.forEach((btn) => {
    const code = btn.getAttribute("data-lang");
    btn.setAttribute("aria-checked", code === safe ? "true" : "false");
  });

  // Update existing slider dot labels if already created
  const dots = Array.from(document.querySelectorAll("#shotsDots .shot-dot"));
  dots.forEach((dot, i) => {
    dot.setAttribute("aria-label", t(safe, "shots.slide", { n: i + 1 }));
  });

  localStorage.setItem(I18N_STORAGE_KEY, safe);
}

function initLanguageToggle() {
  const stored = localStorage.getItem(I18N_STORAGE_KEY);
  const initial = stored === "en" ? "en" : "ru";
  applyLanguage(initial);

  if (!langToggle || !langSelect || !langMenu || !overlayLayer) return;

  let originalParent = null;
  let originalNext = null;

  function positionPortal() {
    const r = langToggle.getBoundingClientRect();
    const margin = 10;
    const viewportPad = 10;
    const top = Math.round(r.bottom + margin);

    // Temporarily ensure measurable
    langMenu.style.visibility = "hidden";
    overlayLayer.appendChild(langMenu);
    langMenu.classList.add("portal-open");

    const w = langMenu.offsetWidth || 180;
    let left = Math.round(r.right - w);
    left = Math.max(viewportPad, Math.min(left, window.innerWidth - w - viewportPad));

    langMenu.style.setProperty("--portal-top", `${top}px`);
    langMenu.style.setProperty("--portal-left", `${left}px`);
    langMenu.style.visibility = "";
  }

  function setOpen(open) {
    langSelect.classList.toggle("open", open);
    langToggle.setAttribute("aria-expanded", open ? "true" : "false");

    if (open) {
      if (!originalParent) {
        originalParent = langMenu.parentElement;
        originalNext = langMenu.nextSibling;
      }
      positionPortal();
    } else {
      langMenu.classList.remove("portal-open");
      langMenu.style.removeProperty("--portal-top");
      langMenu.style.removeProperty("--portal-left");
      if (originalParent) {
        if (originalNext && originalNext.parentNode === originalParent) {
          originalParent.insertBefore(langMenu, originalNext);
        } else {
          originalParent.appendChild(langMenu);
        }
      }
    }
  }

  langToggle.addEventListener("click", (e) => {
    e.stopPropagation();
    const open = langSelect.classList.contains("open");
    setOpen(!open);
  });

  langMenu.addEventListener("click", (e) => {
    const target = e.target instanceof Element ? e.target.closest("[data-lang]") : null;
    if (!target) return;
    const code = target.getAttribute("data-lang");
    if (code === "ru" || code === "en") {
      applyLanguage(code);
      setOpen(false);
    }
  });

  document.addEventListener("click", (e) => {
    const tEl = e.target instanceof Node ? e.target : null;
    if (!tEl) return;
    if (langSelect.contains(tEl)) return;
    if (langMenu.contains(tEl)) return;
    setOpen(false);
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") setOpen(false);
  });

  window.addEventListener(
    "resize",
    () => {
      if (langSelect.classList.contains("open")) positionPortal();
    },
    { passive: true }
  );

  // If user scrolls the page or the mobile menu, close dropdown
  window.addEventListener(
    "scroll",
    () => {
      if (langSelect.classList.contains("open")) setOpen(false);
    },
    { passive: true }
  );

  mainNav?.addEventListener(
    "scroll",
    () => {
      if (langSelect.classList.contains("open")) setOpen(false);
    },
    { passive: true }
  );
}

initLanguageToggle();

if (menuToggle && mainNav) {
  const isMobileNav = () => window.matchMedia?.("(max-width: 760px)")?.matches ?? false;
  let navOriginalParent = null;
  let navOriginalNext = null;

  function setNavPortal(open) {
    if (!overlayLayer) return;
    if (!isMobileNav()) open = false;

    if (open) {
      if (!navOriginalParent) {
        navOriginalParent = mainNav.parentElement;
        navOriginalNext = mainNav.nextSibling;
      }
      overlayLayer.appendChild(mainNav);
      mainNav.classList.add("portal-open");
    } else {
      mainNav.classList.remove("portal-open");
      if (navOriginalParent) {
        if (navOriginalNext && navOriginalNext.parentNode === navOriginalParent) {
          navOriginalParent.insertBefore(mainNav, navOriginalNext);
        } else {
          navOriginalParent.appendChild(mainNav);
        }
      }
    }
  }

  menuToggle.addEventListener("click", () => {
    mainNav.classList.toggle("open");
    const open = mainNav.classList.contains("open");
    setNavPortal(open);
    const lang = (localStorage.getItem(I18N_STORAGE_KEY) || "ru") === "en" ? "en" : "ru";
    menuToggle.setAttribute("aria-label", t(lang, open ? "a11y.menuClose" : "a11y.menuOpen"));
  });

  navLinks.forEach((link) => {
    link.addEventListener("click", () => {
      mainNav.classList.remove("open");
      setNavPortal(false);
    });
  });

  window.addEventListener(
    "resize",
    () => {
      if (!mainNav.classList.contains("open")) return;
      setNavPortal(true);
    },
    { passive: true }
  );
}

function initMatrixRain(canvas) {
  if (!canvas) return () => {};
  const ctx = canvas.getContext("2d", { alpha: true });
  if (!ctx) return () => {};

  const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches ?? false;
  let raf = 0;
  const random = (() => {
    // Simple deterministic-ish RNG (seeded like Random(7))
    let s = 7 >>> 0;
    return {
      float() {
        s = (s * 1664525 + 1013904223) >>> 0;
        return (s & 0xffffff) / 0x1000000;
      },
      int(min, maxExclusive) {
        return Math.floor(this.float() * (maxExclusive - min)) + min;
      },
    };
  })();

  // Ported from app/src/main/java/com/example/aura/ui/matrix/MatrixRainBackground.kt
  const MATRIX = {
    densityMultiplier: 1.0,
    speedMultiplier: 1.0,
    dimOverlayAlpha: 0.38,
    charset:
      "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789ABCDEF",
    greenBright: [0, 255, 65],
    greenMid: [0, 204, 51],
    greenDim: [0, 102, 17],
  };

  function clamp(v, a, b) {
    return Math.min(b, Math.max(a, v));
  }

  function charsetRandom() {
    return MATRIX.charset[random.int(0, MATRIX.charset.length)];
  }

  let columns = [];
  let dims = { w: 0, h: 0, cell: 14, fontPx: 12, dpr: 1 };
  let prev = 0;

  function rebuild() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const wCss = Math.max(2, window.innerWidth);
    const hCss = Math.max(2, window.innerHeight);
    const w = Math.floor(wCss * dpr);
    const h = Math.floor(hCss * dpr);
    canvas.width = w;
    canvas.height = h;
    canvas.style.width = `${wCss}px`;
    canvas.style.height = `${hCss}px`;

    const density = clamp(MATRIX.densityMultiplier, 0.2, 2.5);
    const speedMul = clamp(MATRIX.speedMultiplier, 0.15, 3.0);

    // cell = (13f * (w / 400f).coerceIn(0.85f, 1.4f)).coerceIn(11f, 19f)
    const cell =
      clamp(13 * clamp((w / 400), 0.85, 1.4), 11, 19);

    const baseN = Math.max(10, Math.floor(w / cell));
    const n = Math.max(6, Math.min(96, Math.floor(baseN * density)));

    columns = [];
    for (let i = 0; i < n; i++) {
      const x = (i + 0.5) * (w / n);
      const trail = random.int(14, 32);
      const symbols = Array.from({ length: trail }, () => charsetRandom());
      columns.push({
        x,
        headY: random.float() * -h * 1.2,
        speed: random.float() * 0.9 + 0.4,
        trailLen: trail,
        charStride: cell,
        symbols,
        symbolTick: 0,
      });
    }

    dims = {
      w,
      h,
      cell,
      fontPx: cell * 0.9,
      dpr,
      speedMul,
    };

    ctx.textAlign = "center";
    ctx.textBaseline = "top";
    ctx.font = `${dims.fontPx}px ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace`;

    // Clear
    ctx.fillStyle = "rgba(0,0,0,1)";
    ctx.fillRect(0, 0, w, h);
  }

  function stepColumn(col, dtSec) {
    col.headY += col.speed * dtSec * 140 * dims.speedMul;
    col.symbolTick++;
    if (col.symbolTick >= 4) {
      col.symbolTick = 0;
      col.symbols[random.int(0, col.symbols.length)] = charsetRandom();
    }
    if (col.headY - col.trailLen * col.charStride > dims.h + col.charStride * 6) {
      col.headY = random.float() * -dims.h * 0.6;
      for (let i = 0; i < col.symbols.length; i++) col.symbols[i] = charsetRandom();
    }
  }

  function rgba(rgb, a) {
    const [r, g, b] = rgb;
    return `rgba(${r},${g},${b},${a})`;
  }

  function drawFrame(now) {
    if (!prev) prev = now;
    const dtSec = Math.min(0.08, (now - prev) / 1000);
    prev = now;

    // Full clear to black (as in app) — trails are explicit per-column
    ctx.fillStyle = "rgba(0,0,0,1)";
    ctx.fillRect(0, 0, dims.w, dims.h);

    for (const col of columns) {
      stepColumn(col, dtSec);

      for (let i = 0; i < col.trailLen; i++) {
        const y = col.headY - i * col.charStride;
        if (y < -col.charStride * 2 || y > dims.h + col.charStride * 2) continue;

        const t = i / col.trailLen;
        const color =
          i === 0 ? MATRIX.greenBright :
          t < 0.12 ? MATRIX.greenBright :
          t < 0.4 ? MATRIX.greenMid :
          MATRIX.greenDim;

        const alpha = clamp(1 - t * 0.94, 0.06, 1);
        ctx.fillStyle = rgba(color, alpha);
        ctx.fillText(col.symbols[i], col.x, y);
      }
    }

    // Dim overlay (as in MatrixRainLayer)
    const dim = clamp(MATRIX.dimOverlayAlpha, 0, 0.88);
    if (dim > 0.004) {
      ctx.fillStyle = `rgba(0,0,0,${dim})`;
      ctx.fillRect(0, 0, dims.w, dims.h);
    }

    raf = requestAnimationFrame(drawFrame);
  }

  const onResize = () => rebuild();
  window.addEventListener("resize", onResize, { passive: true });
  rebuild();

  if (!reduceMotion) {
    raf = requestAnimationFrame(drawFrame);
  }

  return () => {
    window.removeEventListener("resize", onResize);
    cancelAnimationFrame(raf);
  };
}

initMatrixRain(matrixCanvas);

const revealObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add("visible");
        revealObserver.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.15 }
);

revealItems.forEach((item) => revealObserver.observe(item));

function animateCounter(counter) {
  const target = Number(counter.getAttribute("data-count"));
  const duration = 1300;
  const startTime = performance.now();

  function update(now) {
    const progress = Math.min((now - startTime) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    counter.textContent = Math.floor(target * eased).toString();
    if (progress < 1) {
      requestAnimationFrame(update);
    } else {
      counter.textContent = target.toString();
    }
  }

  requestAnimationFrame(update);
}

const counterObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        animateCounter(entry.target);
        counterObserver.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.45 }
);

counters.forEach((counter) => counterObserver.observe(counter));

moduleTabs.forEach((tab) => {
  tab.addEventListener("click", () => {
    const target = tab.getAttribute("data-target");
    moduleTabs.forEach((btn) => btn.classList.remove("active"));
    modulePanels.forEach((panel) => panel.classList.remove("active"));
    tab.classList.add("active");
    const activePanel = document.getElementById(target);
    if (activePanel) {
      activePanel.classList.add("active");
    }
  });
});

const sections = ["features", "platform", "screenshots", "workflow", "faq", "cta"]
  .map((id) => document.getElementById(id))
  .filter(Boolean);

window.addEventListener("scroll", () => {
  const y = window.scrollY + 120;
  sections.forEach((section) => {
    const id = section.id;
    const link = navLinks.find((a) => a.getAttribute("href") === `#${id}`);
    if (!link) return;
    const inside = y >= section.offsetTop && y < section.offsetTop + section.offsetHeight;
    if (inside) {
      navLinks.forEach((a) => a.classList.remove("active"));
      link.classList.add("active");
    }
  });

  if (backToTop) {
    const show = window.scrollY > 360;
    backToTop.classList.toggle("visible", show);
  }
});

if (heroVisual) {
  // Intentionally no hover/mouse parallax for hero image
  heroVisual.style.setProperty("--px", "0px");
  heroVisual.style.setProperty("--py", "0px");
}

if (yearNode) {
  yearNode.textContent = new Date().getFullYear().toString();
}

if (backToTop) {
  backToTop.addEventListener("click", () => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  });
}

if (shots.length > 0 && shotsDots && shotsStage) {
  let currentShot = 0;
  const loaded = new Set();

  function renderShot(index) {
    currentShot = (index + shots.length) % shots.length;
    shots.forEach((img, i) => {
      img.classList.toggle("active", i === currentShot);
    });
    const dots = Array.from(shotsDots.querySelectorAll(".shot-dot"));
    dots.forEach((dot, i) => {
      dot.classList.toggle("active", i === currentShot);
      dot.setAttribute("aria-current", i === currentShot ? "true" : "false");
    });
  }

  shots.forEach((img, index) => {
    const dot = document.createElement("button");
    dot.className = `shot-dot${index === 0 ? " active" : ""}`;
    dot.type = "button";
    const lang = (localStorage.getItem(I18N_STORAGE_KEY) || "ru") === "en" ? "en" : "ru";
    dot.setAttribute("aria-label", t(lang, "shots.slide", { n: index + 1 }));
    dot.setAttribute("aria-current", index === 0 ? "true" : "false");
    dot.addEventListener("click", () => renderShot(index));
    shotsDots.append(dot);

    img.addEventListener("load", () => {
      loaded.add(index);
      if (loaded.size > 0) {
        shotsStage.classList.add("has-images");
      }
    });

    img.addEventListener("error", () => {
      img.style.display = "none";
      if (currentShot === index) {
        const nextVisible = shots.findIndex((shot) => shot.style.display !== "none");
        if (nextVisible !== -1) {
          renderShot(nextVisible);
        }
      }
    });
  });

  if (shotPrev) {
    shotPrev.addEventListener("click", () => renderShot(currentShot - 1));
  }

  if (shotNext) {
    shotNext.addEventListener("click", () => renderShot(currentShot + 1));
  }
}
