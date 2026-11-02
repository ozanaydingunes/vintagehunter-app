/* VintageHunter 1.3 — arayüz. Veri ve kurallar yerel katmandadır (SQLite + Java kural motoru);
 * bu dosya yalnızca gösterir ve kullanıcı işlemlerini yerel katmana iletir. Örnek/sahte ilan yoktur. */
"use strict";
window.__errors = [];
window.addEventListener("error", (e) => window.__errors.push(String(e.message || e)));
window.addEventListener("unhandledrejection", (e) => window.__errors.push(String((e.reason && e.reason.message) || e.reason)));

/* ================================================================ yardımcılar */
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => Array.from(el.querySelectorAll(s));
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const clone = (o) => JSON.parse(JSON.stringify(o));
const nfTL0 = new Intl.NumberFormat("tr-TR", { maximumFractionDigits: 0 });
const nfTL2 = new Intl.NumberFormat("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
function tl(v) {
  if (v === null || v === undefined || v === "" || !isFinite(v)) return "—";
  const n = Number(v);
  return (Number.isInteger(n) ? nfTL0.format(n) : nfTL2.format(n)) + " TL";
}
function pct(v) { return v === null || v === undefined || !isFinite(v) ? "—" : "%" + Math.round(v); }
function parseNum(v) {
  if (v === null || v === undefined) return null;
  let s = String(v).trim().replace(/\s|TL|₺/gi, "");
  if (!s) return null;
  if (s.includes(",") && s.includes(".")) s = s.lastIndexOf(",") > s.lastIndexOf(".") ? s.replace(/\./g, "").replace(",", ".") : s.replace(/,/g, "");
  else if (s.includes(",")) s = /^\d{1,3}(,\d{3})+$/.test(s) ? s.replace(/,/g, "") : s.replace(",", ".");
  else if (/^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, "");
  const n = Number(s);
  return isFinite(n) ? n : null;
}
function ago(ms) {
  if (!ms) return "—";
  const m = Math.round((Date.now() - ms) / 60000);
  if (m < 1) return "şimdi";
  if (m < 60) return m + " dk önce";
  if (m < 1440) return Math.round(m / 60) + " sa önce";
  const d = Math.round(m / 1440);
  return d < 31 ? d + " gün önce" : new Date(ms).toLocaleDateString("tr-TR");
}
function when(ms) { return ms ? new Date(ms).toLocaleString("tr-TR", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }) : "—"; }
let toastT;
function toast(msg) { const t = $("#toast"); t.textContent = msg; t.hidden = false; clearTimeout(toastT); toastT = setTimeout(() => (t.hidden = true), 2800); }
function photoSrc(path) {
  if (!path) return null;
  const C = window.Capacitor;
  return C && C.convertFileSrc ? C.convertFileSrc(path) : path;
}

const I = {
  home: '<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/>',
  deals: '<path d="M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
  saved: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/><path d="M8 11h6M11 8v6"/>',
  coll: '<path d="M12 20.5s-7.5-4.6-7.5-10.1A4.3 4.3 0 0 1 12 7.6a4.3 4.3 0 0 1 7.5 2.8c0 5.5-7.5 10.1-7.5 10.1z"/>',
  settings: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
  bell: '<path d="M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8"/><path d="M10.3 21a1.9 1.9 0 0 0 3.4 0"/>',
  pulse: '<path d="M3 12h4l3-8 4 16 3-8h4"/>',
  filter: '<path d="M4 6h16M7 12h10M10 18h4"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  heart: '<path d="M12 20.5s-7.5-4.6-7.5-10.1A4.3 4.3 0 0 1 12 7.6a4.3 4.3 0 0 1 7.5 2.8c0 5.5-7.5 10.1-7.5 10.1z"/>',
  back: '<path d="M15 5l-7 7 7 7"/>',
  ext: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  x: '<path d="M18 6 6 18M6 6l12 12"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
  alert: '<path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17h.01"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V5a1 1 0 0 1 1-1h9"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.7L20 8"/><path d="M20 4v4h-4"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  chev: '<path d="m9 6 6 6-6 6"/>',
  tag: '<path d="M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
};
const icon = (k, cls = "") => `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${I[k] || ""}</svg>`;
const LOGO = `<svg viewBox="0 0 40 40" aria-hidden="true"><defs><linearGradient id="lg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#e8b27a"/><stop offset="1" stop-color="#a86a33"/></linearGradient></defs>
  <path d="M6 20 17 7h15a2 2 0 0 1 2 2v22a2 2 0 0 1-2 2H17z" fill="url(#lg)"/><circle cx="15.5" cy="20" r="2.6" fill="#0c0c0e"/>
  <path d="M21 14l3.2 12L28 14" fill="none" stroke="#1b1209" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>`;

const PLATFORMS = ["Dolap", "Gardrops", "Sahibinden", "Letgo"];
const TIER = { VERY_HIGH: ["Çok yüksek fırsat", "ok"], HIGH: ["Yüksek fırsat", "copper"], MEDIUM: ["Orta fırsat", "info"], LOW: ["Düşük fırsat", "mute"] };
const REASON = {
  OVER_MAX: "Fiyat sınırının üstünde", UNDER_MIN: "Alt fiyat sınırının altında", PRICE_MISSING: "Fiyat okunamadı",
  PRICE_AMBIGUOUS: "Fiyat belirsiz (birden çok tutar)", PRICE_FOREIGN: "Yabancı para birimi", AGGREGATE: "Toplu arama bildirimi",
  DEAL_UNKNOWN: "Piyasa değeri yok (aramanın fırsat şartı)", DEAL_LOW: "Fırsat oranı aramanın şartının altında", NO_MATCH: "Kayıtlı aramalarla eşleşmiyor",
};
const LIST_TAGS = { TRACK: "Takip ettiklerim", CONSIDER: "Satın almayı düşündüklerim", BOUGHT: "Satın aldıklarım" };
const NOTIFY = { INSTANT: "Anında", DIGEST: "Yalnız özet", OFF: "Kapalı" };
const DEFAULT_TEMPLATES = {
  Dolap: "https://dolap.com/ara?q={q}",
  Gardrops: "https://www.gardrops.com/search?q={q}",
  Sahibinden: "https://www.sahibinden.com/arama?query_text={q}&price_min={min}&price_max={max}&sorting=date_desc",
  Letgo: "https://www.letgo.com/arama?query={q}",
};
const DEFAULT_FILTER = { q: "", exclude: [], categories: [], platforms: [], minPrice: null, maxPrice: null, condition: null, since: "all", city: "", minDealPct: 0, favOnly: false };

/* ================================================================ yerel katman */
const P = window.Capacitor && window.Capacitor.Plugins ? window.Capacitor.Plugins.VHListener : null;
const api = P;

