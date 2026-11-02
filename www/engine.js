/* VintageHunter AI — istemci tarafı hesap motoru.
 * apps/api/app/domain/*.py ile birebir aynı formüller (kaynak doğruluk: Python).
 * Parite testi: scripts/engine_parity.test.js
 */
const VH = (() => {
  const r2 = (x) => (x == null || !isFinite(x) ? null : Number(Math.round(Number(x + "e2")) + "e-2"));
  const r4 = (x) => (x == null || !isFinite(x) ? null : Number(Math.round(Number(x + "e4")) + "e-4"));
  const num = (v) => {
    if (v === null || v === undefined || v === "") return 0;
    const n = Number(String(v).replace(/\./g, (m, i, s) => (s.indexOf(",") > -1 ? "" : m)).replace(",", "."));
    if (!isFinite(n)) throw new Error("Geçersiz sayı: " + v);
    return n;
  };

  function priceAdvantagePct(ref, ask) {
    ref = num(ref); ask = num(ask);
    if (ref <= 0) return null;
    return ((ref - ask) / ref) * 100;
  }

  const ACQ = ["inbound_shipping", "repair", "cleaning", "import_duties", "other_acquisition"];
  const SELL = ["fixed_fee", "payment_fee", "outbound_shipping", "packaging", "promotion", "refunds", "other_selling"];

  function computeProfit({ purchase, sale, rate = 0, acquisition = {}, selling = {} }) {
    purchase = num(purchase); sale = num(sale); rate = num(rate);
    if (rate < 0 || rate > 100) throw new Error("Komisyon oranı %0 ile %100 arasında olmalı");
    if (purchase < 0 || sale < 0) throw new Error("Fiyatlar negatif olamaz");
    let nonpurchase = 0, other = 0;
    for (const k of ACQ) { const v = num(acquisition[k]); if (v < 0) throw new Error("Gider negatif olamaz"); nonpurchase += v; }
    for (const k of SELL) { const v = num(selling[k]); if (v < 0) throw new Error("Gider negatif olamaz"); other += v; }
    const landed = purchase + nonpurchase;
    const commission = (sale * rate) / 100;
    const net = sale - commission - other;
    const profit = net - landed;
    return {
      landed, nonpurchase, commission, otherSelling: other, net, profit,
      roi: landed > 0 ? (profit / landed) * 100 : null,
      margin: sale > 0 ? (profit / sale) * 100 : null,
      breakeven: rate < 100 ? (landed + other) / (1 - rate / 100) : null,
    };
  }

  function maxOffer(inp, targetProfit = 0, targetRoi = null) {
    const p = computeProfit(inp);
    const byProfit = p.net - p.nonpurchase - num(targetProfit);
    let byRoi = null;
    if (targetRoi !== null && targetRoi !== "") byRoi = p.net / (1 + num(targetRoi) / 100) - p.nonpurchase;
    const best = Math.min(byProfit, byRoi === null ? Infinity : byRoi);
    return { byProfit, byRoi, maxOffer: Math.max(0, best), feasible: best > 0 };
  }

  /* ---------- Değerleme (H) ---------- */
  function median(a) {
    const s = [...a].sort((x, y) => x - y), n = s.length;
    return n % 2 ? s[(n - 1) / 2] : (s[n / 2 - 1] + s[n / 2]) / 2;
  }

  function estimateReference(comps, manualRef, today = new Date()) {
    const warnings = [], rejected = [], excluded = [], sold = [], asks = [];
    let dateMissing = false, stale = false, condDiff = false, boxDiff = false;
    comps.forEach((c, i) => {
      const id = c.id || "r" + (i + 1);
      if ((c.currency || "TRY") !== "TRY") return excluded.push({ id, reason: `Kur gerekli: ${c.currency}→TRY doğrulanmadı` });
      if (c.edition_mismatch) return rejected.push({ id, reason: "Edition/varyant uyuşmazlığı" });
      if (c.bootleg) return rejected.push({ id, reason: "Orijinal ve bootleg fiyatları birleştirilmez" });
      if (c.sale_type === "SOLD_CONFIRMED") {
        sold.push(num(c.price));
        if (!c.date) dateMissing = true;
        else if ((today - new Date(c.date)) / 864e5 > 365) stale = true;
        if (c.condition_differs) condDiff = true;
        if (c.box_differs) boxDiff = true;
      } else if (c.sale_type === "ACTIVE_ASK") asks.push(num(c.price));
      else excluded.push({ id, reason: `${c.sale_type} değerleme havuzuna alınmaz` });
    });
    if (excluded.some((e) => e.reason.startsWith("Kur"))) warnings.push("Bazı referanslar farklı para biriminde; kur verisi olmadan hesaplanmadı");
    const askCtx = asks.length ? { medianAsk: median(asks), n: asks.length } : null;
    const n = sold.length;
    if (n === 0) {
      warnings.push("Doğrulanmış satış verisi yok — kesin piyasa değeri hesaplanamaz");
      if (manualRef && num(manualRef) > 0) {
        warnings.push("Piyasa referansı doğrulanmış satışlara dayanmıyor (kullanıcı tahmini)");
        return { status: "PARTIAL", ref: num(manualRef), basis: "USER_ESTIMATE", confidence: "LOW", nSold: 0, nAsk: asks.length, askCtx, rejected, excluded, outliers: [], warnings, range: null };
      }
      return { status: "NO_EVIDENCE", ref: null, basis: null, confidence: "NONE", nSold: 0, nAsk: asks.length, askCtx, rejected, excluded, outliers: [], warnings, range: null };
    }
    const ref = median(sold);
    let outliers = [];
    if (n >= 4) {
      const s = [...sold].sort((a, b) => a - b), h = Math.floor(n / 2);
      const q1 = median(s.slice(0, h)), q3 = median(s.slice(h + (n % 2)));
      const iqr = q3 - q1;
      outliers = sold.filter((p) => p < q1 - 1.5 * iqr || p > q3 + 1.5 * iqr);
    }
    let confidence = n < 3 ? "LOW" : n < 6 ? "MEDIUM" : "HIGH";
    if (n < 3) warnings.push(`Yalnızca ${n} doğrulanmış satış — düşük güven`);
    if (dateMissing) warnings.push("Bazı satışların tarihi yok");
    if (stale) warnings.push("Bazı satışlar 1 yıldan eski — güncel TL fiyatı gibi kullanılmamalı");
    if (condDiff) warnings.push("Kondisyonu farklı satışlar var; düzeltme oranı uydurulmadı");
    if (boxDiff) warnings.push("Kutulu/kutusuz farkı var; fiyat etkisi kullanıcı onayı ister");
    if ((dateMissing || stale) && confidence !== "LOW") confidence = confidence === "MEDIUM" ? "LOW" : "MEDIUM";
    return { status: "CALCULATED", ref, basis: "SOLD_MEDIAN", confidence, nSold: n, nAsk: asks.length, askCtx, rejected, excluded, outliers, warnings, range: [Math.min(...sold), Math.max(...sold)] };
  }

  /* ---------- Fırsat puanı (J) ---------- */
  const COND = { NEW: 10, USED_GOOD: 8, USED_FAIR: 5, FOR_PARTS: 1 };
  const RISK = { LOW: 10, MEDIUM: 6, HIGH: 2, CRITICAL: 0 };
  const STATUS_TR = { DEAL: "Fırsat", REVIEW_REQUIRED: "İnceleme gerekli", NOT_A_DEAL: "Fırsat değil", INSUFFICIENT_EVIDENCE: "Yetersiz kanıt" };
  const lin = (v, full, max) => (v <= 0 ? 0 : Math.min(max, (v / full) * max));

  function assessDeal(i) {
    const checks = [], warnings = [];
    const check = (key, ok, text) => (checks.push({ key, passed: !!ok, text }), ok);
    const p = computeProfit({ purchase: i.asking, sale: i.sale, rate: i.rate, acquisition: i.acquisition, selling: i.selling });
    const adv = i.ref == null ? null : priceAdvantagePct(i.ref, i.asking);
    const c = {};
    c.advantage = { points: adv == null ? null : lin(adv, 60, 30), max: 30 };
    c.profit = { points: p.roi == null ? null : lin(p.roi, 100, 20), max: 20 };
    let ev = 0;
    if (i.basis === "SOLD_MEDIAN") ev = i.nSold >= 6 ? 20 : i.nSold >= 3 ? 15 : 8;
    else if (i.basis === "USER_ESTIMATE") ev = 4;
    else if (i.basis === "ACTIVE_ASK_ONLY") ev = 3;
    c.evidence = { points: ev, max: 20 };
    if (i.condition in COND) {
      let pts = COND[i.condition];
      if (i.functional === false) pts = Math.min(pts, 2);
      c.condition = { points: pts, max: 10 };
    } else c.condition = { points: null, max: 10 };
    c.risk = { points: i.risk in RISK ? RISK[i.risk] : null, max: 10 };
    const d = i.days;
    c.liquidity = { points: d == null || d === "" ? null : d <= 7 ? 10 : d <= 30 ? 7 : d <= 90 ? 4 : 1, max: 10 };
    let earned = 0, available = 0;
    const missing = [];
    for (const [k, v] of Object.entries(c)) { if (v.points == null) missing.push(k); else { earned += v.points; available += v.max; } }
    const score = available ? (earned / available) * 100 : 0;
    const hardBlock = i.risk === "CRITICAL" ? "Kritik risk (sahte/çalıntı/yasaklı şüphesi) — fırsat önerilmez" : null;
    let status;
    if (i.ref == null || adv == null) {
      status = "INSUFFICIENT_EVIDENCE";
      check("reference", false, "Geçerli referans fiyat yok; fiyat avantajı hesaplanamaz");
    } else {
      const minProfit = num(i.minProfit || 0), minDisc = num(i.minDiscount ?? 45);
      const okC = check("ceiling", num(i.asking) <= num(i.ceiling), `Alış fiyatı kategori tavanı ${fmtTL(i.ceiling)} içinde`);
      const okA = check("advantage", adv >= minDisc, `Fiyat avantajı ${fmtPct(adv)} ≥ eşik %${minDisc}`);
      const okP = check("profit", p.profit > 0 && p.profit >= minProfit, `Tahmini net kâr ${fmtTL(p.profit)} pozitif ve ≥ ${fmtTL(minProfit)}`);
      check("risk", !hardBlock, "Kritik risk yok");
      check("source", i.hasSource !== false, "Kaynak ve gözlem tarihi mevcut");
      if (hardBlock || !(okC && okA && okP)) status = "NOT_A_DEAL";
      else {
        const rv = [];
        if (i.basis !== "SOLD_MEDIAN") rv.push("Referans doğrulanmış satışlara dayanmıyor");
        else if (i.nSold < 3) rv.push("Doğrulanmış satış sayısı 3'ten az");
        if (i.risk === "HIGH" || !(i.risk in RISK)) rv.push("Orijinallik/risk değerlendirmesi yapılmadı veya yüksek");
        if (!(i.condition in COND)) rv.push("Kondisyon bilinmiyor");
        if (i.functional === false) rv.push("Çalışırlık doğrulanmadı / arızalı — onarım tahmini gerekli");
        if (i.hasSource === false) rv.push("Kaynak/tarih eksik");
        status = rv.length ? "REVIEW_REQUIRED" : "DEAL";
        warnings.push(...rv);
      }
    }
    if (i.basis === "USER_ESTIMATE") warnings.push("Referans kullanıcı tahmini — satışla doğrulanmadı");
    if (i.dataClass === "DEMO") warnings.push("DEMO verisi — gerçek fırsat değildir");
    if (hardBlock) warnings.unshift(hardBlock);
    return {
      status, statusLabel: STATUS_TR[status], score: Math.round(score * 10) / 10, completeness: available,
      missing, components: c, adv, profit: p,
      maxOffer: maxOffer({ purchase: i.asking, sale: i.sale, rate: i.rate, acquisition: i.acquisition, selling: i.selling }, i.minProfit || 0),
      checks, warnings, hardBlock,
      notificationAllowed: status === "DEAL" && i.dataClass !== "DEMO" && !hardBlock,
    };
  }

  /* ---------- Başlık ayrıştırma (G1) ---------- */
  const BRANDS = { NECA: ["neca"], Funko: ["funko"], Hasbro: ["hasbro"], Mattel: ["mattel"], Playmates: ["playmates"], Nintendo: ["nintendo", "game boy", "gameboy"], Sony: ["sony", "ps1", "ps2", "playstation"], Sega: ["sega"], Nike: ["nike"], Adidas: ["adidas"], Converse: ["converse"], Vans: ["vans"], Nikon: ["nikon"] };
  const CATS = { toys_figures: ["figür", "figur", "action figure", "funko", "neca", "tmnt", "ninja turtles", "oyuncak"], retro_gaming: ["ps1", "ps2", "playstation", "nintendo", "game boy", "gameboy", "sega", "konsol"], fashion: ["nike", "adidas", "converse", "vans", "sweatshirt", "forma", "ayakkabı", "ceket"], antiques: ["antika", "porselen", "eski saat", "radyo"], electronics: ["nikon", "lens", "kamera", "kaset", "walkman"] };
  function parseTitle(title) {
    const t = title.replace(/İ/g, "i").replace(/I/g, "ı").toLocaleLowerCase("tr");
    const has = (a) => new RegExp("(^|[^a-zçğıöşü0-9])" + a.replace(/ /g, "\\s") + "($|[^a-zçğıöşü0-9])").test(t);
    const brand = Object.keys(BRANDS).find((b) => BRANDS[b].some(has)) || null;
    const y = t.match(/\b(19[5-9]\d|20[0-2]\d)\b/);
    const category = Object.keys(CATS).find((k) => CATS[k].some((h) => t.includes(h))) || null;
    const claims = {};
    if (t.includes("kutusuz")) claims.boxed = false; else if (t.includes("kutulu")) claims.boxed = true;
    if (/çalışmıyor|arızalı|parça için/.test(t)) claims.functional = false; else if (/çalışıyor|test edildi/.test(t)) claims.functional = true;
    const r = t.match(/\b(pal|ntsc|jp)\b/);
    return { brand, year: y ? +y[1] : null, category, region: r ? r[1].toUpperCase() : null, claims };
  }

  /* ---------- Biçimlendirme (tr-TR) ---------- */
  const tl = new Intl.NumberFormat("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  function fmtTL(v) { return v == null || !isFinite(v) ? "—" : tl.format(r2(num(v))) + " TL"; }
  function fmtPct(v, d = 1) { return v == null || !isFinite(v) ? "N/A" : "%" + new Intl.NumberFormat("tr-TR", { maximumFractionDigits: d, minimumFractionDigits: d }).format(v); }

  /* ---------- CSV (O3-32, O4-42) — csv_io.py aynası ---------- */
  function parseTrNumber(v) {
    v = String(v ?? "").replace(/TL|₺|\s/g, "");
    if (!v) return null;
    if (v.includes(",")) v = v.replace(/\./g, "").replace(",", ".");
    const n = Number(v);
    if (!isFinite(n)) throw new Error(`sayı okunamadı '${v}'`);
    if (n < 0) throw new Error("negatif tutar");
    return n;
  }
  function parseDate(v) {
    v = String(v ?? "").trim();
    if (!v) return null;
    let y, m, d;
    if (v.includes(".")) [d, m, y] = v.split(".").map(Number); else [y, m, d] = v.split("-").map(Number);
    const dt = new Date(Date.UTC(y, m - 1, d));
    if (isNaN(dt) || dt.getUTCDate() !== d) throw new Error(`tarih okunamadı '${v}'`);
    return dt.toISOString().slice(0, 10);
  }
  function parseCSV(text) {
    text = text.replace(/^﻿/, "");
    const first = text.split(/\r?\n/)[0] || "";
    const delim = (first.match(/;/g) || []).length > (first.match(/,/g) || []).length ? ";" : ",";
    const rows = []; let row = [], cell = "", q = false;
    for (let i = 0; i < text.length; i++) {
      const c = text[i];
      if (q) { if (c === '"') { if (text[i + 1] === '"') { cell += '"'; i++; } else q = false; } else cell += c; }
      else if (c === '"') q = true;
      else if (c === delim) { row.push(cell); cell = ""; }
      else if (c === "\n" || c === "\r") { if (c === "\r" && text[i + 1] === "\n") i++; row.push(cell); rows.push(row); row = []; cell = ""; }
      else cell += c;
    }
    if (cell !== "" || row.length) { row.push(cell); rows.push(row); }
    return rows.filter((r) => r.some((x) => x.trim() !== ""));
  }
  const CAT_KEYS = ["toys_figures", "retro_gaming", "fashion", "antiques", "electronics"];
  const CAT_TR = { oyuncak: "toys_figures", "figür": "toys_figures", retro: "retro_gaming", konsol: "retro_gaming", giyim: "fashion", "ayakkabı": "fashion", antika: "antiques", elektronik: "electronics" };
  const STATUS_IN = { stokta: "IN_STOCK", ilanda: "LISTED", "satışta": "LISTED", rezerve: "RESERVED", "satıldı": "SOLD" };
  function importInventoryCSV(text) {
    const rows = parseCSV(text);
    if (!rows.length) return { ok: [], errors: [{ row: 1, reason: "Dosya boş" }] };
    const head = rows[0].map((h) => h.trim().toLocaleLowerCase("tr"));
    if (!head.includes("baslik")) return { ok: [], errors: [{ row: 1, reason: "Eksik sütun: baslik" }] };
    const ok = [], errors = [];
    rows.slice(1).forEach((cells, idx) => {
      const r = Object.fromEntries(head.map((h, j) => [h, (cells[j] ?? "").trim()]));
      try {
        if (!r.baslik) throw new Error("başlık boş");
        const ck = (r.kategori || "").toLocaleLowerCase("tr");
        const category = !ck ? "toys_figures" : CAT_KEYS.includes(ck) ? ck : CAT_TR[ck];
        if (!category) throw new Error(`bilinmeyen kategori '${r.kategori}'`);
        const sk = (r.durum || "").toLocaleLowerCase("tr");
        let status = !sk ? "IN_STOCK" : STATUS_IN[sk];
        if (!status) throw new Error(`bilinmeyen durum '${r.durum}'`);
        const salePrice = parseTrNumber(r.satis_fiyati);
        const item = { title: r.baslik.slice(0, 200), category, status: salePrice != null ? "SOLD" : status, cost: parseTrNumber(r.alis_maliyeti),
          acquiredAt: parseDate(r.alis_tarihi), channel: { platform: r.platform || null, url: r.ilan_linki || null, price: parseTrNumber(r.ilan_fiyati) }, notes: r.notlar || "", sale: null };
        if (salePrice != null) item.sale = { price: salePrice, date: parseDate(r.satis_tarihi), channel: r.satis_kanali || r.platform || "manuel", commission: parseTrNumber(r.komisyon) || 0, shipping: parseTrNumber(r.kargo) || 0 };
        ok.push(item);
      } catch (e) { errors.push({ row: idx + 2, reason: e.message }); }
    });
    return { ok, errors };
  }
  function safeCell(v) {
    if (v == null) return "";
    if (typeof v === "number") return String(v);
    let s = String(v);
    if (/^[=+\-@\t\r]/.test(s)) s = "'" + s;
    return /[;"\n\r]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
  }
  function exportCSV(rows, columns) {
    return "﻿" + [columns.join(";"), ...rows.map((r) => columns.map((c) => safeCell(r[c])).join(";"))].join("\r\n");
  }

  /* ---------- İlan taslağı (F3/L12) — drafts.py aynası ---------- */
  const COND_TXT = { NEW: "Yeni / kullanılmamış", USED_GOOD: "İkinci el, iyi durumda", USED_FAIR: "İkinci el, kullanım izleri var", FOR_PARTS: "Arızalı / parça için", UNKNOWN: "Durum belirtilmedi" };
  function makeTitle(parts, limit) {
    let out = "";
    for (const p of parts.map((x) => String(x || "").trim()).filter(Boolean)) { const c = (out + " " + p).trim(); if (c.length <= limit) out = c; }
    return out;
  }
  function buildDraft(item, platform, limit = 60) {
    if (item.risk === "CRITICAL") throw new Error("Kritik risk işaretli üründe satış taslağı önerilmez");
    const title = makeTitle([item.brand, item.title, item.size], limit) || String(item.title || "").slice(0, limit);
    const flaws = String(item.flaws || "").split(/\n|;/).map((s) => s.trim()).filter(Boolean);
    const L = [String(item.title || "").trim(), "", "Durum: " + COND_TXT[item.condition || "UNKNOWN"]];
    if (item.brand) L.push("Marka: " + item.brand);
    if (item.size) L.push("Beden / ölçü: " + item.size);
    if (item.included) L.push("Kutu / aksesuar: " + item.included);
    L.push("Kusurlar: " + (flaws.length ? flaws.join("; ") : "Belirtilen kusur yok — fotoğrafları inceleyiniz"));
    if (item.functional === true) L.push("Çalışırlık: test edildi, çalışıyor");
    else if (item.functional === false) L.push("Çalışırlık: arızalı / test edilmedi — bilgi amaçlıdır");
    if (item.extra) L.push("", String(item.extra).trim());
    const missing = [["brand", "marka"], ["size", "beden/ölçü"], ["photos", "fotoğraf"]].filter(([k]) => !item[k] || (Array.isArray(item[k]) && !item[k].length)).map(([, l]) => l);
    return { platform, title, titleLength: title.length, titleLimit: limit, description: L.join("\n").trim(), missing, publishStatus: "LOCAL_DRAFT" };
  }

  /* ---------- Stok ---------- */
  function ageDays(fromIso, now = new Date()) { return fromIso ? Math.max(0, Math.floor((now - new Date(fromIso)) / 864e5)) : null; }
  function agingBucket(days) { return days == null ? "?" : days <= 30 ? "0–30" : days <= 60 ? "31–60" : days <= 90 ? "61–90" : "90+"; }
  function realizedProfit(sale) {
    if (sale.costSnapshot == null) return null;
    return num(sale.salePrice) - num(sale.commission) - num(sale.shipping) - num(sale.discount) - num(sale.otherFees) - num(sale.costSnapshot) - num(sale.expensesSnapshot);
  }

  return { parseTrNumber, parseDate, parseCSV, importInventoryCSV, safeCell, exportCSV, buildDraft, ageDays, agingBucket, realizedProfit,
    r2, r4, num, priceAdvantagePct, computeProfit, maxOffer, estimateReference, assessDeal, parseTitle, fmtTL, fmtPct, STATUS_TR };
})();
if (typeof module !== "undefined") module.exports = VH;
