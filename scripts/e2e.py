#!/usr/bin/env python3
"""VintageHunter uçtan uca testleri — gerçek Android emülatöründe.

Gerçek akış: test uygulaması (com.vhtest.dolap, platform uygulaması gibi davranır) bildirim gönderir →
VintageHunter'ın bildirim dinleyicisi yakalar → SQLite'a yazar → fiyat kuralı → kendi bildirimi →
bildirime dokunma → ilan detayı → "Platformda aç" → test uygulamasının ilan sayfası.
Arayüz, Chrome DevTools Protocol ile uygulamanın kendi JavaScript fonksiyonları üzerinden okunur.
Kullanım: python3 e2e.py <çıktı_klasörü>
"""
import base64
import json
import os
import re
import shlex
import sqlite3
import subprocess
import sys
import time
import urllib.request

import websocket

APP = "com.ozzystore35.vintagehunter"
SIM = "com.vhtest.dolap"
OUT = sys.argv[1] if len(sys.argv) > 1 else "e2e-out"
os.makedirs(OUT + "/shots", exist_ok=True)
RESULTS = []


def adb(*args, timeout=90, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def sh(cmd, timeout=90):
    return adb("shell", cmd, timeout=timeout)


def record(name, ok, detail=""):
    RESULTS.append({"name": name, "ok": bool(ok), "detail": str(detail)[:3000]})
    print(("PASS  " if ok else "FAIL  ") + name + "  ::  " + str(detail)[:400], flush=True)


def shot(name):
    time.sleep(0.8)
    data = adb("exec-out", "screencap", "-p", binary=True, timeout=60)
    with open(f"{OUT}/shots/{name}.png", "wb") as f:
        f.write(data)


def b64(s):
    return base64.b64encode(s.encode("utf-8")).decode("ascii")


def post(nid, title, text, pic=False, lines=None):
    cmd = f"am broadcast --include-stopped-packages -n {SIM}/.Post --ei id {nid} --es t64 {b64(title)} --es x64 {b64(text)}"
    if pic:
        cmd += " --ez pic true"
    if lines:
        cmd += f" --es l64 {b64('|'.join(lines))}"
    sh(cmd)


class CDP:
    def __init__(self):
        self.ws = None
        self.n = 0
        self.connect()

    def connect(self, wait=60):
        end = time.time() + wait
        last = None
        while time.time() < end:
            try:
                pid = sh(f"pidof {APP}").strip().split()[0]
                adb("forward", "--remove-all")
                adb("forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}")
                pages = json.load(urllib.request.urlopen("http://127.0.0.1:9222/json/list", timeout=5))
                page = [p for p in pages if p.get("type") == "page"][0]
                self.ws = websocket.create_connection(page["webSocketDebuggerUrl"], timeout=60, suppress_origin=True)
                for _ in range(60):
                    if self.ev("!!(window.VHApp && VHApp.ready)"):
                        return
                    time.sleep(0.5)
            except Exception as e:  # noqa: BLE001
                last = e
            time.sleep(1.5)
        raise RuntimeError(f"WebView'e bağlanılamadı: {last}")

    def ev(self, expr, await_promise=True):
        self.n += 1
        self.ws.send(json.dumps({"id": self.n, "method": "Runtime.evaluate",
                                 "params": {"expression": expr, "awaitPromise": await_promise, "returnByValue": True}}))
        while True:
            msg = json.loads(self.ws.recv())
            if msg.get("id") == self.n:
                break
        res = msg.get("result", {})
        if "exceptionDetails" in res:
            raise RuntimeError(res["exceptionDetails"].get("exception", {}).get("description") or res["exceptionDetails"].get("text"))
        return res.get("result", {}).get("value")

    def js(self, expr):
        """Promise döndüren ifadeyi JSON olarak al."""
        return json.loads(self.ev(f"(async () => JSON.stringify(await ({expr})))()"))


def listings(cdp, **params):
    return cdp.js(f"VHApp.api.list({json.dumps({'offset': 0, 'limit': 500, **params})})")["items"]


def find(items, needle):
    return [x for x in items if needle.lower() in ((x.get("title") or "") + " " + (x.get("text") or "")).lower()]


def notifs(cdp):
    return cdp.js("VHApp.api.notifLog({limit: 300})")["items"]


def our_notifications():
    return sh("dumpsys notification --noredact", timeout=60)


def db_rows(sql):
    """Uygulama arka plandayken veritabanını doğrudan okur (WAL dosyalarıyla birlikte)."""
    tmp = f"{OUT}/db"
    os.makedirs(tmp, exist_ok=True)
    for suf in ["", "-wal", "-shm"]:
        data = adb("exec-out", "run-as", APP, "cat", f"databases/vh.db{suf}", binary=True)
        p = f"{tmp}/vh.db{suf}"
        if data and not data.startswith(b"cat:"):
            open(p, "wb").write(data)
        elif os.path.exists(p):
            os.remove(p)
    con = sqlite3.connect(f"{tmp}/vh.db")
    con.row_factory = sqlite3.Row
    rows = [dict(r) for r in con.execute(sql)]
    con.close()
    return rows


def tap_notification(substr, tries=3):
    for _ in range(tries):
        sh("cmd statusbar expand-notifications")
        time.sleep(2.5)
        sh("uiautomator dump /sdcard/ui.xml")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
            text = m.group(1).replace("&amp;", "&")
            if substr in text:
                x = (int(m.group(2)) + int(m.group(4))) // 2
                y = (int(m.group(3)) + int(m.group(5))) // 2
                shot("02-bildirim-cubugu")
                sh(f"input tap {x} {y}")
                return text
        sh("cmd statusbar collapse")
        time.sleep(2)
    return None


def top_activity():
    out = sh("dumpsys activity activities")
    m = re.search(r"topResumedActivity=.*?\{[^}]*? ([\w.]+/[\w.$]+)", out) or re.search(r"mResumedActivity: .*? ([\w.]+/[\w.$]+)", out)
    return m.group(1) if m else out[:300]


def bring_app():
    sh(f"am start -n {APP}/.MainActivity")
    time.sleep(3)


def wait_for(fn, timeout=12, step=1.0):
    end = time.time() + timeout
    val = None
    while time.time() < end:
        val = fn()
        if val:
            return val
        time.sleep(step)
    return val


def main():
    cdp = CDP()
    st = wait_for(lambda: (lambda s: s if s["listenerConnected"] else None)(cdp.js("VHApp.api.status()")), timeout=40)
    st = st or cdp.js("VHApp.api.status()")
    record("Kurulum: bildirim erişimi ve dinleyici bağlı", st["listenerEnabled"] and st["listenerConnected"], st)

    # ---------------------------------------------------------------- 0) gerçek veri: sahte ilan yok
    cdp.ev("VHApp.go('home')")
    time.sleep(2)
    main_text = cdp.ev("document.querySelector('#main').innerText")
    cards = cdp.ev("document.querySelectorAll('.pcard').length")
    record("Test 3a — İlk açılışta sahte/örnek ilan gösterilmiyor", cards == 0 and "Henüz ilan yakalanmadı" in main_text, f"kart={cards}")
    shot("01-ilk-acilis")

    # ---------------------------------------------------------------- 1) bildirim → kayıt → bildirim
    post(1, "Kayıtlı aramanda yeni ilan", "Levi's 501 vintage kot 180 TL", pic=True)
    lev = wait_for(lambda: find(listings(cdp), "Levi's 501 vintage kot"), timeout=15)
    ok = bool(lev) and len(lev) == 1 and lev[0]["price"] == 180 and lev[0]["in_limit"] == 1 and lev[0]["platform"] == "Dolap"
    record("Bildirim yakalandı ve veritabanına yazıldı (180 TL, Dolap, uygun)", ok, lev)
    lev_id = lev[0]["id"] if lev else None
    record("Bildirimdeki ürün fotoğrafı kaydedildi", bool(lev) and bool(lev[0].get("photo")), (lev[0].get("photo") if lev else None))
    dn = our_notifications()
    record("Uygulama kendi 'Uygun ilan' bildirimini gönderdi", "Uygun ilan · 180 TL" in dn and APP in dn,
           re.findall(r"android.title=String \(([^)]*)\)", dn)[:6])
    nl = notifs(cdp)
    record("Bildirim geçmişi kayıtla eşleşiyor (aynı ilan kimliği)", any(n.get("listing_id") == lev_id and n["kind"] == "MATCH" for n in nl), nl[:3])

    # ---------------------------------------------------------------- Test 2: bildirime dokun → ilan detayı
    sh("input keyevent KEYCODE_HOME")
    time.sleep(1.5)
    tapped = tap_notification("Uygun ilan · 180")
    time.sleep(4)
    try:
        cdp.connect(wait=30)
    except Exception:  # noqa: BLE001
        pass
    h = cdp.ev("location.hash")
    title = cdp.ev("(document.querySelector('.dtitle')||{}).textContent || ''")
    record("Test 2 — Bildirime dokununca ilgili ilanın detayı açılıyor", tapped is not None and h == f"#listing/{lev_id}" and "Levi" in title,
           f"dokunulan='{tapped}' hash={h} başlık={title}")
    shot("03-detay-bildirimden")

    # ---------------------------------------------------------------- Test 3: platformda aç → gerçek ilan sayfası
    cdp.ev("document.querySelector('[data-act=open-platform]').click()")
    time.sleep(4)
    top = top_activity()
    sh("uiautomator dump /sdcard/ui2.xml")
    ui2 = adb("exec-out", "cat", "/sdcard/ui2.xml")
    record("Test 3 — 'Platformda aç' platformun ilan sayfasını açıyor", f"{SIM}/.ListingActivity" in top and "Levi&apos;s 501 vintage kot" in ui2.replace("'", "&apos;"),
           top)
    shot("04-platform-ilan-sayfasi")
    bring_app()
    cdp.connect(wait=30)

    # ---------------------------------------------------------------- Test 1: fiyat filtresi (figür 1.500)
    cdp.js("VHApp.test.setCategoryMax('figur', 1500)")
    tests = [(2, "He-Man figür 1.501 TL"), (3, "He-Man Battle Cat figür 1.500 TL"), (4, "Skeletor figür 2.000 TL"),
             (5, "She-Ra figür 3.000 TL"), (6, "Masters of the Universe Orko 5.000 TL"), (7, "He-Man Man-At-Arms 1.250,50 TL")]
    for nid, t in tests:
        post(nid, "Yeni ilan", t)
        time.sleep(0.6)
    wait_for(lambda: len(listings(cdp, categories=["figur"])) >= 6, timeout=15)
    figs = listings(cdp, categories=["figur"])
    inl = sorted(x["price"] for x in figs if x["in_limit"] == 1)
    over = sorted(x["price"] for x in figs if x["in_limit"] == 0 and x.get("price"))
    record("Test 1 — Figür sınırı 1.500: 1.500 ve 1.250,50 uygun; 1.501/2.000/3.000/5.000 uygun değil",
           inl == [1250.5, 1500.0] and over == [1501.0, 2000.0, 3000.0, 5000.0], f"uygun={inl} sınır_üstü={over}")
    fig_ids = {x["id"]: x["price"] for x in figs}
    nl = notifs(cdp)
    notified = sorted(fig_ids[n["listing_id"]] for n in nl if n.get("listing_id") in fig_ids and n["kind"] in ("MATCH", "HIGH_DEAL"))
    record("Test 1 — Bildirimler de aynı sınırı kullanıyor (yalnız 1.500 ve 1.250,50 bildirildi)", notified == [1250.5, 1500.0], notified)
    cdp.js("VHApp.test.applyFilter({categories: ['figur']}, true)")
    time.sleep(1.5)
    prices = cdp.js("Promise.resolve(VHApp.test.cardPrices())")
    record("Test 1 — Arayüzdeki 'Uygun' listede 1.500 TL üstü yok", prices and max(prices) <= 1500 and 1500 in prices, prices)
    shot("05-firsatlar-figur-uygun")

    # ---------------------------------------------------------------- Test 5: kategori bağımsızlığı
    cdp.js("VHApp.test.setCategoryMax('konsol', 3000)")
    post(8, "Yeni ilan", "Super Nintendo SNES konsol 2.800 TL")
    post(9, "Yeni ilan", "He-Man figür kutulu 2.800 TL")
    time.sleep(4)
    snes = find(listings(cdp), "SNES konsol")
    hm = find(listings(cdp), "He-Man figür kutulu 2.800")
    record("Test 5 — Konsol 3.000 / figür 1.500: SNES 2.800 uygun, He-Man 2.800 uygun değil",
           snes and snes[0]["in_limit"] == 1 and snes[0]["category_id"] == "konsol" and hm and hm[0]["in_limit"] == 0,
           {"snes": snes[:1], "heman": hm[:1]})

    # ---------------------------------------------------------------- Test 7: hatalı / eksik fiyat
    bad = [(10, "Yeni ilan", "He-Man figür kutulu orijinal"), (11, "Yeni ilan", "Skeletor figür $40"),
           (12, "Kayıtlı aramanda", "He-Man için 3 yeni ilan, 100 TL'den başlayan fiyatlarla"),
           (13, "Yeni ilan", "He-Man 100 TL veya Skeletor 120 TL"), (14, "Yeni ilan", "Levi's 505 kot 1.200 TL · Kargo 39,99 TL")]
    before = {n["id"] for n in notifs(cdp)}
    for nid, t, x in bad:
        post(nid, t, x)
        time.sleep(0.6)
    time.sleep(4)
    allx = listings(cdp)
    got = {x: find(allx, x[:24]) for _, _, x in bad}
    new_n = [n for n in notifs(cdp) if n["id"] not in before and n["kind"] in ("MATCH", "HIGH_DEAL")]
    statuses = {k[:30]: (v[0]["price_status"], v[0].get("price"), v[0]["in_limit"]) if v else None for k, v in got.items()}
    ok7 = all(v and v[2] == 0 for v in statuses.values()) and not new_n
    lev1200 = got[bad[4][2]]
    ok7 = ok7 and lev1200 and lev1200[0]["price"] == 1200
    record("Test 7 — Fiyatsız, $, toplu, belirsiz ve kargolu bildirimler fırsat sayılmadı (kargo yok sayıldı: 1.200 TL)", ok7,
           {"durumlar": statuses, "yeni_bildirim": new_n})

    # ---------------------------------------------------------------- tekrar ve fiyat düşüşü
    before_n = len([n for n in notifs(cdp) if n.get("listing_id") == lev_id])
    post(15, "Kayıtlı aramanda yeni ilan", "Levi's 501 vintage kot 180 TL")
    time.sleep(4)
    dup = find(listings(cdp), "Levi's 501 vintage kot")
    after_n = len([n for n in notifs(cdp) if n.get("listing_id") == lev_id])
    record("Aynı ilan tekrar gelince ikinci kayıt ve ikinci bildirim yok", len(dup) == 1 and after_n == before_n, f"kayıt={len(dup)} bildirim {before_n}->{after_n}")
    cdp.js(f"VHApp.api.setList({{id: '{lev_id}', list: 'TRACK'}})")
    post(16, "Fiyatı düştü", "Levi's 501 vintage kot 180 TL yerine 150 TL")
    time.sleep(4)
    lev2 = cdp.js(f"VHApp.api.get({{id: '{lev_id}'}})")["item"]
    drops = [n for n in notifs(cdp) if n.get("listing_id") == lev_id and n["kind"] == "PRICE_DROP"]
    record("Takip edilen ilanda fiyat düşüşü: kayıt güncellendi ve bildirim gitti", lev2["price"] == 150 and lev2.get("prev_price") == 180 and drops,
           {"fiyat": lev2["price"], "önceki": lev2.get("prev_price"), "bildirim": drops[:1]})

    # ---------------------------------------------------------------- Test 8: kayıtlı aramalar karışmaz
    cdp.js("VHApp.test.setSearchActive('nike', false)")
    a_id = cdp.js("VHApp.test.saveSearch({name: 'Nike ceket test', keywords: ['Nike + ceket'], categoryId: 'giyim', maxPrice: 200, platforms: ['Dolap']})")
    b_id = cdp.js("VHApp.test.saveSearch({name: 'Nike Air test', keywords: ['Nike Air'], categoryId: 'giyim', minPrice: 100, maxPrice: 250})")
    post(17, "Yeni ilan", "Vintage Nike spor ceket 220 TL")
    post(18, "Yeni ilan", "Nike Air Max 90 220 TL")
    time.sleep(5)
    la = listings(cdp, searchId=a_id)
    lb = listings(cdp, searchId=b_id)
    ra = find(la, "Nike spor ceket")
    rb = find(lb, "Nike Air Max 90")
    ok8 = len(la) == 1 and ra and ra[0]["in_limit"] == 0 and len(lb) == 1 and rb and rb[0]["in_limit"] == 1
    record("Test 8 — İki kayıtlı arama kendi kuralıyla çalışıyor (A: 200 sınırı → uygun değil, B: uygun)", ok8,
           {"A": [(x["text"], x["in_limit"]) for x in la], "B": [(x["text"], x["in_limit"]) for x in lb]})

    # ---------------------------------------------------------------- Gelişmiş filtre örneği (belge 6. bölüm)
    cdp.js("VHApp.test.applyFilter({q: 'nike', platforms: ['Dolap', 'Gardrops'], minPrice: 100, maxPrice: 1000}, false)")
    time.sleep(1.5)
    items = cdp.ev("JSON.stringify(VHApp.S.deals.items.map(x => [x.platform, x.price]))")
    items = json.loads(items)
    record("Filtre: 'nike' · Dolap+Gardrops · 100–1.000 TL birlikte uygulanıyor", items and all(p in ("Dolap", "Gardrops") and 100 <= (v or 0) <= 1000 for p, v in items), items)
    cdp.ev("VHApp.test.openFilter()")
    time.sleep(1.2)
    shot("06-filtre-paneli")
    cdp.ev("document.querySelector('[data-act=close-sheet]').click()")

    # ---------------------------------------------------------------- Test 6: uygulama açık değilken
    sh("input keyevent KEYCODE_HOME")
    time.sleep(2)
    post(19, "Yeni ilan", "Funko Pop Batman 300 TL")
    time.sleep(5)
    rows = db_rows("SELECT price, in_limit, alerted FROM listings WHERE text LIKE '%Funko Pop Batman%'")
    dn = our_notifications()
    record("Test 6 — Uygulama ekranda değilken yakalama ve bildirim", rows and rows[0]["in_limit"] == 1 and "Uygun ilan · 300 TL" in dn, rows)

    # ---------------------------------------------------------------- Test 4: kalıcı kayıt (zorla kapat → aç)
    bring_app()
    cdp.connect(wait=30)
    cdp.js(f"VHApp.api.setFav({{id: '{lev_id}', fav: true}})")
    before = {"ilan": len(listings(cdp)), "arama": len(cdp.ev("JSON.stringify(VHApp.S.cfg.searches)") and json.loads(cdp.ev("JSON.stringify(VHApp.S.cfg.searches)"))),
              "favori": [x["id"] for x in listings(cdp, favOnly=True)], "bildirim": len(notifs(cdp)),
              "figur": cdp.ev("VHApp.S.cfg.categories.find(c => c.id === 'figur').maxPrice"), "filtre": cdp.ev("JSON.stringify(VHApp.S.ui.filter.platforms)")}
    sh(f"am force-stop {APP}")
    time.sleep(2)
    bring_app()
    cdp.connect(wait=40)
    time.sleep(2)
    after = {"ilan": len(listings(cdp)), "arama": len(json.loads(cdp.ev("JSON.stringify(VHApp.S.cfg.searches)"))),
             "favori": [x["id"] for x in listings(cdp, favOnly=True)], "bildirim": len(notifs(cdp)),
             "figur": cdp.ev("VHApp.S.cfg.categories.find(c => c.id === 'figur').maxPrice"), "filtre": cdp.ev("JSON.stringify(VHApp.S.ui.filter.platforms)")}
    record("Test 4 — Zorla kapatıp açınca ilanlar, aramalar, favoriler, sınırlar ve filtre korunuyor", before == after, {"önce": before, "sonra": after})

    st = wait_for(lambda: (lambda s: s if s["listenerConnected"] else None)(cdp.js("VHApp.api.status()")), timeout=40)
    post(20, "Yeni ilan", "Pokemon Pikachu figür 250 TL")
    pk = wait_for(lambda: find(listings(cdp), "Pikachu figür"), timeout=15)
    record("Zorla kapatma sonrası dinleyici yeniden bağlandı ve yakalamaya devam ediyor", bool(pk), {"status": st, "kayıt": pk[:1] if pk else None})

    # ---------------------------------------------------------------- Paylaş menüsü → gerçek bağlantı
    url = "https://dolap.com/urun/vintage-levis-505-e2e-test"
    sh("am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT " +
       shlex.quote(f"Bu ürüne göz at: Vintage Levis 505 kot {url}") + f" -n {APP}/.MainActivity")
    time.sleep(4)
    h = cdp.ev("location.hash")
    if h == "#share":
        cdp.ev("document.querySelector('#sh_price').value = '200'")
        cdp.ev("document.querySelector('[data-act=share-save]').click()")
        time.sleep(3)
    shared = [x for x in listings(cdp) if x.get("url") == url]
    record("Paylaş → VintageHunter: ilanın gerçek bağlantısı kaydedildi ve kurala göre değerlendirildi",
           h == "#share" and shared and shared[0]["kind"] == "SHARED" and shared[0]["in_limit"] == 1, {"hash": h, "kayıt": shared[:1]})

    # ---------------------------------------------------------------- ekran görüntüleri
    cdp.js("VHApp.test.applyFilter({}, true)")
    for name, h in [("07-ana-sayfa", "home"), ("08-firsatlar", "deals"), ("10-aramalar", "searches"), ("11-arama-duzenle", "search/denim"),
                    ("12-koleksiyon", "collection"), ("13-ayarlar", "settings"), ("14-kategori-sinirlari", "categories"),
                    ("15-sistem-durumu", "status"), ("16-bildirim-gecmisi", "history")]:
        cdp.ev(f"VHApp.go({json.dumps(h)})")
        time.sleep(2.2)
        cdp.ev("window.scrollTo(0,0)")
        shot(name)
    cdp.ev(f"VHApp.go('listing/{lev_id}')")
    time.sleep(2.2)
    shot("09-ilan-detayi")
    cdp.ev("window.scrollTo(0, 700)")
    shot("09b-ilan-detayi-analiz")

    errs = cdp.ev("JSON.stringify(window.__errors || [])")
    summary = db_rows("SELECT kind, platform, price, price_status, in_limit, reason, category_id, substr(text,1,60) AS text FROM listings ORDER BY captured_at")
    json.dump(summary, open(f"{OUT}/db-listings.json", "w"), ensure_ascii=False, indent=1)
    json.dump(db_rows("SELECT platform, result, substr(text,1,80) AS text FROM raw_log ORDER BY at"), open(f"{OUT}/db-raw-log.json", "w"), ensure_ascii=False, indent=1)
    record("Arayüzde yakalanmamış JavaScript hatası yok", errs in ("[]", None), errs)


if __name__ == "__main__":
    try:
        main()
    except Exception as e:  # noqa: BLE001
        import traceback
        record("Test betiği hatasız tamamlandı", False, traceback.format_exc())
    passed = sum(1 for r in RESULTS if r["ok"])
    report = {"passed": passed, "total": len(RESULTS), "results": RESULTS, "time": time.strftime("%Y-%m-%d %H:%M:%S")}
    json.dump(report, open(f"{OUT}/report.json", "w"), ensure_ascii=False, indent=1)
    print(f"\n{passed}/{len(RESULTS)} test geçti", flush=True)
    sys.exit(0 if passed == len(RESULTS) and RESULTS else 1)