/* ================================================================ durum */
const S = {
  cfg: null, ui: { filter: clone(DEFAULT_FILTER), inLimitOnly: true, sort: "new", theme: "dark", templates: clone(DEFAULT_TEMPLATES), collTab: "fav", searchId: null },
  status: null, home: null, deals: { items: [], total: 0, loading: false, key: "" }, detail: null, platforms: null,
  confirm: null, editing: null, online: navigator.onLine,
};
let uiSaveT;
function saveUi() {
  clearTimeout(uiSaveT);
  uiSaveT = setTimeout(() => {
    try { localStorage.setItem("vh_ui", JSON.stringify(S.ui)); } catch (e) { /* yalnız yerel kopya */ }
    if (api) api.setUi({ ui: S.ui }).catch(() => {});
  }, 250);
}
async function loadUi() {
  let ui = null;
  if (api) { try { ui = (await api.getUi()).ui; } catch (e) { /* */ } }
  if (!ui || !Object.keys(ui).length) { try { ui = JSON.parse(localStorage.getItem("vh_ui") || "null"); } catch (e) { ui = null; } }
  if (ui) S.ui = { ...S.ui, ...ui, filter: { ...clone(DEFAULT_FILTER), ...(ui.filter || {}) }, templates: { ...clone(DEFAULT_TEMPLATES), ...(ui.templates || {}) } };
  applyTheme();
}
function applyTheme() {
  const t = S.ui.theme === "system" ? (matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark") : S.ui.theme;
  document.documentElement.setAttribute("data-theme", t);
  const m = $('meta[name="theme-color"]');
  if (m) m.setAttribute("content", t === "light" ? "#f3f0ea" : "#0c0c0e");
}
const cat = (id) => (S.cfg ? S.cfg.categories.find((c) => c.id === id) : null);
const search = (id) => (S.cfg ? S.cfg.searches.find((s) => s.id === id) : null);

/** Ayar değişikliği: kopyada değiştir → doğrula ve kaydet (yerel katman) → tüm ilanlar yeniden değerlendirilir. */
async function saveConfig(mutate, okMsg) {
  const next = clone(S.cfg);
  mutate(next);
  const r = await api.setConfig({ config: next });
  if (!r.ok) { toast(r.error || "Kaydedilemedi"); return false; }
  S.cfg = next;
  S.deals.key = "";
  if (okMsg) toast(okMsg);
  return true;
}

/* ================================================================ yönlendirme */
const TABS = [["home", "Ana Sayfa", "home"], ["deals", "Fırsatlar", "deals"], ["searches", "Aramalar", "saved"], ["collection", "Koleksiyon", "coll"], ["settings", "Ayarlar", "settings"]];
function route() {
  const h = decodeURIComponent(location.hash.slice(1) || "home");
  const [name, ...rest] = h.split("/");
  return { name, arg: rest.join("/") };
}
function go(h) { if (location.hash.slice(1) === h) render(); else location.hash = h; }
function tabOf(name) {
  if (["listing", "share"].includes(name)) return "deals";
  if (name === "search") return "searches";
  if (["status", "history", "categories"].includes(name)) return "settings";
  return name;
}

function renderShell() {
  const st = S.status, hs = S.home && S.home.stats;
  let dot = "", line = "Durum okunuyor…";
  if (st) {
    if (!st.listenerEnabled) { dot = "bad"; line = "Bildirim erişimi kapalı"; }
    else if (!st.listenerConnected) { dot = "warn"; line = "Avcı yeniden bağlanıyor"; }
    else { dot = "ok"; line = "Son bildirim: " + (hs && hs.lastNotificationAt ? ago(hs.lastNotificationAt) : "henüz yok"); }
  }
  const unread = hs ? hs.unreadNotifs || 0 : 0;
  $("#topbar").innerHTML = `
    <div class="brand">${LOGO}<div style="min-width:0"><div class="brand-name">Vintage <em>Hunter</em></div>
      <div class="brand-sub"><span class="dot ${dot}"></span><span>${esc(line)}</span></div></div></div>
    <button class="icon-btn" data-act="go" data-to="status" aria-label="Sistem durumu">${icon("pulse")}</button>
    <button class="icon-btn" data-act="go" data-to="history" aria-label="Bildirim geçmişi">${icon("bell")}${unread ? `<span class="badge">${unread > 99 ? "99+" : unread}</span>` : ""}</button>`;
  const cur = tabOf(route().name);
  $("#tabs").innerHTML = TABS.map(([k, l, ic]) => `<a href="#${k}" ${cur === k ? 'aria-current="page"' : ""}>${icon(ic)}<span>${l}</span></a>`).join("");
  const off = $("#offline");
  off.hidden = S.online;
  if (!S.online) off.innerHTML = `<div class="notice bad" style="border-radius:0">${icon("alert")}<div>İnternet bağlantısı yok. Kayıtlı ilanlar görüntülenebilir; ilanı platformda açmak için internet gerekir.</div></div>`;
}

let renderSeq = 0;
async function render() {
  const seq = ++renderSeq;
  renderShell();
  const { name, arg } = route();
  const v = VIEWS[name] || VIEWS.home;
  const main = $("#main");
  try {
    const html = await v.html(arg);
    if (seq !== renderSeq) return;
    main.innerHTML = `<div class="view">${html}</div>`;
    if (v.after) await v.after(arg);
  } catch (e) {
    if (seq !== renderSeq) return;
    main.innerHTML = `<div class="view"><div class="empty">${icon("alert")}<h3>Bu ekran yüklenemedi</h3><p>${esc(e && e.message ? e.message : e)}</p>
      <button class="btn" data-act="go" data-to="home">Ana sayfaya dön</button></div></div>`;
  }
  renderShell();
}

/* ================================================================ parçalar */
function phEmpty(item) {
  const p = item.platform || "?";
  const c = cat(item.category_id);
  return `<div class="ph-empty"><div><b>${esc(p.slice(0, 1))}</b><small>${esc(c ? c.name : "Fotoğraf yok")}</small></div></div>`;
}
function dealBadge(it) {
  if (it.tier && TIER[it.tier]) return `<span class="pill ${TIER[it.tier][1]}">${pct(it.deal_pct)} · ${TIER[it.tier][0].replace(" fırsat", "")}</span>`;
  if (it.in_limit === 1) return `<span class="pill ok">${icon("check").replace('class=""', 'style="width:12px;height:12px"')} Uygun</span>`;
  if (it.kind === "AGGREGATE") return `<span class="pill info">Toplu bildirim</span>`;
  if (it.reason && REASON[it.reason]) return `<span class="pill mute">${esc(REASON[it.reason].replace(" (birden çok tutar)", "").replace(" (aramanın fırsat şartı)", ""))}</span>`;
  return "";
}
function titleOf(it) {
  const t = (it.text || "").trim(), h = (it.title || "").trim();
  if (it.kind === "SHARED" || it.kind === "MANUAL") return h || t;
  return t.length > 6 ? t : (h + " " + t).trim();
}
function card(it) {
  const src = photoSrc(it.photo);
  const drop = it.prev_price && it.price && it.prev_price > it.price ? `<s>${tl(it.prev_price)}</s>` : "";
  return `<div class="pcard" role="button" tabindex="0" data-act="open" data-id="${esc(it.id)}">
    <div class="ph">${src ? `<img src="${esc(src)}" alt="" loading="lazy" decoding="async">` : phEmpty(it)}
      <span class="plat">${esc(it.platform || "")}</span>
      <button class="fav" data-act="fav" data-id="${esc(it.id)}" aria-pressed="${it.fav === 1}" aria-label="Favorilere ekle">${icon("heart")}</button>
      <span class="deal">${dealBadge(it)}</span></div>
    <div class="meta"><div class="price">${it.price ? tl(it.price) : '<span class="muted" style="font-size:14px">Fiyat yok</span>'}${drop}</div>
      <div class="t">${esc(titleOf(it))}</div>
      <div class="sub"><span>${ago(it.captured_at)}</span>${it.term ? `<span>· ${esc(it.term)}</span>` : ""}</div></div></div>`;
}
function rowItem(it, right = "") {
  const src = photoSrc(it.photo);
  return `<button class="lrow" data-act="open" data-id="${esc(it.id)}">
    <div class="thumb">${src ? `<img src="${esc(src)}" alt="" loading="lazy">` : phEmpty(it)}</div>
    <div style="min-width:0"><div class="t">${esc(titleOf(it))}</div><div class="s">${esc(it.platform || "")} · ${ago(it.captured_at)}</div></div>
    <div class="r"><b class="price">${it.price ? tl(it.price) : "—"}</b>${right || dealBadge(it)}</div></button>`;
}
const tog = (id, on, attrs = "") => `<label class="tog"><input type="checkbox" id="${id}" ${on ? "checked" : ""} ${attrs}><i></i></label>`;
const sw = (id, on, title, sub = "", attrs = "") => `<div class="switch"><div class="lbl"><b>${title}</b>${sub ? `<small>${sub}</small>` : ""}</div>${tog(id, on, attrs)}</div>`;

/* ================================================================ ekranlar */
const VIEWS = {};

/* ---------- ana sayfa ---------- */
VIEWS.home = {
  async html() {
    const [home, status] = await Promise.all([api.home(), api.status()]);
    S.home = home; S.status = status;
    const st = home.stats || {};
    const setup = setupCard(status);
    const any = (home.newest || []).length > 0;
    const favCats = S.cfg.categories.map((c) => {
      return `<button class="chip" data-act="cat-deals" data-cat="${esc(c.id)}">${esc(c.name)} <span class="n">≤ ${tl(c.maxPrice)}</span></button>`;
    }).join("");
    return `
      ${setup}
      <section class="stats">
        <button class="stat" data-act="deals-preset" data-p="today"><b>${st.today ?? 0}</b><span>Bugün bulunan ilan</span></button>
        <button class="stat hl" data-act="deals-preset" data-p="inlimit"><b>${st.todayInLimit ?? 0}</b><span>Fiyat limitine uygun</span></button>
        <button class="stat" data-act="deals-preset" data-p="high"><b>${st.todayHigh ?? 0}</b><span>Yüksek fırsat</span></button>
        <button class="stat" data-act="go" data-to="searches"><b>${st.activeSearches ?? 0}</b><span>Takip edilen arama</span></button>
      </section>
      ${any ? `
      <section style="display:grid;gap:12px"><div class="sec-head"><h2 class="h-sec">En yüksek fırsatlar</h2><button class="link" data-act="deals-preset" data-p="inlimit">Tümü</button></div>
        ${(home.top || []).length ? `<div class="rail">${home.top.map(card).join("")}</div>` : `<p class="muted small" style="margin:0">Fiyat limitinize uyan ilan henüz yok. Yeni ilanlar aşağıda.</p>`}</section>
      <section style="display:grid;gap:12px"><div class="sec-head"><h2 class="h-sec">Yeni eklenen ilanlar</h2><button class="link" data-act="deals-preset" data-p="all">Tümü</button></div>
        <div class="rail">${home.newest.map(card).join("")}</div></section>` : emptyHome(status)}
      <section style="display:grid;gap:10px"><h2 class="h-sec">Kategoriler</h2><div class="chips scroll">${favCats}</div></section>
      ${(home.recent || []).length ? `<section style="display:grid;gap:4px"><h2 class="h-sec">Son görüntülenen</h2><div class="list-card">${home.recent.slice(0, 5).map((x) => rowItem(x)).join("")}</div></section>` : ""}
      ${(home.aggregates || []).length ? `<section style="display:grid;gap:4px"><div class="sec-head"><h2 class="h-sec">Platform arama bildirimleri</h2></div>
        <p class="muted small" style="margin:0">Bu bildirimler birden çok ilanı birlikte haber veriyor; fiyat içermediği için fırsat sayılmaz. Dokununca platformda açılır.</p>
        <div class="list-card">${home.aggregates.map((x) => rowItem(x, '<span class="pill info">Toplu</span>')).join("")}</div></section>` : ""}`;
  },
};
function setupCard(st) {
  const steps = [
    [st.listenerEnabled, "Bildirim erişimini açın", "Platform bildirimlerini okuyabilmek için", "grant"],
    [st.notificationsAllowed, "Bildirim iznini verin", "Fırsat alarmlarını görebilmeniz için", "notif"],
    [st.batteryUnrestricted, "Pil kısıtlamasını kaldırın", "Avcının arka planda kesintisiz çalışması için", "battery"],
  ];
  if (steps.every((s) => s[0])) return "";
  return `<section class="card accent"><div><div class="eyebrow">Kurulum</div><h2 class="h-sec" style="margin-top:4px">Avcıyı çalışır hale getirin</h2></div>
    <ol class="steps">${steps.map(([ok, t, s, a]) => `<li class="${ok ? "done" : ""}"><div><b style="font-size:14px">${t}</b><div class="small muted">${s}</div></div>
      ${ok ? "" : `<button class="btn sm ${a === "grant" ? "primary" : ""}" data-act="${a}">${a === "grant" ? "Aç" : "İzin ver"}</button>`}</li>`).join("")}</ol>
    ${!st.listenerEnabled ? `<p class="small muted" style="margin:0">Anahtar gri ve basılmıyorsa: <button class="link" data-act="appinfo">Uygulama bilgisi</button> → sağ üstteki ⋮ → <b>Kısıtlanmış ayarlara izin ver</b>, sonra tekrar açın.</p>` : ""}</section>`;
}
function emptyHome(st) {
  return `<section class="empty">${icon("deals")}<h3>Henüz ilan yakalanmadı</h3>
    <p>VintageHunter siteleri kendisi taramaz. Dolap, Gardrops, Sahibinden ve Letgo uygulamalarında kurduğunuz <b>kayıtlı arama bildirimleri</b> telefona geldiği anda yakalanır, fiyat sınırınızla karşılaştırılır ve burada listelenir.</p>
    <div class="row" style="justify-content:center"><button class="btn" data-act="go" data-to="searches">Takip listelerim</button>
    <button class="btn" data-act="share-manual">${icon("plus")} İlanı elle ekle</button></div>
    <p class="small">İpucu: bir platform uygulamasında ilanı <b>Paylaş → VintageHunter</b> ile de ekleyebilirsiniz; ilanın gerçek bağlantısı saklanır.</p></section>`;
}

/* ---------- fırsatlar ---------- */
function filterParams() {
  const f = S.ui.filter;
  const days = { all: 0, "1": 1, "3": 3, "7": 7, "30": 30 }[f.since] || 0;
  let sinceMs = 0;
  if (days === 1) { const d = new Date(); d.setHours(0, 0, 0, 0); sinceMs = d.getTime(); } else if (days) sinceMs = Date.now() - days * 864e5;
  const p = { q: f.q || null, exclude: f.exclude, categories: f.categories, platforms: f.platforms, minPrice: f.minPrice, maxPrice: f.maxPrice,
    condition: f.condition, sinceMs, city: f.city || null, minDealPct: f.minDealPct || null, favOnly: !!f.favOnly, inLimitOnly: !!S.ui.inLimitOnly,
    listingsOnly: true, order: S.ui.sort, searchId: S.ui.searchId || null };
  Object.keys(p).forEach((k) => (p[k] === null || p[k] === undefined) && delete p[k]);
  return p;
}
function activeFilterCount() {
  const f = S.ui.filter;
  return [f.q, f.exclude.length, f.categories.length, f.platforms.length, f.minPrice != null, f.maxPrice != null, f.condition, f.since !== "all", f.city, f.minDealPct > 0, f.favOnly]
    .filter(Boolean).length;
}
VIEWS.deals = {
  async html() {
    const s = S.ui.searchId ? search(S.ui.searchId) : null;
    const f = S.ui.filter;
    const n = activeFilterCount();
    const chips = [];
    if (s) chips.push(`<button class="chip" aria-pressed="true" data-act="clear-search">${esc(s.name)} <span class="x">×</span></button>`);
    f.categories.forEach((c) => chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="categories" data-v="${esc(c)}">${esc(cat(c) ? cat(c).name : c)} <span class="x">×</span></button>`));
    f.platforms.forEach((p) => chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="platforms" data-v="${esc(p)}">${esc(p)} <span class="x">×</span></button>`));
    if (f.minPrice != null || f.maxPrice != null) chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="price">${f.minPrice != null ? tl(f.minPrice) : "0"} – ${f.maxPrice != null ? tl(f.maxPrice) : "∞"} <span class="x">×</span></button>`);
    if (f.minDealPct > 0) chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="minDealPct">Fırsat ≥ %${f.minDealPct} <span class="x">×</span></button>`);
    if (f.condition) chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="condition">${f.condition === "NEW" ? "Yeni" : "İkinci el"} <span class="x">×</span></button>`);
    if (f.since !== "all") chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="since">${{ "1": "Bugün", "3": "Son 3 gün", "7": "Son 7 gün", "30": "Son 30 gün" }[f.since]} <span class="x">×</span></button>`);
    if (f.city) chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="city">${esc(f.city)} <span class="x">×</span></button>`);
    if (f.favOnly) chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="favOnly">Favoriler <span class="x">×</span></button>`);
    f.exclude.forEach((x) => chips.push(`<button class="chip" aria-pressed="true" data-act="rm-f" data-k="exclude" data-v="${esc(x)}">− ${esc(x)} <span class="x">×</span></button>`));
    return `
      <div><h1 class="h-screen">Yeni Fırsatlar</h1><p class="lede">Platform bildirimlerinden yakalanan ilanlar. “Uygun” görünümünde yalnızca kategori fiyat sınırınıza uyan ve fiyatı okunmuş ilanlar var.</p></div>
      <div class="searchbar"><label class="field-search">${icon("search")}<input id="dq" type="search" placeholder="Ara: vintage nike ceket" value="${esc(f.q)}" aria-label="Anahtar kelime"></label>
        <button class="filter-btn" data-act="filter" aria-label="Filtreler">${icon("filter")}${n ? `<span class="badge">${n}</span>` : ""}</button></div>
      <div class="row between"><div class="seg" role="group" aria-label="Görünüm"><button data-act="inlimit" data-v="1" aria-pressed="${!!S.ui.inLimitOnly}">Uygun</button><button data-act="inlimit" data-v="0" aria-pressed="${!S.ui.inLimitOnly}">Tümü</button></div>
        <select class="input" id="dsort" style="width:auto;padding:8px 10px" aria-label="Sıralama">${[["new", "En yeni"], ["deal", "En yüksek fırsat"], ["price", "En düşük fiyat"]].map(([k, l]) => `<option value="${k}" ${S.ui.sort === k ? "selected" : ""}>${l}</option>`).join("")}</select></div>
      ${chips.length ? `<div class="chips">${chips.join("")}${chips.length > 1 ? `<button class="link" data-act="clear-f">Temizle</button>` : ""}</div>` : ""}
      <div id="dmeta" class="small muted"></div>
      <div id="dlist" class="grid"></div>
      <div id="dmore"></div>
      <button class="btn ghost" data-act="share-manual">${icon("plus")} İlanı elle ekle</button>`;
  },
  async after() { await loadDeals(true); },
};
async function loadDeals(reset) {
  const p = filterParams();
  const key = JSON.stringify(p);
  if (reset || key !== S.deals.key) { S.deals = { items: [], total: 0, loading: false, key }; }
  if (S.deals.loading) return;
  S.deals.loading = true;
  const more = $("#dmore");
  if (more) more.innerHTML = `<div class="loading"><div class="spin"></div></div>`;
  const r = await api.list({ ...p, offset: S.deals.items.length, limit: 30 });
  S.deals.items = S.deals.items.concat(r.items || []);
  S.deals.total = r.total || 0;
  S.deals.loading = false;
  const list = $("#dlist"), meta = $("#dmeta");
  if (!list) return;
  if (!S.deals.items.length) {
    list.outerHTML = `<div id="dlist" class="empty">${icon("search")}<h3>${S.ui.inLimitOnly ? "Fiyat sınırınıza uyan ilan yok" : "İlan bulunamadı"}</h3>
      <p>${activeFilterCount() || S.ui.searchId ? "Filtreleri gevşetin veya temizleyin." : S.ui.inLimitOnly ? "“Tümü” görünümünde sınır üstü ve fiyatı okunamayan ilanları da görebilirsiniz." : "Platform bildirimleri geldikçe ilanlar burada görünür."}</p></div>`;
  } else {
    if (list.classList.contains("empty")) list.outerHTML = `<div id="dlist" class="grid"></div>`;
    $("#dlist").innerHTML = S.deals.items.map(card).join("");
  }
  if (meta) meta.textContent = S.deals.total ? `${S.deals.total} ilan` : "";
  if (more) more.innerHTML = S.deals.items.length < S.deals.total ? `<button class="btn block" data-act="more">Daha fazla göster (${S.deals.total - S.deals.items.length})</button>` : "";
}

