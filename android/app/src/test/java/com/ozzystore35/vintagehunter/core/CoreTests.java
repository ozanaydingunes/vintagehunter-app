package com.ozzystore35.vintagehunter.core;

import com.ozzystore35.vintagehunter.core.VHModel.Config;
import com.ozzystore35.vintagehunter.core.VHModel.Evaluation;
import com.ozzystore35.vintagehunter.core.VHModel.Search;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Çekirdek mantık testleri. Hem `java CoreTests` ile hem Gradle/JUnit (CoreTest) ile çalışır.
 * Kabul testleri: T1 fiyat filtresi, T5 kategori bağımsızlığı, T7 hatalı/eksik fiyat, T8 kayıtlı aramalar.
 */
public final class CoreTests {
    private static final List<String> FAILS = new ArrayList<>();
    private static int passed = 0;

    static void eq(Object actual, Object expected, String name) {
        if (Objects.equals(actual, expected)) passed++;
        else FAILS.add(name + ": beklenen <" + expected + "> gelen <" + actual + ">");
    }

    static void ok(boolean cond, String name) { eq(cond, true, name); }

    static Config defaults() throws Exception {
        Path p = null;
        for (String c : new String[]{"src/main/res/raw/vh_default_config.json", "android/app/src/main/res/raw/vh_default_config.json",
                "app/src/main/res/raw/vh_default_config.json", System.getProperty("vh.config", "")}) {
            if (!c.isEmpty() && Files.exists(Paths.get(c))) { p = Paths.get(c); break; }
        }
        if (p == null) throw new IllegalStateException("vh_default_config.json bulunamadı");
        return VHConfigJson.parse(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
    }

    static void price(String text, String status, Double price) {
        VHPrice.Parsed p = VHPrice.parse(text);
        eq(p.status, status, "fiyat durumu: " + text);
        eq(p.price, price, "fiyat: " + text);
    }

    static Evaluation ev(Config c, String platform, String text) {
        return VHEval.evaluate(platform, text, VHPrice.parse(text), null, c);
    }

    public static List<String> run() throws Exception {
        FAILS.clear();
        passed = 0;

        // ---------- Metin ----------
        eq(VHText.fold("İNDİRİM Levi's 501!"), " indirim levi s 501 ", "fold: Türkçe İ ve kesme");
        ok(VHText.hasPhrase(VHText.fold("He-Man Battle Cat"), "He-Man"), "tire");
        ok(!VHText.hasPhrase(VHText.fold("Vansan ceket"), "Vans"), "tam kelime: Vansan ≠ Vans");
        ok(VHText.hasPhrase(VHText.fold("Nike'ın ceketi"), "Nike"), "Türkçe ek: Nike'ın");
        ok(VHText.hasPhrase(VHText.fold("STÜSSY hoodie"), "Stussy"), "ü → u");
        ok(VHText.hasAllWords(VHText.fold("Vintage NIKE spor ceket"), "vintage nike ceket"), "tüm kelimeler");
        eq(VHText.city(VHText.fold("Kadıköy, İstanbul")), "istanbul", "il");
        ok(VHText.hasKeyword(VHText.fold("Vintage NIKE spor ceket"), "nike + ceket"), "+ ile VE eşleşmesi");
        ok(!VHText.hasKeyword(VHText.fold("Vintage NIKE sweatshirt"), "nike + ceket"), "+ parçalarından biri yoksa eşleşmez");
        eq(VHText.condition(VHText.fold("Sıfır etiketli")), "NEW", "durum yeni");
        eq(VHText.condition(VHText.fold("az kullanılmış temiz")), "USED", "durum ikinci el");

        // ---------- Fiyat ayrıştırma ----------
        price("Levi's 501 kot 180 TL", "OK", 180.0);
        price("Fiyat: 1.250,50 TL", "OK", 1250.5);
        price("₺900 · Dolap", "OK", 900.0);
        price("TL 250 pazarlık", "OK", 250.0);
        price("300 TRY", "OK", 300.0);
        price("He-Man figür 1,500 TL", "OK", 1500.0);
        price("Ceket 250,90 TL", "OK", 250.9);
        price("Jacket 1,250.50 TL", "OK", 1250.5);
        price("He-Man figür 1.501 TL", "OK", 1501.0);
        price("Levi's 501 kot 1.200 TL · Kargo 39,99 TL", "OK", 1200.0);
        price("Ücretsiz kargo! Nike ceket 180 TL", "OK", 180.0);
        price("Nike ceket 180 TL, kargo dahil", "OK", 180.0);
        price("500 TL üzeri alışverişte kargo bedava, Nike ceket 180 TL", "OK", 180.0);
        price("200 TL indirim! Ceket 450 TL", "OK", 450.0);
        price("Teklif: 150 TL · Nike ceket 300 TL", "OK", 300.0);
        price("He-Man 2.000 TL yerine 900 TL", "OK", 900.0);
        eq(VHPrice.parse("He-Man 2.000 TL yerine 900 TL").oldPrice, 2000.0, "eski fiyat");
        price("Fiyatı 1.200 TL'den 950 TL'ye düştü", "OK", 950.0);
        price("Levi's 501 için 4 yeni ilan, 90 TL'den başlayan", "AGGREGATE", null);
        price("Kayıtlı aramanda yeni ilanlar var", "AGGREGATE", null);
        price("Levi's 501 kot", "MISSING", null);
        price("Levi's 501 W32 1988 model", "MISSING", null);
        price("Levi's 501 $20", "FOREIGN", null);
        price("Nike 150 TL veya Adidas 200 TL", "AMBIGUOUS", null);
        price("", "MISSING", null);
        eq(VHPrice.toNumber("1,5"), 1.5, "1,5");
        eq(VHPrice.toNumber("0"), null, "sıfır fiyat yok");

        // ---------- Varsayılan ayarlar ----------
        Config c = defaults();
        eq(VHConfigJson.validate(c), null, "varsayılan ayar geçerli");
        eq(c.category("figur").maxPrice, 500.0, "figür sınırı 500");
        eq(c.category("konsol").maxPrice, 1000.0, "konsol sınırı 1000");
        eq(c.category("giyim").maxPrice, 250.0, "kıyafet sınırı 250");
        eq(c.category("antika").maxPrice, 500.0, "antika sınırı 500");
        eq(c.category("elektronik").maxPrice, 500.0, "elektronik sınırı 500");

        Evaluation e = ev(c, "Dolap", "Kayıtlı aramanda yeni ilan: Levi's 501 vintage kot 180 TL");
        ok(e.matched && e.inLimit, "Levi's 180 uygun");
        eq(e.categoryId, "giyim", "Levi's kategori");
        eq(e.alert, "MATCH", "Levi's anında bildirim");
        e = ev(c, "Dolap", "Levi's 501 vintage kot 251 TL");
        ok(e.matched && !e.inLimit, "Levi's 251 sınır üstü");
        eq(e.reason, "OVER_MAX", "neden OVER_MAX");
        eq(e.alert, "NONE", "sınır üstü bildirim yok");
        eq(ev(c, "Dolap", "Express kargo ile gönderildi 120 TL").matched, false, "Express marka sanılmaz");
        eq(ev(c, "Dolap", "Levi's 501 arıyorum 200 TL").reason, "EXCLUDED", "genel hariç: arıyorum");
        eq(ev(c, "Sahibinden", "Nintendo Switch OLED 900 TL").matched, false, "hariç: Switch");

        // ---------- T1: Fiyat filtresi (figür 1.500 TL) ----------
        c.category("figur").maxPrice = 1500.0;
        ok(ev(c, "Dolap", "He-Man figür 1.500 TL").inLimit, "T1: 1.500 uygun (sınır dahil)");
        ok(!ev(c, "Dolap", "He-Man figür 1.501 TL").inLimit, "T1: 1.501 uygun değil");
        for (String p : new String[]{"2.000", "3.000", "5.000"}) {
            Evaluation x = ev(c, "Dolap", "He-Man figür " + p + " TL");
            ok(!x.inLimit && "NONE".equals(x.alert), "T1: " + p + " TL ne listede ne bildirimde");
        }
        ok(ev(c, "Dolap", "He-Man figür 1.250,50 TL").inLimit, "T1: 1.250,50 uygun");

        // ---------- T5: Kategori bağımsızlığı ----------
        c.category("konsol").maxPrice = 3000.0;
        e = ev(c, "Sahibinden", "Super Nintendo SNES konsol 2.800 TL");
        ok(e.inLimit && "konsol".equals(e.categoryId), "T5: konsol 2.800 uygun (3.000 sınırı)");
        ok(!ev(c, "Dolap", "He-Man figür 2.800 TL").inLimit, "T5: figür 2.800 uygun değil (1.500 sınırı)");
        ok(!ev(c, "Sahibinden", "SNES 3.001 TL").inLimit, "T5: konsol 3.001 uygun değil");

        // ---------- T7: Hatalı ve eksik fiyatlar ----------
        for (String t : new String[]{"He-Man figür", "He-Man figür $40", "He-Man için 3 yeni ilan 100 TL'den başlayan",
                "He-Man 100 TL veya Skeletor 120 TL"}) {
            Evaluation x = ev(c, "Dolap", t);
            ok(x.matched && !x.inLimit && "NONE".equals(x.alert), "T7: fırsat sayılmaz: " + t);
        }
        eq(ev(c, "Dolap", "He-Man figür").reason, "PRICE_MISSING", "T7: neden fiyat yok");
        eq(ev(c, "Dolap", "He-Man figür $40").reason, "PRICE_FOREIGN", "T7: neden yabancı para");

        // ---------- T8: Kayıtlı aramalar birbirine karışmaz ----------
        Config c8 = defaults();
        c8.searches.removeIf(s -> s.id.equals("nike"));
        Search a = new Search();
        a.id = "a"; a.name = "Nike ceket"; a.categoryId = "giyim"; a.keywords = Arrays.asList("Nike ceket");
        a.maxPrice = 200.0; a.platforms = Arrays.asList("Dolap");
        Search b = new Search();
        b.id = "b"; b.name = "Nike Air"; b.categoryId = "giyim"; b.keywords = Arrays.asList("Nike Air");
        b.minPrice = 100.0; b.maxPrice = 250.0; b.platforms = Arrays.asList("Dolap", "Gardrops");
        c8.searches.add(a);
        c8.searches.add(b);
        e = ev(c8, "Dolap", "Vintage Nike ceket 220 TL");
        ok(e.matched && !e.inLimit && "a".equals(e.searchId), "T8: A eşleşti ama 200 sınırı üstü");
        eq(e.matches.size(), 1, "T8: B karışmadı");
        e = ev(c8, "Dolap", "Nike Air Max 90 220 TL");
        ok(e.inLimit && "b".equals(e.searchId), "T8: B uygun");
        eq(ev(c8, "Gardrops", "Nike ceket 150 TL").matched, false, "T8: A yalnız Dolap");
        ok(!ev(c8, "Dolap", "Nike Air Max 90 90 TL").inLimit, "T8: B alt sınır 100");
        a.maxPrice = 1000.0;
        eq(ev(c8, "Dolap", "Nike ceket 600 TL").reason, "OVER_MAX", "arama sınırı kategori sınırını aşamaz (250)");

        // ---------- Fırsat analizi ----------
        Config cd = defaults();
        Search tm = cd.search("tmnt");
        tm.marketValue = 2000.0;
        e = VHEval.evaluate("Dolap", "Ninja Turtles Leonardo 1988 figür 900 TL", VHPrice.parse("Ninja Turtles Leonardo 1988 figür 900 TL"), null, cd);
        cd.category("figur").maxPrice = 1000.0;
        e = VHEval.evaluate("Dolap", "Ninja Turtles Leonardo 1988 figür 900 TL", VHPrice.parse("Ninja Turtles Leonardo 1988 figür 900 TL"), null, cd);
        eq(Math.round(e.dealPct), 55L, "fırsat oranı %55");
        eq(e.tier, "HIGH", "kademe yüksek");
        eq(e.alert, "HIGH_DEAL", "öncelikli bildirim");
        eq(VHEval.tier(60.0, cd.settings), "VERY_HIGH", "%60 çok yüksek");
        eq(VHEval.tier(59.99, cd.settings), "HIGH", "%59,99 yüksek");
        eq(VHEval.tier(24.99, cd.settings), "LOW", "%24,99 düşük");
        cd.category("figur").maxPrice = 500.0;
        e = VHEval.evaluate("Dolap", "Ninja Turtles 900 TL", VHPrice.parse("Ninja Turtles 900 TL"), null, cd);
        ok(!e.inLimit && "NONE".equals(e.alert), "yüksek fırsat ama kategori sınırı üstü → bildirim yok");
        tm.marketValue = null;
        tm.minDealPct = 45.0;
        e = ev(cd, "Dolap", "Ninja Turtles 300 TL");
        eq(e.reason, "DEAL_UNKNOWN", "piyasa değeri yoksa fırsat şartı sağlanmaz");
        eq(e.dealPct, null, "değer uydurulmaz");
        e = VHEval.evaluate("Dolap", "Ninja Turtles 300 TL", VHPrice.parse("Ninja Turtles 300 TL"), 1000.0, cd);
        ok(e.inLimit && Math.round(e.dealPct) == 70, "ilana özel piyasa değeri %70");
        tm.minDealPct = null;
        tm.notify = "DIGEST";
        eq(ev(cd, "Dolap", "Ninja Turtles 300 TL").alert, "DIGEST", "özet modunda anında bildirim yok");
        tm.notify = "OFF";
        e = ev(cd, "Dolap", "Ninja Turtles 300 TL");
        ok(e.inLimit && "NONE".equals(e.alert), "bildirimi kapalı arama listede kalır");

        // ---------- Kimlik / tekrar ----------
        String k1 = VHIdent.key("Dolap", "Kayıtlı aramanda yeni ilan", "Levi's 501 vintage kot 180 TL");
        String k2 = VHIdent.key("Dolap", "Fiyatı düştü!", "Levi's 501 vintage kot 150 TL");
        eq(k1, k2, "aynı ürün, farklı fiyat → aynı kimlik");
        ok(!k1.equals(VHIdent.key("Gardrops", "Yeni ilan", "Levi's 501 vintage kot 180 TL")), "platform kimliğe dahil");
        ok(!k1.equals(VHIdent.key("Dolap", "Yeni ilan", "Levi's 505 vintage kot 180 TL")), "farklı model farklı kimlik");

        // ---------- Filtre paneli ----------
        VHFilter.Row r = new VHFilter.Row();
        r.platform = "Dolap"; r.title = "Yeni ilan"; r.text = "Vintage Nike ceket İstanbul"; r.priceStatus = "OK"; r.price = 350.0;
        r.categoryId = "giyim"; r.condition = "USED"; r.at = 1000; r.kind = "LISTING";
        VHFilter f = new VHFilter();
        f.q = "vintage nike ceket"; f.platforms.add("Dolap"); f.minPrice = 100.0; f.maxPrice = 1000.0; f.condition = "USED";
        ok(f.test(r), "filtre: Vintage Nike ceket örneği");
        f.platforms.clear(); f.platforms.add("Gardrops");
        ok(!f.test(r), "filtre: platform");
        f.platforms.clear();
        f.maxPrice = 349.99;
        ok(!f.test(r), "filtre: üst fiyat");
        f.maxPrice = 350.0;
        ok(f.test(r), "filtre: üst fiyat dahil");
        r.priceStatus = "MISSING"; r.price = null;
        ok(!f.test(r), "filtre: fiyatsız ilan fiyat filtresinden geçmez");
        r.priceStatus = "OK"; r.price = 350.0;
        f.exclude.add("İstanbul");
        ok(!f.test(r), "filtre: hariç kelime");
        f.exclude.clear();
        f.minDealPct = 45.0;
        ok(!f.test(r), "filtre: fırsat oranı bilinmiyorsa geçmez");
        r.dealPct = 50.0;
        ok(f.test(r), "filtre: fırsat oranı");
        f.city = "izmir";
        ok(!f.test(r), "filtre: konum");

        // ---------- Doğrulama ----------
        Config bad = defaults();
        bad.category("figur").maxPrice = -5.0;
        ok(VHConfigJson.validate(bad) != null, "negatif sınır reddedilir");
        bad = defaults();
        bad.settings.tierHigh = 70;
        ok(VHConfigJson.validate(bad) != null, "eşik sırası denetlenir");
        bad = defaults();
        bad.searches.get(0).keywords = new ArrayList<>();
        ok(VHConfigJson.validate(bad) != null, "boş anahtar kelime reddedilir");

        // ---------- Sessiz saat ----------
        VHModel.Settings qs = new VHModel.Settings();
        qs.quietStart = 23; qs.quietEnd = 8;
        ok(VHEval.quiet(qs, 23) && VHEval.quiet(qs, 3) && !VHEval.quiet(qs, 8) && !VHEval.quiet(qs, 14), "sessiz saat 23–08");

        return new ArrayList<>(FAILS);
    }

    public static void main(String[] args) throws Exception {
        List<String> f = run();
        for (String s : f) System.out.println("BAŞARISIZ  " + s);
        System.out.println((f.isEmpty() ? "TÜMÜ GEÇTİ" : f.size() + " BAŞARISIZ") + " · " + passed + " doğrulama geçti");
        System.exit(f.isEmpty() ? 0 : 1);
    }
}