/* ---------- filtre paneli ---------- */
function openSheet(html, onBind) {
  const root = $("#sheet-root");
  root.innerHTML = `<div class="sheet-scrim" data-act="close-sheet"></div><div class="sheet" role="dialog" aria-modal="true"><div class="grab"></div>${html}</div>`;
  document.body.style.overflow = "hidden";
  if (onBind) onBind($(".sheet", root));
}
function closeSheet() { $("#sheet-root").innerHTML = ""; document.body.style.overflow = ""; }
function sliderMax() { return Math.max(5000, ...S.cfg.categories.map((c) => (c.maxPrice || 0) * 2)); }
let draft = null;
function openFilter() {
  draft = clone(S.ui.filter);
  const max = sliderMax();
  const lo = draft.minPrice ?? 0, hi = draft.maxPrice ?? max;
  openSheet(`
    <div class="sheet-head"><h2>Filtreler</h2><button class="icon-btn" data-act="close-sheet" aria-label="Kapat">${icon("x")}</button></div>
    <div class="fgroup"><label for="f_q">Anahtar kelime</label><input class="input" id="f_q" value="${esc(draft.q)}" placeholder="vintage nike ceket"><span class="hint">Tüm kelimeler ilanda geçmeli.</span></div>
    <div class="fgroup"><span class="flabel">Kategori</span><div class="chips">${S.cfg.categories.map((c) => `<button class="chip" data-act="ft" data-k="categories" data-v="${esc(c.id)}" aria-pressed="${draft.categories.includes(c.id)}">${esc(c.name)}</button>`).join("")}</div></div>
    <div class="fgroup"><span class="flabel">Platform</span><div class="chips">${PLATFORMS.map((p) => `<button class="chip" data-act="ft" data-k="platforms" data-v="${p}" aria-pressed="${draft.platforms.includes(p)}">${p}</button>`).join("")}</div></div>
    <div class="fgroup"><span class="flabel">Fiyat aralığı</span>
      <div class="range"><div class="track"></div><div class="fill" id="rfill"></div>
        <input type="range" id="r_lo" min="0" max="${max}" step="10" value="${lo}" aria-label="En düşük fiyat"><input type="range" id="r_hi" min="0" max="${max}" step="10" value="${hi}" aria-label="En yüksek fiyat"></div>
      <div class="range-ticks"><span>0</span><span>${nfTL0.format(max / 4)}</span><span>${nfTL0.format(max / 2)}</span><span>${nfTL0.format((max * 3) / 4)}</span><span>${nfTL0.format(max)}+</span></div>
      <div class="two"><label class="field"><span>En düşük</span><div class="tl-wrap"><input class="input tl" id="f_min" inputmode="decimal" value="${draft.minPrice ?? ""}" placeholder="0"></div></label>
        <label class="field"><span>En yüksek</span><div class="tl-wrap"><input class="input tl" id="f_max" inputmode="decimal" value="${draft.maxPrice ?? ""}" placeholder="sınırsız"></div></label></div>
      <span class="hint">Fiyat filtresi varken fiyatı okunamayan ilanlar gösterilmez.</span></div>
    <div class="fgroup"><span class="flabel">Durum</span><div class="chips">${[[null, "Tümü"], ["USED", "İkinci el"], ["NEW", "Yeni"]].map(([k, l]) => `<button class="chip" data-act="fone" data-k="condition" data-v="${k ?? ""}" aria-pressed="${draft.condition === k}">${l}</button>`).join("")}</div>
      <span class="hint">Yalnızca bildirimde açıkça yazıyorsa bilinir.</span></div>
    <div class="fgroup"><span class="flabel">İlan tarihi</span><div class="chips">${[["all", "Tümü"], ["1", "Bugün"], ["3", "3 gün"], ["7", "7 gün"], ["30", "30 gün"]].map(([k, l]) => `<button class="chip" data-act="fone" data-k="since" data-v="${k}" aria-pressed="${draft.since === k}">${l}</button>`).join("")}</div></div>
    <div class="fgroup"><label for="f_deal">En az fırsat oranı: <b id="f_deal_v">${draft.minDealPct ? "%" + draft.minDealPct : "kapalı"}</b></label>
      <input type="range" id="f_deal" min="0" max="90" step="5" value="${draft.minDealPct || 0}" style="width:100%;accent-color:var(--copper)">
      <span class="hint">Piyasa değeri girilmemiş ilanlar bu filtreden geçmez.</span></div>
    <div class="fgroup two"><label class="field"><span>Konum <em>il</em></span><input class="input" id="f_city" value="${esc(draft.city)}" placeholder="İstanbul"></label>
      <label class="field"><span>Hariç kelimeler</span><input class="input" id="f_ex" value="${esc(draft.exclude.join(", "))}" placeholder="çocuk, replika"></label></div>
    <div class="fgroup">${sw("f_fav", draft.favOnly, "Yalnızca favoriler")}${sw("f_inl", S.ui.inLimitOnly, "Yalnızca fiyat limitine uygun", "Kategori sınırı ve kayıtlı arama kuralları")}</div>
    <button class="btn ghost block" data-act="save-filter-search">${icon("plus")} Bu filtreyi kayıtlı arama yap</button>
    <div class="sheet-foot"><button class="btn" data-act="reset-filter">Temizle</button><button class="btn primary" data-act="apply-filter" id="f_apply">Uygula</button></div>`, bindFilter);
  countDraft();
}
function readDraft() {
  const sh = $(".sheet");
  if (!sh || !draft) return;
  draft.q = $("#f_q", sh).value.trim();
  draft.minPrice = parseNum($("#f_min", sh).value);
  draft.maxPrice = parseNum($("#f_max", sh).value);
  draft.city = $("#f_city", sh).value.trim();
  draft.exclude = $("#f_ex", sh).value.split(",").map((s) => s.trim()).filter(Boolean);
  draft.favOnly = $("#f_fav", sh).checked;
  draft.minDealPct = Number($("#f_deal", sh).value) || 0;
}
function syncRange() {
  const max = sliderMax(), lo = $("#r_lo"), hi = $("#r_hi"), fill = $("#rfill");
  if (!lo) return;
  const a = Math.min(+lo.value, +hi.value), b = Math.max(+lo.value, +hi.value);
  fill.style.left = (a / max) * 100 + "%";
  fill.style.right = 100 - (b / max) * 100 + "%";
}
let countT;
function countDraft() {
  clearTimeout(countT);
  countT = setTimeout(async () => {
    readDraft();
    const keep = S.ui.filter, keepInl = S.ui.inLimitOnly;
    S.ui.filter = draft;
    S.ui.inLimitOnly = $("#f_inl") ? $("#f_inl").checked : keepInl;
    const p = filterParams();
    S.ui.filter = keep; S.ui.inLimitOnly = keepInl;
    try {
      const r = await api.list({ ...p, offset: 0, limit: 1 });
      const b = $("#f_apply");
      if (b) b.textContent = `Uygula (${r.total} ilan)`;
    } catch (e) { /* */ }
  }, 200);
}
function bindFilter(sh) {
  syncRange();
  const max = sliderMax();
  sh.addEventListener("input", (e) => {
    const t = e.target;
    if (t.id === "r_lo" || t.id === "r_hi") {
      const a = Math.min(+$("#r_lo").value, +$("#r_hi").value), b = Math.max(+$("#r_lo").value, +$("#r_hi").value);
      $("#f_min").value = a > 0 ? a : "";
      $("#f_max").value = b < max ? b : "";
      syncRange();
    }
    if (t.id === "f_min" || t.id === "f_max") {
      const a = parseNum($("#f_min").value), b = parseNum($("#f_max").value);
      $("#r_lo").value = a ?? 0; $("#r_hi").value = b ?? max; syncRange();
    }
    if (t.id === "f_deal") $("#f_deal_v").textContent = +t.value ? "%" + t.value : "kapalı";
    countDraft();
  });
  sh.addEventListener("change", countDraft);
}

/* ---------- ilan detayı ---------- */
VIEWS.listing = {
  async html(id) {
    const r = await api.get({ id });
    S.detail = r;
    api.viewed({ id }).catch(() => {});
    const it = r.item;
    const c = cat(it.category_id), s = search(it.search_id);
    const src = photoSrc(it.photo);
    const lim = c && c.maxPrice != null ? tl(c.maxPrice) : "sınırsız";
    let verdict;
    if (it.in_limit === 1) verdict = `<div class="verdict ok">${icon("check")}<div><b>Fiyat limitinize uygun.</b> ${esc(c ? c.name : "")} sınırı ${lim}${s ? ` · arama: ${esc(s.name)}` : ""}</div></div>`;
    else if (it.kind === "AGGREGATE") verdict = `<div class="verdict mute">${icon("info")}<div><b>Toplu arama bildirimi.</b> Platform birden çok ilanı tek bildirimle haber verdi; tek ilan fiyatı olmadığı için fırsat sayılmadı.</div></div>`;
    else verdict = `<div class="verdict bad">${icon("x")}<div><b>${esc(REASON[it.reason] || "Uygun değil")}.</b> ${it.reason === "OVER_MAX" ? `${tl(it.price)} > ${esc(c ? c.name : "")} sınırı ${lim}` : it.reason === "PRICE_MISSING" ? "Bildirimde TL fiyatı yoktu; fırsat bildirimi gönderilmedi." : ""}</div></div>`;
    const mv = it.market_value ?? (s ? s.marketValue : null);
    const mvBasis = it.market_value != null ? "Sizin girdiğiniz değer" : s && s.marketValue != null ? `“${esc(s.name)}” aramasının referans değeri` : null;
    const link = it.url ? `<span class="pill ok">Bağlantı kayıtlı</span>` : r.linkAvailable ? `<span class="pill ok">Bağlantı açık</span>` : it.kind === "MANUAL" ? `<span class="pill mute">Bağlantı yok</span>` : `<span class="pill warn">Bağlantı süresi doldu</span>`;
    const tag = it.list_tag;
    return `
      <div class="hero">${src ? `<img src="${esc(src)}" alt="">` : phEmpty(it)}
        <div class="bar"><button class="icon-btn" data-act="back" aria-label="Geri">${icon("back")}</button>
          <button class="icon-btn" data-act="fav" data-id="${esc(it.id)}" aria-pressed="${it.fav === 1}" aria-label="Favori">${icon("heart")}</button></div></div>
      <section style="display:grid;gap:10px">
        <div class="row"><span class="pill copper">${esc(it.platform || "")}</span>${dealBadge(it)}${link}</div>
        <h1 class="dtitle">${esc(titleOf(it))}</h1>
        <div class="dprice price">${it.price ? tl(it.price) : '<span class="muted" style="font-size:20px">Fiyat okunamadı</span>'}${it.prev_price && it.price && it.prev_price > it.price ? `<s>${tl(it.prev_price)}</s>` : ""}</div>
        <div class="small muted">Bildirim: ${when(it.posted_at)} · Kayıt: ${ago(it.captured_at)}${it.city ? " · " + esc(it.city.charAt(0).toLocaleUpperCase("tr") + it.city.slice(1)) : ""}</div>
        ${verdict}
      </section>
      <div class="sticky-cta"><button class="btn primary" data-act="open-platform" data-id="${esc(it.id)}">${icon("ext")} ${it.url ? "İlanı aç" : "Platformda aç"}</button>
        <button class="btn" data-act="copy" data-text="${esc(titleOf(it))}" aria-label="Başlığı kopyala">${icon("copy")}</button></div>
      ${!it.url && !r.linkAvailable && it.kind !== "MANUAL" ? `<p class="small muted" style="margin:-8px 0 0">Platform bildirimi kapandığı için ilanın kendisine bağlantı kalmadı; “Platformda aç” uygulamayı açar. Başlığı kopyalayıp platformda aratabilirsiniz.</p>` : ""}
      <section class="card"><div class="sec-head"><h2 class="h-sec">Fırsat analizi</h2>${it.tier ? `<span class="pill ${TIER[it.tier][1]}">${TIER[it.tier][0]}</span>` : ""}</div>
        ${mv && it.price ? `<dl class="kv"><dt>Tahmini piyasa değeri</dt><dd>${tl(mv)}</dd><dt>İlan fiyatı</dt><dd>${tl(it.price)}</dd><dt>Fırsat avantajı</dt><dd><b>${pct(it.deal_pct)}</b></dd></dl>
          <div class="meter"><i style="width:${Math.max(0, Math.min(100, it.deal_pct || 0))}%"></i><u style="left:${S.cfg.settings.tierHigh}%"></u></div>
          <div class="small muted">Dayanak: ${mvBasis}. Çizgi: yüksek fırsat eşiği %${S.cfg.settings.tierHigh}.</div>`
          : `<div class="verdict mute">${icon("info")}<div><b>Piyasa değeri doğrulanamadı.</b> Rastgele değer üretilmez. Benzer ürünlerin satılmış fiyatlarını veya kendi tahmininizi girin.</div></div>`}
        <div class="two"><label class="field"><span>Piyasa değeri</span><div class="tl-wrap"><input class="input tl" id="mv" inputmode="decimal" value="${it.market_value ?? ""}" placeholder="${mv ?? "örn. 2000"}"></div></label>
          <label class="field"><span>Satılmış benzerleri <em>ops.</em></span><input class="input" id="comps" inputmode="decimal" placeholder="1500; 1800; 2000"></label></div>
        <div class="row"><button class="btn sm" data-act="save-mv" data-id="${esc(it.id)}">Kaydet ve hesapla</button>${it.market_value != null ? `<button class="btn sm ghost" data-act="clear-mv" data-id="${esc(it.id)}">Değeri sil</button>` : ""}</div>
        <span class="hint">Benzer fiyatları noktalı virgülle yazarsanız ortanca (medyan) değer piyasa değeri olarak kullanılır. Aktif ilan fiyatları satış fiyatı değildir.</span></section>
      <section class="card"><h2 class="h-sec">Listelerim</h2>
        <div class="chips">${Object.entries(LIST_TAGS).map(([k, l]) => `<button class="chip" data-act="set-list" data-id="${esc(it.id)}" data-v="${k}" aria-pressed="${tag === k}">${l}</button>`).join("")}</div>
        ${tag === "BOUGHT" ? purchaseForm(it) : ""}</section>
      ${(it.kind === "SHARED" || it.kind === "MANUAL") ? `<section class="card"><h2 class="h-sec">Fiyatı güncelle</h2><div class="row"><div class="tl-wrap grow"><input class="input tl" id="newprice" inputmode="decimal" value="${it.price ?? ""}"></div><button class="btn sm" data-act="set-price" data-id="${esc(it.id)}">Kaydet</button></div></section>` : ""}
      <section class="card"><h2 class="h-sec">Ayrıntılar</h2>
        <dl class="kv"><dt>Platform</dt><dd>${esc(it.platform || "—")}</dd><dt>Kategori</dt><dd>${esc(c ? c.name : "—")}</dd>
          <dt>Eşleşen arama</dt><dd>${esc(s ? s.name : "—")}</dd><dt>Eşleşen kelime</dt><dd>${esc(it.term || "—")}</dd>
          <dt>Durum</dt><dd>${{ NEW: "Yeni", USED: "İkinci el" }[it.condition] || "Belirtilmemiş"}</dd>
          ${it.ignored ? `<dt>Yok sayılan tutarlar</dt><dd>${esc(it.ignored)}</dd>` : ""}
          ${it.url ? `<dt>Bağlantı</dt><dd style="font-size:12px">${esc(it.url)}</dd>` : ""}</dl>
        <details><summary class="small muted" style="cursor:pointer">Gelen bildirim metni</summary><div class="raw" style="margin-top:8px">${esc(((it.title || "") + "\n" + (it.text || "")).trim())}</div></details>
        ${(r.notifs || []).length ? `<div class="small muted">Gönderilen bildirimler: ${r.notifs.map((n) => `${esc({ MATCH: "Uygun ilan", HIGH_DEAL: "Yüksek fırsat", PRICE_DROP: "Fiyat düştü" }[n.kind] || n.kind)} (${when(n.at)})`).join(" · ")}</div>` : ""}</section>
      ${S.confirm === "del:" + it.id ? `<div class="confirm"><span>Bu ilan kayıtlardan silinsin mi?</span><span class="row"><button class="btn sm" data-act="confirm-no">Vazgeç</button><button class="btn sm danger" data-act="del-yes" data-id="${esc(it.id)}">Sil</button></span></div>`
        : `<button class="btn ghost danger" data-act="del" data-id="${esc(it.id)}">${icon("trash")} İlanı sil</button>`}`;
  },
};
function purchaseForm(it) {
  let fees = {};
  try { fees = JSON.parse(it.fees || "{}") || {}; } catch (e) { fees = {}; }
  const res = profitOf(it.buy_price, it.sell_price, fees);
  return `<div style="display:grid;gap:10px;margin-top:6px">
    <div class="two"><label class="field"><span>Alış fiyatı</span><div class="tl-wrap"><input class="input tl" id="p_buy" inputmode="decimal" value="${it.buy_price ?? it.price ?? ""}"></div></label>
      <label class="field"><span>Satış fiyatı <em>ops.</em></span><div class="tl-wrap"><input class="input tl" id="p_sell" inputmode="decimal" value="${it.sell_price ?? ""}"></div></label></div>
    <div class="three"><label class="field"><span>Komisyon %</span><input class="input" id="p_rate" inputmode="decimal" value="${fees.rate ?? ""}" placeholder="0"></label>
      <label class="field"><span>Kargo</span><div class="tl-wrap"><input class="input tl" id="p_ship" inputmode="decimal" value="${fees.ship ?? ""}" placeholder="0"></div></label>
      <label class="field"><span>Diğer</span><div class="tl-wrap"><input class="input tl" id="p_other" inputmode="decimal" value="${fees.other ?? ""}" placeholder="0"></div></label></div>
    <label class="field"><span>Not</span><input class="input" id="p_note" value="${esc(it.note || "")}"></label>
    <div id="p_res">${profitHtml(res)}</div>
    <button class="btn sm" data-act="save-purchase" data-id="${esc(it.id)}">Kaydet</button></div>`;
}
function profitOf(buy, sell, fees) {
  if (buy == null || sell == null || buy === "" || sell === "") return null;
  try {
    const p = VH.computeProfit({ purchase: buy, sale: sell, rate: fees.rate || 0, acquisition: {}, selling: { outbound_shipping: fees.ship || 0, other_selling: fees.other || 0 } });
    return { gross: Number(sell) - Number(buy), net: p.profit, roi: p.roi, commission: p.commission };
  } catch (e) { return { error: e.message }; }
}
function profitHtml(r) {
  if (!r) return `<span class="hint">Satış fiyatını girince brüt ve net kâr hesaplanır.</span>`;
  if (r.error) return `<span class="hint" style="color:var(--bad)">${esc(r.error)}</span>`;
  return `<dl class="kv"><dt>Brüt kâr</dt><dd>${tl(r.gross)}</dd><dt>Komisyon</dt><dd>−${tl(r.commission)}</dd>
    <dt><b>Net kâr</b></dt><dd><b style="color:${r.net >= 0 ? "var(--ok)" : "var(--bad)"}">${tl(r.net)}</b></dd><dt>Yatırım getirisi</dt><dd>${pct(r.roi)}</dd></dl>`;
}

/* ---------- kayıtlı aramalar ---------- */
VIEWS.searches = {
  async html() {
    if (!S.home) S.home = await api.home();
    const counts = S.home.searchCounts || {};
    const byCat = S.cfg.categories.map((c) => ({ c, list: S.cfg.searches.filter((s) => s.categoryId === c.id) }));
    return `
      <div class="row between" style="align-items:flex-end"><div><h1 class="h-screen">Kayıtlı aramalar</h1>
        <p class="lede">${S.cfg.searches.filter((s) => s.active).length} etkin arama. Platform bildirimi geldiği anda bu aramalarla ve kategori fiyat sınırıyla karşılaştırılır.</p></div>
        <button class="btn primary sm" data-act="new-search">${icon("plus")} Yeni</button></div>
      ${byCat.map(({ c, list }) => `<section style="display:grid;gap:8px">
        <div class="sec-head"><h2 class="h-sec">${esc(c.name)}</h2><button class="link" data-act="go" data-to="categories">Sınır ${tl(c.maxPrice)}</button></div>
        ${list.length ? `<div class="list-card">${list.map((s) => {
          const n = counts[s.id] || {};
          const eff = Math.min(s.maxPrice ?? Infinity, c.maxPrice ?? Infinity);
          return `<div class="srow"><button style="all:unset;cursor:pointer;min-width:0" data-act="go" data-to="search/${esc(s.id)}">
              <div class="t">${esc(s.name)}</div><div class="s">${esc(s.keywords.slice(0, 6).join(", "))}${s.keywords.length > 6 ? ` +${s.keywords.length - 6}` : ""}</div>
              <div class="c"><span class="pill ${s.active ? "ok" : "mute"}">${s.active ? NOTIFY[s.notify] : "Duraklatıldı"}</span>
                <span>${s.minPrice ? tl(s.minPrice) + " – " : "≤ "}${isFinite(eff) ? tl(eff) : "sınırsız"}</span>
                <span>· ${s.platforms.length ? s.platforms.join(", ") : "Tüm platformlar"}</span>
                ${n.total ? `<span>· ${n.inLimit || 0}/${n.total} uygun</span>` : ""}</div></button>
            <div class="row" style="gap:6px;flex-wrap:nowrap"><button class="btn sm" data-act="search-results" data-id="${esc(s.id)}">Sonuçlar</button>
              <button class="btn sm ghost" data-act="search-platform" data-id="${esc(s.id)}" aria-label="Platformda ara">${icon("ext")}</button></div></div>`;
        }).join("")}</div>` : `<p class="small muted" style="margin:0">Bu kategoride arama yok.</p>`}</section>`).join("")}
      <p class="small muted">Arama sayısında sınır yoktur; 200'ün üzerindeki aramalar ve çok sayıda kelime bildirim işlemeyi yavaşlatabilir.</p>`;
  },
};
VIEWS.search = {
  async html(id) {
    const isNew = id === "new" || !search(id);
    let s;
    if (S.editing && S.editing.id === (isNew ? "new" : id)) s = S.editing;
    else if (!isNew) s = clone(search(id));
    else s = { id: "new", name: "", keywords: [], exclude: [], categoryId: S.cfg.categories[0].id, platforms: [], minPrice: null, maxPrice: null, minDealPct: null, marketValue: null, notify: "INSTANT", active: true };
    S.editing = s;
    const c = cat(s.categoryId);
    return `
      <div class="row"><button class="icon-btn" data-act="back" aria-label="Geri">${icon("back")}</button><h1 class="h-screen grow">${isNew ? "Yeni arama" : "Aramayı düzenle"}</h1></div>
      <section class="card">
        <label class="field"><span>Arama adı</span><input class="input" id="s_name" value="${esc(s.name)}" placeholder="Vintage Nike ceket"></label>
        <div class="field"><span>Anahtar kelimeler <em>biri geçmesi yeterli</em></span>
          <div class="kw" id="s_kw">${s.keywords.map((k, i) => `<button class="chip" aria-pressed="true" data-act="kw-rm" data-k="keywords" data-i="${i}">${esc(k)} <span class="x">×</span></button>`).join("")}
            <input id="s_kw_in" placeholder="Kelime yazıp Enter" data-kw="keywords" enterkeyhint="done"></div>
          <span class="hint">Birden çok kelimenin hepsi geçsin isterseniz + ile yazın: <b>Nike + ceket</b></span></div>
        <div class="field"><span>Hariç tutulacak kelimeler</span>
          <div class="kw" id="s_ex">${s.exclude.map((k, i) => `<button class="chip" aria-pressed="true" data-act="kw-rm" data-k="exclude" data-i="${i}">${esc(k)} <span class="x">×</span></button>`).join("")}
            <input id="s_ex_in" placeholder="örn. çocuk, replika" data-kw="exclude" enterkeyhint="done"></div></div>
        <label class="field"><span>Kategori</span><select class="input" id="s_cat">${S.cfg.categories.map((k) => `<option value="${esc(k.id)}" ${k.id === s.categoryId ? "selected" : ""}>${esc(k.name)} · sınır ${tl(k.maxPrice)}</option>`).join("")}</select></label>
        <div class="field"><span>Platformlar <em>hiçbiri seçili değilse tümü</em></span><div class="chips">${PLATFORMS.map((p) => `<button class="chip" data-act="s-plat" data-v="${p}" aria-pressed="${s.platforms.includes(p)}">${p}</button>`).join("")}</div></div>
        <div class="two"><label class="field"><span>En düşük fiyat</span><div class="tl-wrap"><input class="input tl" id="s_min" inputmode="decimal" value="${s.minPrice ?? ""}" placeholder="0"></div></label>
          <label class="field"><span>En yüksek fiyat</span><div class="tl-wrap"><input class="input tl" id="s_max" inputmode="decimal" value="${s.maxPrice ?? ""}" placeholder="${c && c.maxPrice != null ? c.maxPrice : "sınırsız"}"></div></label></div>
        <span class="hint" id="s_eff">${c && c.maxPrice != null ? `Kategori sınırı ${tl(c.maxPrice)} her zaman uygulanır; daha düşük bir üst sınır girebilirsiniz.` : ""}</span>
        <div class="two"><label class="field"><span>En az fırsat oranı <em>ops.</em></span><input class="input" id="s_deal" inputmode="decimal" value="${s.minDealPct ?? ""}" placeholder="örn. 45"></label>
          <label class="field"><span>Referans piyasa değeri <em>ops.</em></span><div class="tl-wrap"><input class="input tl" id="s_mv" inputmode="decimal" value="${s.marketValue ?? ""}"></div></label></div>
        <span class="hint">Fırsat oranı şartı koyarsanız, piyasa değeri bilinmeyen ilanlar bu arama için uygun sayılmaz. Referans değer sizin tahmininizdir.</span>
        <div class="field"><span>Bildirim</span><div class="chips">${Object.entries(NOTIFY).map(([k, l]) => `<button class="chip" data-act="s-notify" data-v="${k}" aria-pressed="${s.notify === k}">${l}</button>`).join("")}</div>
          <span class="hint">Kontrol sıklığı: platform bildirimi geldiği anda. “Yalnız özet” seçilirse ${S.cfg.settings.digestMinutes ? S.cfg.settings.digestMinutes + " dakikalık" : "dönemsel"} özette bildirilir.</span></div>
        ${sw("s_active", s.active, "Etkin", "Duraklatılan arama yeni ilanları eşleştirmez")}
      </section>
      <div class="row"><button class="btn primary grow" data-act="search-save">Kaydet</button>${!isNew ? `<button class="btn" data-act="search-platform" data-id="${esc(s.id)}">${icon("ext")} Platformda ara</button>` : ""}</div>
      ${!isNew ? (S.confirm === "sdel" ? `<div class="confirm"><span>“${esc(s.name)}” silinsin mi? Yakalanmış ilanlar silinmez.</span><span class="row"><button class="btn sm" data-act="confirm-no">Vazgeç</button><button class="btn sm danger" data-act="search-del-yes">Sil</button></span></div>`
        : `<button class="btn ghost danger" data-act="search-del">${icon("trash")} Aramayı sil</button>`) : ""}`;
  },
};
function readSearchForm() {
  const s = S.editing;
  if (!s || !$("#s_name")) return s;
  s.name = $("#s_name").value.trim();
  s.categoryId = $("#s_cat").value;
  s.minPrice = parseNum($("#s_min").value);
  s.maxPrice = parseNum($("#s_max").value);
  s.minDealPct = parseNum($("#s_deal").value);
  s.marketValue = parseNum($("#s_mv").value);
  s.active = $("#s_active").checked;
  for (const [k, inp] of [["keywords", "#s_kw_in"], ["exclude", "#s_ex_in"]]) {
    const v = $(inp).value.trim();
    if (v) { v.split(",").map((x) => x.trim()).filter(Boolean).forEach((x) => s[k].push(x)); $(inp).value = ""; }
  }
  return s;
}
function cleanSearch(s) {
  const o = { id: s.id, name: s.name, categoryId: s.categoryId, keywords: s.keywords, exclude: s.exclude, platforms: s.platforms, notify: s.notify, active: s.active };
  for (const k of ["minPrice", "maxPrice", "minDealPct", "marketValue"]) if (s[k] !== null && s[k] !== undefined && s[k] !== "") o[k] = s[k];
  return o;
}
function slug(s) { return (s || "arama").toLocaleLowerCase("tr").replace(/[^a-z0-9ğüşıöç]+/gi, "-").replace(/^-|-$/g, "").slice(0, 30) + "-" + Date.now().toString(36).slice(-4); }
async function saveSearchObj(s) {
  if (!s.name) return toast("Arama adı girin."), false;
  if (!s.keywords.length) return toast("En az bir anahtar kelime ekleyin."), false;
  const isNew = s.id === "new" || !search(s.id);
  if (isNew) s.id = slug(s.name);
  const ok = await saveConfig((cfg) => {
    const i = cfg.searches.findIndex((x) => x.id === s.id);
    if (i >= 0) cfg.searches[i] = cleanSearch(s); else cfg.searches.push(cleanSearch(s));
  }, isNew ? "Arama eklendi" : "Arama kaydedildi");
  if (ok) S.home = null;
  return ok ? s.id : false;
}
function platformSearch(s) {
  const c = cat(s.categoryId);
  const kw = (s.keywords[0] || s.name).replace(/\s*\+\s*/g, " ");
  const max = Math.min(s.maxPrice ?? Infinity, c && c.maxPrice != null ? c.maxPrice : Infinity);
  openSheet(`<div class="sheet-head"><h2>Platformda ara: ${esc(kw)}</h2><button class="icon-btn" data-act="close-sheet" aria-label="Kapat">${icon("x")}</button></div>
    <p class="small muted" style="margin-top:0">Platformun kendi arama sayfası açılır. Orada aramayı kaydedip bildirimini açarsanız yeni ilanlar VintageHunter'a da düşer. Fiyat filtresini yalnızca Sahibinden bağlantısı taşıyabiliyor; diğerlerinde sayfada seçin.</p>
    <div style="display:grid;gap:10px">${PLATFORMS.map((p) => {
      const u = (S.ui.templates[p] || "").replaceAll("{q}", encodeURIComponent(kw)).replaceAll("{min}", s.minPrice || 0).replaceAll("{max}", isFinite(max) ? max : "");
      return `<button class="btn block" data-act="open-url" data-url="${esc(u)}">${icon("ext")} ${p}</button>`;
    }).join("")}</div>`);
}

/* ---------- koleksiyon ---------- */
VIEWS.collection = {
  async html() {
    const tab = S.ui.collTab;
    const q = tab === "fav" ? { favOnly: true } : { listTag: tab };
    const r = await api.list({ ...q, offset: 0, limit: 200, order: "new" });
    const items = r.items || [];
    let summary = "";
    if (tab === "BOUGHT" && items.length) {
      let buy = 0, sell = 0, net = 0, sold = 0;
      items.forEach((it) => {
        let fees = {}; try { fees = JSON.parse(it.fees || "{}"); } catch (e) { /* */ }
        buy += Number(it.buy_price ?? it.price ?? 0);
        const p = profitOf(it.buy_price ?? it.price, it.sell_price, fees);
        if (p && !p.error) { sell += Number(it.sell_price); net += p.net; sold++; }
      });
      summary = `<section class="stats"><div class="stat"><b>${items.length}</b><span>Ürün</span></div><div class="stat"><b class="num" style="font-size:22px">${tl(buy)}</b><span>Toplam alış</span></div>
        <div class="stat"><b class="num" style="font-size:22px">${tl(sell)}</b><span>Satış (${sold} ürün)</span></div><div class="stat hl"><b class="num" style="font-size:22px">${tl(net)}</b><span>Net kâr</span></div></section>`;
    }
    return `<div><h1 class="h-screen">Koleksiyon</h1><p class="lede">Favorileriniz ve takip listeleriniz. Takip ettiğiniz ilanın fiyatı düşerse bildirim gelir.</p></div>
      <div class="chips scroll">${[["fav", "Favoriler"], ...Object.entries(LIST_TAGS)].map(([k, l]) => `<button class="chip" data-act="coll-tab" data-v="${k}" aria-pressed="${tab === k}">${l}</button>`).join("")}</div>
      ${summary}
      ${items.length ? `<div class="grid">${items.map(card).join("")}</div>` : `<div class="empty">${icon("heart")}<h3>Bu liste boş</h3><p>İlan detayında kalp simgesine veya liste düğmelerine dokunarak ekleyebilirsiniz.</p></div>`}`;
  },
};

/* ---------- ayarlar ---------- */
VIEWS.settings = {
  async html() {
    const st = S.cfg.settings;
    const status = S.status || (S.status = await api.status());
    return `<div><h1 class="h-screen">Ayarlar</h1></div>
      <section class="card"><div class="sec-head"><h2 class="h-sec">Platformlar</h2><button class="link" data-act="go" data-to="status">Durum ve hatalar</button></div>
        ${PLATFORMS.map((p) => sw("pl_" + p, st.platformEnabled[p] !== false, p, "", `data-plat="${p}"`)).join("")}</section>
      <section class="card"><div class="sec-head"><h2 class="h-sec">Kategori fiyat limitleri</h2><button class="link" data-act="go" data-to="categories">Düzenle</button></div>
        <dl class="kv">${S.cfg.categories.map((c) => `<dt>${esc(c.name)}</dt><dd><b>${c.maxPrice != null ? tl(c.maxPrice) : "sınırsız"}</b></dd>`).join("")}</dl></section>
      <section class="card"><h2 class="h-sec">Fırsat eşikleri</h2>
        <div class="three"><label class="field"><span>Çok yüksek ≥</span><input class="input" id="t_vh" inputmode="decimal" value="${st.tierVeryHigh}"></label>
          <label class="field"><span>Yüksek ≥</span><input class="input" id="t_h" inputmode="decimal" value="${st.tierHigh}"></label>
          <label class="field"><span>Orta ≥</span><input class="input" id="t_m" inputmode="decimal" value="${st.tierMedium}"></label></div>
        <span class="hint">Yüzde. “Yüksek” eşiği aynı zamanda öncelikli bildirim için minimum fırsat oranıdır (başlangıç %45). Altı “Düşük fırsat”.</span></section>
      <section class="card"><h2 class="h-sec">Bildirimler</h2>
        ${sw("n_inst", st.notifyInstant, "Uygun ilan bildirimi", "Fiyat limitine giren yeni ilan, anında")}
        ${sw("n_high", st.notifyPriorityHigh, "Yüksek fırsat öncelikli", "Fırsat oranı yüksek eşiğin üstündeyse ayrı alarm")}
        ${sw("n_drop", st.notifyPriceDrop, "Fiyat düşüşü", "Favori veya listedeki ilanın fiyatı düşerse")}
        <div class="two"><label class="field"><span>Özet sıklığı</span><select class="input" id="n_dig">${[[0, "Kapalı"], [30, "30 dakikada bir"], [60, "Saatte bir"]].map(([v, l]) => `<option value="${v}" ${st.digestMinutes === v ? "selected" : ""}>${l}</option>`).join("")}</select></label>
          <label class="field"><span>Özet saatleri</span><div class="row" style="flex-wrap:nowrap;gap:6px"><input class="input" id="n_ds" inputmode="numeric" value="${st.digestStartHour}"><span>–</span><input class="input" id="n_de" inputmode="numeric" value="${st.digestEndHour}"></div></label></div>
        <div class="two"><label class="field"><span>Sessiz saat başlangıcı</span><input class="input" id="n_qs" inputmode="numeric" value="${st.quietStart >= 0 ? st.quietStart : ""}" placeholder="kapalı"></label>
          <label class="field"><span>Sessiz saat bitişi</span><input class="input" id="n_qe" inputmode="numeric" value="${st.quietEnd >= 0 ? st.quietEnd : ""}" placeholder="kapalı"></label></div>
        <span class="hint">Özet yalnızca o dönemde yeni uygun ilan varsa gönderilir.</span>
        <div class="row"><button class="btn sm" data-act="test-notify">Bildirim testi</button><button class="btn sm ghost" data-act="notif">Android bildirim ayarları</button></div></section>
      <section class="card"><h2 class="h-sec">Genel hariç kelimeler</h2>
        <input class="input" id="g_ex" value="${esc(st.globalExclude.join(", "))}"><span class="hint">Bu kelimelerin geçtiği bildirimler hiçbir aramayla eşleşmez (örn. “arıyorum” ilanları).</span></section>
      <section class="card"><h2 class="h-sec">Tema</h2><div class="chips">${[["dark", "Koyu"], ["light", "Açık"], ["system", "Sistem"]].map(([k, l]) => `<button class="chip" data-act="theme" data-v="${k}" aria-pressed="${S.ui.theme === k}">${l}</button>`).join("")}</div></section>
      <section class="card"><h2 class="h-sec">Veri yenileme</h2>
        <p class="small muted" style="margin:0">İlanlar platform bildirimi geldiği anda kaydedilir. Uygulama açıkken liste 20 saniyede bir yenilenir; arka planda ayrıca bir tarama yapılmaz, pil harcanmaz.</p>
        <button class="btn sm" data-act="refresh" style="justify-self:start">${icon("refresh")} Şimdi yenile</button></section>
      <section class="card"><h2 class="h-sec">Eşleştirme denemesi</h2>
        <p class="small muted" style="margin:0">Bir bildirim metnini yapıştırın; kaydedilmeden nasıl değerlendirileceğini görün.</p>
        <div class="row"><select class="input" id="tt_p" style="width:auto">${PLATFORMS.map((p) => `<option>${p}</option>`).join("")}</select><input class="input grow" id="tt" placeholder="Levi's 501 vintage kot 180 TL"></div>
        <button class="btn sm" data-act="try" style="justify-self:start">Değerlendir</button><div id="tt_out" class="small"></div></section>
      <section class="card"><h2 class="h-sec">Platform arama bağlantıları</h2>
        <p class="small muted" style="margin:0">“Platformda ara” düğmelerinin açtığı adresler. Yanlış sayfa açılırsa platformda <b>nike</b> aratın, adresi kopyalayıp yapıştırın.</p>
        ${PLATFORMS.map((p) => `<label class="field"><span>${p}</span><div class="row" style="flex-wrap:nowrap"><input class="input grow" id="tpl_${p}" value="${esc(S.ui.templates[p])}" style="font-size:12.5px"><button class="btn sm" data-act="learn" data-p="${p}">Öğret</button></div></label>`).join("")}
        <button class="btn sm ghost" data-act="tpl-reset" style="justify-self:start">Varsayılana döndür</button></section>
      <section class="card"><h2 class="h-sec">Veri</h2>
        <div class="row"><button class="btn sm" data-act="backup">Yedekle</button><label class="btn sm">Geri yükle<input type="file" id="restore" accept="application/json,.json" hidden></label>
          <button class="btn sm" data-act="clear-cache">Önbelleği temizle</button></div>
        ${S.confirm === "history" ? `<div class="confirm"><span>Favori ve listelerde olmayan tüm ilanlar silinsin mi?</span><span class="row"><button class="btn sm" data-act="confirm-no">Vazgeç</button><button class="btn sm danger" data-act="history-yes">Sil</button></span></div>`
          : S.confirm === "reset" ? `<div class="confirm"><span>Kategori sınırları ve kayıtlı aramalar başlangıç ayarına dönsün mü?</span><span class="row"><button class="btn sm" data-act="confirm-no">Vazgeç</button><button class="btn sm danger" data-act="reset-yes">Sıfırla</button></span></div>`
          : `<div class="row"><button class="btn sm ghost danger" data-act="ask" data-v="history">İlan geçmişini temizle</button><button class="btn sm ghost danger" data-act="ask" data-v="reset">Varsayılan ayarlar</button></div>`}
        <span class="hint">Yedek; ayarları, kayıtlı aramaları, favorileri ve ilan geçmişini içerir (fotoğraflar hariç).</span></section>
      <section class="card"><dl class="kv"><dt>Sürüm</dt><dd>${esc(status.versionName || "")}</dd><dt>Android</dt><dd>${status.sdk} · ${esc(status.manufacturer || "")}</dd></dl>
        <button class="btn sm ghost" data-act="go" data-to="history" style="justify-self:start">Bildirim geçmişi</button></section>`;
  },
};
VIEWS.categories = {
  async html() {
    return `<div class="row"><button class="icon-btn" data-act="back" aria-label="Geri">${icon("back")}</button><h1 class="h-screen grow">Kategori fiyat limitleri</h1></div>
      <p class="lede" style="margin:0">Her kategori kendi sınırıyla çalışır. Sınır dahildir: 1.500 TL sınırında 1.500 TL uygun, 1.501 TL uygun değildir. Değişiklik kaydedilince tüm ilanlar ve bildirimler yeni sınırla değerlendirilir.</p>
      <section class="list-card">${S.cfg.categories.map((c) => {
        const used = S.cfg.searches.filter((s) => s.categoryId === c.id).length;
        return `<div class="srow" style="grid-template-columns:minmax(0,1fr)"><div style="display:grid;gap:8px">
          <label class="field"><span>Ad</span><input class="input" id="cn_${esc(c.id)}" value="${esc(c.name)}"></label>
          <div class="two"><label class="field"><span>En yüksek fiyat</span><div class="tl-wrap"><input class="input tl" id="cx_${esc(c.id)}" inputmode="decimal" value="${c.maxPrice ?? ""}" placeholder="sınırsız"></div></label>
            <label class="field"><span>En düşük <em>ops.</em></span><div class="tl-wrap"><input class="input tl" id="cm_${esc(c.id)}" inputmode="decimal" value="${c.minPrice ?? ""}" placeholder="0"></div></label></div>
          <div class="row between"><span class="small muted">${used} kayıtlı arama</span>${used ? "" : `<button class="btn sm ghost danger" data-act="cat-del" data-id="${esc(c.id)}">Sil</button>`}</div></div></div>`;
      }).join("")}</section>
      <section class="card"><h2 class="h-sec">Kategori ekle</h2><div class="two"><label class="field"><span>Ad</span><input class="input" id="nc_name" placeholder="Vintage saatler"></label>
        <label class="field"><span>En yüksek fiyat</span><div class="tl-wrap"><input class="input tl" id="nc_max" inputmode="decimal"></div></label></div>
        <button class="btn sm" data-act="cat-add" style="justify-self:start">${icon("plus")} Ekle</button></section>
      <button class="btn primary block" data-act="cat-save">Sınırları kaydet</button>`;
  },
};

/* ---------- sistem durumu ---------- */
VIEWS.status = {
  async html() {
    const [st, pl, raw] = await Promise.all([api.status(), api.platforms(), api.rawLog({ limit: 50 })]);
    S.status = st; S.platforms = pl.items;
    const line = (ok, t, sub, btn) => `<div class="switch"><div class="lbl"><b><span class="dot ${ok ? "ok" : "bad"}" style="display:inline-block;margin-right:8px"></span>${t}</b><small>${sub}</small></div>${btn || ""}</div>`;
    return `<div class="row"><button class="icon-btn" data-act="back" aria-label="Geri">${icon("back")}</button><h1 class="h-screen grow">Sistem durumu</h1></div>
      <section class="card">
        ${line(st.listenerEnabled, "Bildirim erişimi", st.listenerEnabled ? "Açık — platform bildirimleri okunabiliyor" : "Kapalı — hiçbir ilan yakalanamaz", st.listenerEnabled ? "" : `<button class="btn sm primary" data-act="grant">Aç</button>`)}
        ${line(st.listenerConnected, "Avcı bağlantısı", st.listenerConnected ? "Bağlı — uygulama kapalıyken de çalışır" : "Bağlı değil — uygulama yeniden bağlamayı denedi", "")}
        ${line(st.notificationsAllowed, "Uygulama bildirimleri", st.notificationsAllowed ? "İzin var" : "Kapalı — alarmları göremezsiniz", st.notificationsAllowed ? "" : `<button class="btn sm" data-act="notif">Aç</button>`)}
        ${line(st.batteryUnrestricted, "Pil kısıtlaması", st.batteryUnrestricted ? "Kısıtlama yok" : "Kısıtlı — bazı telefonlar arka planda durdurabilir", st.batteryUnrestricted ? "" : `<button class="btn sm" data-act="battery">Kaldır</button>`)}
        <div class="small muted">Sonraki özet: ${S.cfg.settings.digestMinutes ? when(st.digestNext) + " (yalnızca yeni uygun ilan varsa)" : "kapalı"}${st.migratedCount ? ` · 1.2'den ${st.migratedCount} kayıt taşındı` : ""}</div></section>
      ${pl.items.map((p) => `<section class="card plat-card"><div class="row between"><h2 class="h-sec">${p.platform}</h2>${tog("ps_" + p.platform, p.enabled, `data-plat="${p.platform}"`)}</div>
        <div class="small ${p.installed ? "muted" : ""}" style="${p.installed ? "" : "color:var(--bad)"}">${p.installed ? "Uygulama yüklü · " + esc(p.pkg) : "Uygulama bu telefonda bulunamadı — bildirim gelmez"}</div>
        <div class="stats-mini"><div><b>${p.notifs24h}</b><span>bildirim</span></div><div><b>${p.matched24h}</b><span>eşleşen</span></div><div><b>${p.inLimit24h}</b><span>uygun</span></div><div><b>${p.unreadable24h}</b><span>fiyatsız</span></div></div>
        <dl class="kv small"><dt>Son bildirim</dt><dd>${p.lastNotificationAt ? ago(p.lastNotificationAt) : "henüz yok"}</dd><dt>Son ilan</dt><dd>${p.lastListingAt ? ago(p.lastListingAt) : "—"}</dd>
          <dt>Kontrol</dt><dd>Anlık — bildirim geldiğinde</dd><dt>Toplam ilan</dt><dd>${p.totalListings}</dd></dl>
        ${p.installed ? `<button class="btn sm ghost" data-act="open-app" data-p="${p.platform}" style="justify-self:start">${icon("ext")} ${p.platform} uygulamasını aç</button>` : ""}</section>`).join("")}
      <section class="card"><div class="sec-head"><h2 class="h-sec">Son platform bildirimleri</h2><div class="row" style="gap:6px"><button class="btn sm" data-act="copy-diag">${icon("copy")} Tanı raporu</button><button class="btn sm ghost" data-act="clear-raw">Temizle</button></div></div>
        <p class="small muted" style="margin:0">Platform uygulamalarından gelen son 50 bildirim ve sonucu. Eşleşmeyenler ilan olarak kaydedilmez; burada yalnızca tanı için durur.</p>
        <div class="log">${(raw.items || []).map((r) => `<div><b>${esc(r.platform)} · ${ago(r.at)} · ${esc(r.result)}</b><span class="muted">${esc(((r.title || "") + " — " + (r.text || "")).slice(0, 180))}</span></div>`).join("") || `<p class="small muted">Henüz platform bildirimi gelmedi.</p>`}</div></section>`;
  },
};
VIEWS.history = {
  async html() {
    const r = await api.notifLog({ limit: 150, markSeen: true });
    const K = { MATCH: ["Uygun ilan", "ok"], HIGH_DEAL: ["Yüksek fırsat", "copper"], PRICE_DROP: ["Fiyat düştü", "info"], DIGEST: ["Özet", "mute"] };
    return `<div class="row"><button class="icon-btn" data-act="back" aria-label="Geri">${icon("back")}</button><h1 class="h-screen grow">Bildirim geçmişi</h1></div>
      ${(r.items || []).length ? `<div class="list-card">${r.items.map((n) => `<button class="lrow" ${n.listing_id && n.l_title !== undefined ? `data-act="open" data-id="${esc(n.listing_id)}"` : 'data-act="go" data-to="deals"'}>
        <div class="thumb">${n.l_photo ? `<img src="${esc(photoSrc(n.l_photo))}" alt="">` : `<div class="ph-empty"><b style="font-size:20px">${esc((n.l_platform || "V").slice(0, 1))}</b></div>`}</div>
        <div style="min-width:0"><div class="t">${esc(n.title || "")}</div><div class="s">${esc(n.body || n.l_title || "")}</div></div>
        <div class="r"><span class="pill ${(K[n.kind] || ["", "mute"])[1]}">${esc((K[n.kind] || [n.kind])[0])}</span><span class="small muted">${ago(n.at)}</span></div></button>`).join("")}</div>`
        : `<div class="empty">${icon("bell")}<h3>Henüz bildirim gönderilmedi</h3><p>Fiyat limitinize uyan ilk ilan yakalandığında burada görünür.</p></div>`}`;
  },
};
VIEWS.share = {
  async html() {
    const sh = S.shareDraft || { text: "", subject: "" };
    const text = [sh.subject, sh.text].filter((x) => x && x !== "null").join(" ");
    const m = text.match(/https?:\/\/\S+/);
    const url = m ? m[0].replace(/[),.]+$/, "") : "";
    const platform = /dolap/i.test(url) ? "Dolap" : /gardrops/i.test(url) ? "Gardrops" : /sahibinden/i.test(url) ? "Sahibinden" : /letgo/i.test(url) ? "Letgo" : url ? "Diğer" : "Dolap";
    let title = text.replace(url, "")
      .replace(/\b(dolap|gardrops|sahibinden(?:\.com)?|letgo)(?:['’][a-zçğıöşü]+)?/gi, "")
      .replace(/(bu|şu)\s+(ürüne|ilana|ürünü|ilanı)\s+(göz\s+at|bak|incele)[a-zçğıöşü]*\s*:?/gi, "")
      .replace(/(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,]\d{1,2})?\s?(?:TL|₺)/gi, "")
      .replace(/\s{2,}/g, " ").replace(/^[\s:·\-–'’]+|[\s:·\-–]+$/g, "");
    const price = (text.match(/(\d{1,3}(?:[.,]\d{3})+|\d+)(?:[.,]\d{1,2})?\s?(?:TL|₺)/i) || [])[0];
    return `<div class="row"><button class="icon-btn" data-act="back" aria-label="Geri">${icon("back")}</button><h1 class="h-screen grow">${url ? "Paylaşılan ilan" : "İlanı elle ekle"}</h1></div>
      <section class="card">
        ${url ? `<div class="small muted" style="overflow-wrap:anywhere">${esc(url)}</div>` : `<label class="field"><span>İlan bağlantısı <em>ops.</em></span><input class="input" id="sh_url" type="url" placeholder="https://"></label>`}
        <label class="field"><span>Platform</span><select class="input" id="sh_p">${[...PLATFORMS, "Diğer"].map((p) => `<option ${p === platform ? "selected" : ""}>${p}</option>`).join("")}</select></label>
        <label class="field"><span>Başlık</span><input class="input" id="sh_t" value="${esc(title)}" placeholder="Levi's 501 vintage kot"></label>
        <label class="field"><span>Fiyat</span><div class="tl-wrap"><input class="input tl" id="sh_price" inputmode="decimal" value="${price ? esc(String(parseNum(price))) : ""}"></div></label>
        <label class="field"><span>Not <em>ops.</em></span><input class="input" id="sh_note"></label>
        <span class="hint">Kaydedince kayıtlı aramalarınız ve kategori sınırınızla değerlendirilir.</span></section>
      <button class="btn primary block" data-act="share-save" data-url="${esc(url)}">Kaydet</button>`;
  },
};

/* ================================================================ olaylar */
document.addEventListener("click", async (e) => {
  const el = e.target.closest("[data-act]");
  if (!el) return;
  const a = el.dataset.act, id = el.dataset.id;
  if (el.tagName === "A") return;
  e.preventDefault();
  try {
    switch (a) {
      case "go": closeSheet(); S.confirm = null; if (/^search\//.test(el.dataset.to)) S.editing = null; go(el.dataset.to); break;
      case "new-search": S.editing = null; S.confirm = null; go("search/new"); break;
      case "back": S.confirm = null; if (history.length > 1) history.back(); else go("home"); break;
      case "open": if (e.target.closest('[data-act="fav"]')) return; S.confirm = null; go("listing/" + id); break;
      case "fav": {
        e.stopPropagation();
        const on = el.getAttribute("aria-pressed") !== "true";
        await api.setFav({ id, fav: on });
        $$(`[data-act="fav"][data-id="${CSS.escape(id)}"]`).forEach((b) => b.setAttribute("aria-pressed", on));
        toast(on ? "Favorilere eklendi" : "Favorilerden çıkarıldı");
        break;
      }
      case "grant": await api.openListenerSettings(); break;
      case "appinfo": await api.openAppDetails(); break;
      case "notif": {
        await api.requestPostNotifications();
        setTimeout(async () => { const st = await api.status(); if (!st.notificationsAllowed) api.openNotificationSettings(); else { S.status = st; render(); } }, 1500);
        break;
      }
      case "battery": await api.requestIgnoreBattery(); break;
      case "deals-preset": {
        S.ui.filter = clone(DEFAULT_FILTER); S.ui.searchId = null;
        const p = el.dataset.p;
        S.ui.inLimitOnly = p !== "all" && p !== "today";
        if (p === "today") S.ui.filter.since = "1";
        if (p === "high") { S.ui.sort = "deal"; S.ui.filter.minDealPct = S.cfg.settings.tierHigh; } else S.ui.sort = "new";
        saveUi(); go("deals"); break;
      }
      case "cat-deals": S.ui.filter = { ...clone(DEFAULT_FILTER), categories: [el.dataset.cat] }; S.ui.searchId = null; S.ui.inLimitOnly = true; saveUi(); go("deals"); break;
      case "inlimit": S.ui.inLimitOnly = el.dataset.v === "1"; saveUi(); render(); break;
      case "filter": openFilter(); break;
      case "close-sheet": closeSheet(); break;
      case "ft": {
        readDraft();
        const k = el.dataset.k, v = el.dataset.v, arr = draft[k];
        const i = arr.indexOf(v);
        if (i >= 0) arr.splice(i, 1); else arr.push(v);
        el.setAttribute("aria-pressed", i < 0);
        countDraft(); break;
      }
      case "fone": {
        readDraft();
        const k = el.dataset.k, v = el.dataset.v || null;
        draft[k] = k === "since" ? v || "all" : v;
        $$(`[data-act="fone"][data-k="${k}"]`).forEach((b) => b.setAttribute("aria-pressed", (b.dataset.v || null) === (k === "since" ? draft[k] : v)));
        countDraft(); break;
      }
      case "reset-filter": draft = clone(DEFAULT_FILTER); closeSheet(); openFilter(); break;
      case "apply-filter": {
        readDraft();
        if (draft.minPrice != null && draft.maxPrice != null && draft.minPrice > draft.maxPrice) { toast("En düşük fiyat en yüksekten büyük olamaz"); return; }
        S.ui.filter = draft; S.ui.inLimitOnly = $("#f_inl").checked; saveUi(); closeSheet(); render(); break;
      }
      case "save-filter-search": {
        readDraft();
        const words = (draft.q || "").trim().split(/\s+/).filter(Boolean);
        if (!words.length) { toast("Önce bir anahtar kelime yazın"); return; }
        const c = draft.categories[0] || S.cfg.categories[0].id;
        S.editing = { id: "new", name: draft.q, keywords: [words.join(" + ")], exclude: draft.exclude, categoryId: c, platforms: draft.platforms,
          minPrice: draft.minPrice, maxPrice: draft.maxPrice, minDealPct: draft.minDealPct || null, marketValue: null, notify: "INSTANT", active: true };
        closeSheet(); go("search/new"); break;
      }
      case "rm-f": {
        const k = el.dataset.k, f = S.ui.filter;
        if (k === "price") { f.minPrice = null; f.maxPrice = null; }
        else if (Array.isArray(f[k])) f[k] = f[k].filter((x) => x !== el.dataset.v);
        else f[k] = DEFAULT_FILTER[k];
        saveUi(); render(); break;
      }
      case "clear-f": S.ui.filter = clone(DEFAULT_FILTER); S.ui.searchId = null; saveUi(); render(); break;
      case "clear-search": S.ui.searchId = null; saveUi(); render(); break;
      case "more": await loadDeals(false); break;
      case "open-platform": {
        if (!S.online && S.detail && S.detail.item.url) toast("İnternet yok; bağlantı açılamayabilir.");
        const r = await api.openListing({ id });
        if (r.opened === "app") toast("İlan bağlantısı artık yok; platform uygulaması açıldı.");
        else if (r.opened === "none") toast("Platform uygulaması bulunamadı.");
        break;
      }
      case "copy": {
        const t = el.dataset.text;
        try { await navigator.clipboard.writeText(t); toast("Başlık kopyalandı"); } catch (err) { toast("Kopyalanamadı"); }
        break;
      }
      case "save-mv": case "clear-mv": {
        let v = a === "clear-mv" ? null : parseNum($("#mv").value);
        const comps = a === "save-mv" ? $("#comps").value.split(/[;\n]/).map(parseNum).filter((x) => x && x > 0) : [];
        if (comps.length) {
          const ref = VH.estimateReference(comps.map((p) => ({ price: p, sale_type: "SOLD_CONFIRMED", date: new Date().toISOString().slice(0, 10) })), null);
          v = ref.ref;
          toast(`${comps.length} satış fiyatının ortancası: ${tl(v)}`);
        }
        if (a === "save-mv" && !(v > 0)) { toast("Geçerli bir piyasa değeri girin"); return; }
        await api.setMarket({ id, value: v });
        render(); break;
      }
      case "set-list": {
        const v = el.getAttribute("aria-pressed") === "true" ? null : el.dataset.v;
        await api.setList({ id, list: v });
        toast(v ? LIST_TAGS[v] + " listesine eklendi" : "Listeden çıkarıldı");
        render(); break;
      }
      case "save-purchase": {
        const fees = { rate: parseNum($("#p_rate").value) || 0, ship: parseNum($("#p_ship").value) || 0, other: parseNum($("#p_other").value) || 0 };
        if (fees.rate < 0 || fees.rate > 100) { toast("Komisyon %0–100 olmalı"); return; }
        await api.setPurchase({ id, buy: parseNum($("#p_buy").value), sell: parseNum($("#p_sell").value), fees, note: $("#p_note").value });
        toast("Kaydedildi"); render(); break;
      }
      case "set-price": await api.setPrice({ id, price: parseNum($("#newprice").value) }); toast("Fiyat güncellendi"); render(); break;
      case "del": S.confirm = "del:" + id; render(); break;
      case "del-yes": await api.remove({ id }); S.confirm = null; toast("İlan silindi"); history.back(); break;
      case "confirm-no": S.confirm = null; render(); break;
      case "kw-rm": readSearchForm(); S.editing[el.dataset.k].splice(+el.dataset.i, 1); rerenderSearch(); break;
      case "s-plat": {
        readSearchForm();
        const p = el.dataset.v, arr = S.editing.platforms, i = arr.indexOf(p);
        if (i >= 0) arr.splice(i, 1); else arr.push(p);
        el.setAttribute("aria-pressed", i < 0); break;
      }
      case "s-notify": readSearchForm(); S.editing.notify = el.dataset.v; $$('[data-act="s-notify"]').forEach((b) => b.setAttribute("aria-pressed", b.dataset.v === el.dataset.v)); break;
      case "search-save": {
        const s = readSearchForm();
        const ok = await saveSearchObj(s);
        if (ok) { S.editing = null; go("searches"); }
        break;
      }
      case "search-del": S.confirm = "sdel"; rerenderSearch(); break;
      case "search-del-yes": {
        const sid = S.editing.id;
        await saveConfig((cfg) => { cfg.searches = cfg.searches.filter((x) => x.id !== sid); }, "Arama silindi");
        S.confirm = null; S.editing = null; go("searches"); break;
      }
      case "search-results": S.ui.searchId = id; S.ui.filter = clone(DEFAULT_FILTER); S.ui.inLimitOnly = false; saveUi(); go("deals"); break;
      case "search-platform": platformSearch(id === "new" ? readSearchForm() : search(id) || readSearchForm()); break;
      case "open-url": await api.openUrl({ url: el.dataset.url }); closeSheet(); break;
      case "coll-tab": S.ui.collTab = el.dataset.v; saveUi(); render(); break;
      case "theme": S.ui.theme = el.dataset.v; saveUi(); applyTheme(); render(); break;
      case "test-notify": { const r = await api.testNotify(); toast(r.ok ? "Test bildirimi gönderildi" : "Bildirim izni yok"); break; }
      case "refresh": await refreshData(true); toast("Yenilendi"); break;
      case "try": {
        const r = await api.tryText({ text: $("#tt").value, platform: $("#tt_p").value });
        const s = search(r.searchId);
        $("#tt_out").innerHTML = `<dl class="kv"><dt>Fiyat</dt><dd>${r.price ? tl(r.price) : esc(REASON["PRICE_" + r.priceStatus] || r.priceStatus)}${r.oldPrice ? ` (eski ${tl(r.oldPrice)})` : ""}</dd>
          ${r.ignored ? `<dt>Yok sayılan</dt><dd>${esc(r.ignored)}</dd>` : ""}<dt>Eşleşme</dt><dd>${r.matched ? esc((s ? s.name : r.searchId) + " · " + r.term) : "Yok"}</dd>
          <dt>Sonuç</dt><dd><b style="color:${r.inLimit ? "var(--ok)" : "var(--bad)"}">${r.inLimit ? "Uygun ilan" : esc(REASON[r.reason] || r.reason || "Uygun değil")}</b></dd>
          <dt>Bildirim</dt><dd>${{ MATCH: "Anında", HIGH_DEAL: "Öncelikli", DIGEST: "Özette", NONE: "Gönderilmez" }[r.alert] || r.alert}</dd></dl>`;
        break;
      }
      case "learn": {
        const p = el.dataset.p, v = $("#tpl_" + p).value.trim();
        let tpl = null;
        try {
          const u = new URL(v);
          const s = u.toString();
          const hit = ["nike", "Nike", "NIKE"].find((w) => s.includes(w));
          if (hit) tpl = s.split(hit).join("{q}");
        } catch (err) { tpl = null; }
        if (!tpl) { toast("Adreste “nike” bulunamadı. Platformda nike aratıp adresi yapıştırın."); return; }
        S.ui.templates[p] = tpl; saveUi(); toast(p + " bağlantısı güncellendi"); render(); break;
      }
      case "tpl-reset": S.ui.templates = clone(DEFAULT_TEMPLATES); saveUi(); render(); break;
      case "backup": { const r = await api.exportBackup(); toast(`Yedek hazır: ${r.listings} ilan`); break; }
      case "clear-cache": { const r = await api.clearCache(); toast(`Önbellek temizlendi (${Math.round((r.freed || 0) / 1024)} KB)`); break; }
      case "ask": S.confirm = el.dataset.v; render(); break;
      case "history-yes": { const r = await api.clearHistory(); S.confirm = null; toast(`${r.deleted} ilan silindi`); S.home = null; render(); break; }
      case "reset-yes": { const r = await api.resetConfig(); S.cfg = r.config; S.confirm = null; toast("Varsayılan ayarlar yüklendi"); render(); break; }
      case "cat-save": {
        const changes = S.cfg.categories.map((c) => ({ id: c.id, name: $("#cn_" + CSS.escape(c.id)).value.trim() || c.name,
          maxPrice: parseNum($("#cx_" + CSS.escape(c.id)).value), minPrice: parseNum($("#cm_" + CSS.escape(c.id)).value) }));
        const ok = await saveConfig((cfg) => {
          cfg.categories = changes.map((x) => { const o = { id: x.id, name: x.name }; if (x.maxPrice != null) o.maxPrice = x.maxPrice; if (x.minPrice != null) o.minPrice = x.minPrice; return o; });
        }, "Sınırlar kaydedildi; ilanlar yeniden değerlendirildi");
        if (ok) { S.home = null; render(); }
        break;
      }
      case "cat-add": {
        const name = $("#nc_name").value.trim(), max = parseNum($("#nc_max").value);
        if (!name) { toast("Kategori adı girin"); return; }
        const ok = await saveConfig((cfg) => { const o = { id: slug(name), name }; if (max != null) o.maxPrice = max; cfg.categories.push(o); }, "Kategori eklendi");
        if (ok) render();
        break;
      }
      case "cat-del": { const ok = await saveConfig((cfg) => { cfg.categories = cfg.categories.filter((c) => c.id !== id); }, "Kategori silindi"); if (ok) render(); break; }
      case "open-app": { const r = await api.openPlatform({ platform: el.dataset.p }); if (!r.ok) toast("Uygulama açılamadı"); break; }
      case "copy-diag": {
        const [st, pl, raw] = await Promise.all([api.status(), api.platforms(), api.rawLog({ limit: 50 })]);
        const txt = ["VintageHunter tanı raporu " + new Date().toLocaleString("tr-TR"), JSON.stringify(st),
          ...pl.items.map((p) => JSON.stringify(p)), "--- son bildirimler ---", ...(raw.items || []).map((r) => `${when(r.at)} | ${r.platform} | ${r.result} | ${r.title} — ${r.text}`)].join("\n");
        try { await navigator.clipboard.writeText(txt); toast("Tanı raporu kopyalandı"); } catch (err) { toast("Kopyalanamadı"); }
        break;
      }
      case "clear-raw": await api.clearRawLog(); render(); break;
      case "share-manual": S.shareDraft = { text: "", subject: "" }; go("share"); break;
      case "share-save": {
        const url = el.dataset.url || ($("#sh_url") ? $("#sh_url").value.trim() : "");
        const r = await api.addShared({ url, title: $("#sh_t").value.trim(), platform: $("#sh_p").value, price: parseNum($("#sh_price").value), note: $("#sh_note").value.trim() });
        S.shareDraft = null;
        toast(r.existing ? "Bu ilan zaten kayıtlı" : "İlan kaydedildi");
        location.replace("#listing/" + r.id); break;
      }
      default: break;
    }
  } catch (err) {
    toast(err && err.message ? err.message : "İşlem tamamlanamadı");
  }
});
function rerenderSearch() { const id = S.editing && S.editing.id; VIEWS.search.html(search(id) ? id : "new").then((h) => { $("#main").innerHTML = `<div class="view">${h}</div>`; }); }

document.addEventListener("keydown", (e) => {
  if (e.key === "Enter" && e.target.dataset && e.target.dataset.kw) { e.preventDefault(); readSearchForm(); rerenderSearch(); }
  if (e.key === "Enter" && e.target.classList.contains("pcard")) { e.target.click(); }
});
let qT;
document.addEventListener("input", (e) => {
  if (e.target.id === "dq") {
    clearTimeout(qT);
    qT = setTimeout(() => { S.ui.filter.q = e.target.value.trim(); saveUi(); loadDeals(true); }, 300);
  }
  if (e.target.id && /^p_(buy|sell|rate|ship|other)$/.test(e.target.id)) {
    const fees = { rate: parseNum($("#p_rate").value) || 0, ship: parseNum($("#p_ship").value) || 0, other: parseNum($("#p_other").value) || 0 };
    $("#p_res").innerHTML = profitHtml(profitOf(parseNum($("#p_buy").value), parseNum($("#p_sell").value), fees));
  }
});
document.addEventListener("change", async (e) => {
  const t = e.target, id = t.id || "";
  try {
    if (id === "dsort") { S.ui.sort = t.value; saveUi(); loadDeals(true); return; }
    if (t.dataset.plat) {
      const p = t.dataset.plat, on = t.checked;
      await saveConfig((cfg) => { cfg.settings.platformEnabled[p] = on; }, `${p} ${on ? "açıldı" : "kapatıldı"}`);
      return;
    }
    const map = { n_inst: "notifyInstant", n_high: "notifyPriorityHigh", n_drop: "notifyPriceDrop" };
    if (map[id]) { const on = t.checked; await saveConfig((cfg) => { cfg.settings[map[id]] = on; }, "Kaydedildi"); return; }
    if (id === "n_dig") { const v = +t.value; await saveConfig((cfg) => { cfg.settings.digestMinutes = v; }, "Özet: " + (v ? v + " dk" : "kapalı")); return; }
    if (["n_ds", "n_de", "n_qs", "n_qe"].includes(id)) {
      const val = (x) => { const n = parseInt($(x).value, 10); return isFinite(n) && n >= 0 && n <= 23 ? n : -1; };
      const ds = val("#n_ds"), de = val("#n_de"), qs = val("#n_qs"), qe = val("#n_qe");
      if (ds < 0 || de < 0 || ds > de) { toast("Özet saatleri 0–23 arası ve başlangıç ≤ bitiş olmalı"); return; }
      await saveConfig((cfg) => { Object.assign(cfg.settings, { digestStartHour: ds, digestEndHour: de, quietStart: qs, quietEnd: qe }); }, "Kaydedildi");
      return;
    }
    if (["t_vh", "t_h", "t_m"].includes(id)) {
      const vh = parseNum($("#t_vh").value), h = parseNum($("#t_h").value), m = parseNum($("#t_m").value);
      await saveConfig((cfg) => { Object.assign(cfg.settings, { tierVeryHigh: vh, tierHigh: h, tierMedium: m }); }, "Eşikler kaydedildi");
      return;
    }
    if (id === "g_ex") { const v = t.value.split(",").map((x) => x.trim()).filter(Boolean); await saveConfig((cfg) => { cfg.settings.globalExclude = v; }, "Kaydedildi"); return; }
    if (id === "s_cat") { readSearchForm(); rerenderSearch(); return; }
    if (id === "restore" && t.files && t.files[0]) {
      const json = await t.files[0].text();
      const r = await api.importBackup({ json });
      S.cfg = (await api.getConfig()).config;
      await loadUi();
      toast(`Yedek yüklendi: ${r.listings} ilan`);
      render();
    }
  } catch (err) { toast(err && err.message ? err.message : "Kaydedilemedi"); }
});
window.addEventListener("hashchange", () => { closeSheet(); window.scrollTo(0, 0); render(); });
window.addEventListener("scroll", () => { $("#topbar").classList.toggle("scrolled", window.scrollY > 4); }, { passive: true });
window.addEventListener("online", () => { S.online = true; renderShell(); });
window.addEventListener("offline", () => { S.online = false; renderShell(); });

/* ================================================================ yenileme ve yönlendirme */
async function takePending() {
  try {
    const r = await api.takePending();
    if (r.share) S.shareDraft = r.share;
    if (r.route) { if (route().name + (route().arg ? "/" + route().arg : "") === r.route) render(); else location.hash = r.route; return true; }
  } catch (e) { /* */ }
  return false;
}
async function refreshData(force) {
  const n = route().name;
  if (["home", "deals", "status", "history", "collection"].includes(n) || force) {
    if (document.querySelector(".sheet")) return;
    const y = window.scrollY;
    if (n === "deals" && !force) { const len = S.deals.items.length; await loadDeals(true); if (S.deals.items.length < len) await loadDeals(false); }
    else await render();
    window.scrollTo(0, y);
  } else {
    try { S.status = await api.status(); renderShell(); } catch (e) { /* */ }
  }
}
let pollT;
function startPolling() {
  clearInterval(pollT);
  pollT = setInterval(() => { if (document.visibilityState === "visible") refreshData(false); }, 20000);
}
document.addEventListener("visibilitychange", async () => {
  if (document.visibilityState === "visible") { if (!(await takePending())) refreshData(false); }
});

async function boot() {
  if (!api) {
    $("#main").innerHTML = `<div class="view"><div class="empty">${icon("info")}<h3>Android uygulaması gerekli</h3><p>VintageHunter, telefonunuzdaki platform bildirimlerini okuyarak çalışır. Bu sayfa yalnızca Android uygulamasının içinde çalışır.</p></div></div>`;
    renderShell();
    return;
  }
  await loadUi();
  S.cfg = (await api.getConfig()).config;
  try { api.addListener("pending", () => takePending()); } catch (e) { /* */ }
  const routed = await takePending();
  if (!routed) await render();
  startPolling();
  window.VHApp.ready = true;
}

/* Uçtan uca testler için (emülatörde CDP üzerinden). Uygulamanın kendi fonksiyonlarını kullanır. */
window.VHApp = {
  ready: false, S, api, go, render,
  test: {
    async setCategoryMax(id, max) { return saveConfig((cfg) => { const c = cfg.categories.find((x) => x.id === id); c.maxPrice = max; }); },
    async saveSearch(o) { return saveSearchObj({ id: "new", exclude: [], platforms: [], notify: "INSTANT", active: true, minPrice: null, maxPrice: null, minDealPct: null, marketValue: null, ...o }); },
    async setSearchActive(id, on) { return saveConfig((cfg) => { const s = cfg.searches.find((x) => x.id === id); if (s) s.active = on; }); },
    openFilter() { openFilter(); },
    async applyFilter(f, inLimitOnly) { S.ui.filter = { ...clone(DEFAULT_FILTER), ...f }; if (inLimitOnly !== undefined) S.ui.inLimitOnly = inLimitOnly; saveUi(); go("deals"); await new Promise((r) => setTimeout(r, 600)); return S.deals; },
    cardPrices() { return $$("#dlist .pcard .price").map((p) => parseNum(p.childNodes[0] ? p.childNodes[0].textContent : "")); },
  },
};
boot().catch((e) => { $("#main").innerHTML = `<div class="view"><div class="empty"><h3>Uygulama başlatılamadı</h3><p>${esc(e && e.message ? e.message : e)}</p></div></div>`; });
