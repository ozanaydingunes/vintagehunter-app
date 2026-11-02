package com.ozzystore35.vintagehunter.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bildirim metninden Türk lirası fiyatı ayrıştırır.
 *
 * Kurallar:
 *  - Yalnızca para birimi yazan tutarlar fiyattır: "180 TL", "1.250,50 TL", "₺900", "TL 250", "300 TRY".
 *    Para birimi olmayan sayılar (model no "501", yıl "1988", beden "42") asla fiyat sayılmaz.
 *  - Binlik/ondalık: "1.250" = 1250, "1,250" = 1250, "1.250,50" = 1250.5, "1,250.50" = 1250.5, "250,90" = 250.9.
 *  - Kargo, indirim, kupon, teklif, tasarruf, "X TL üzeri / X TL'ye kadar" gibi tutarlar ilan fiyatı değildir.
 *  - İki farklı fiyat + düşüş/indirim ifadesi ("2.000 TL yerine 900 TL", "1.200 TL'den 950 TL'ye düştü") → yeni fiyat düşük olan.
 *  - Başka durumda birden fazla farklı fiyat → BELİRSİZ. Belirsiz fiyat hiçbir zaman fırsat sayılmaz.
 *  - "4 yeni ilan", "yeni ilanlar var", "... TL'den başlayan" → TOPLU arama bildirimi; tek ilan fiyatı yok.
 *  - $, €, £, USD, EUR → YABANCI para; TL tutarı yoksa fiyat yok sayılır.
 */
public final class VHPrice {

    public static final String OK = "OK";
    public static final String MISSING = "MISSING";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String FOREIGN = "FOREIGN";
    public static final String AGGREGATE = "AGGREGATE";

    public static final class Parsed {
        public String status = MISSING;
        public Double price;
        public Double oldPrice;
        /** Hata ayıklama için: dikkate alınmayan tutarlar ve nedeni. */
        public final List<String> ignored = new ArrayList<>();

        public boolean ok() { return OK.equals(status) && price != null; }
    }

    private VHPrice() {}

    private static final String NUM = "(\\d{1,3}(?:[.,]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)";
    private static final Pattern SUFFIX = Pattern.compile(NUM + "\\s?(?:TL|Tl|tl|TRY|try|₺)(?![A-Za-zÇĞİÖŞÜçğıöşü])");
    private static final Pattern PREFIX = Pattern.compile("(?:₺|(?<![A-Za-zÇĞİÖŞÜçğıöşü])(?:TL|TRY))\\s?" + NUM);
    private static final Pattern FOREIGN_RX = Pattern.compile(
            "[$€£]\\s?\\d|\\d\\s?[$€£]|\\d\\s?(?:USD|EUR|GBP|usd|eur|gbp|dolar|euro|Dolar|Euro)\\b|\\b(?:USD|EUR|GBP)\\s?\\d");
    private static final Pattern AGGREGATE_RX = Pattern.compile(
            " \\d+ (?:adet )?(?:yeni )?ilan(?:lar)? | yeni ilanlar | ilanlar var | yeni urunler | dan baslayan | den baslayan | baslayan fiyat");

    private static final String[] BEFORE_FEE = {" kargo ", " kargo ucreti ", " kargo bedeli ", " indirim ", " kupon ", " kuponu ",
            " teklif ", " teklifi ", " tasarruf ", " puan ", " bakiye ", " komisyon ", " hizmet bedeli ", " min ", " minimum ", " sepette "};
    private static final String[] AFTER_FEE = {" indirim", " kupon", " tasarruf", " uzeri", " ve uzeri", " ye kadar", " ya kadar",
            " a kadar", " e kadar", " kadar", " kargo ucreti", " kargo bedeli", " puan", " bakiye", " kazan", " hediye", " teklif"};
    private static final String[] DROP_WORDS = {" yerine ", " dustu ", " dusti ", " dusus ", " indirimde ", " indirime ",
            " eski fiyat ", " yeni fiyat ", " fiyati guncellendi ", " fiyat guncellemesi "};

    /** Türkçe/İngilizce yazımlı sayıyı çevirir. Hatalıysa null. */
    public static Double toNumber(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String v = raw;
        int dot = v.lastIndexOf('.'), comma = v.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            if (comma > dot) v = v.replace(".", "").replace(',', '.');   // 1.250,50
            else v = v.replace(",", "");                                 // 1,250.50
        } else if (comma >= 0) {
            if (v.matches("\\d{1,3}(,\\d{3})+")) v = v.replace(",", ""); // 1,250  → binlik
            else v = v.replace(',', '.');                                // 250,90 → ondalık
        } else if (dot >= 0) {
            if (v.matches("\\d{1,3}(\\.\\d{3})+")) v = v.replace(".", ""); // 1.250 → binlik
        }
        try {
            double d = Double.parseDouble(v);
            return (d > 0 && d < 100_000_000) ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Hit {
        final double value; final int start, end;
        Hit(double v, int s, int e) { value = v; start = s; end = e; }
    }

    public static Parsed parse(String text) {
        Parsed p = new Parsed();
        if (text == null || text.trim().isEmpty()) return p;
        String folded = VHText.fold(text);
        boolean aggregate = AGGREGATE_RX.matcher(folded).find();

        List<Hit> hits = new ArrayList<>();
        for (Pattern rx : new Pattern[]{SUFFIX, PREFIX}) {
            Matcher m = rx.matcher(text);
            while (m.find()) {
                Double d = toNumber(m.group(1));
                if (d == null) continue;
                boolean dup = false;
                for (Hit h : hits) if (m.start() < h.end && h.start < m.end()) { dup = true; break; }
                if (!dup) hits.add(new Hit(d, m.start(), m.end()));
            }
        }

        Set<Double> values = new LinkedHashSet<>();
        for (Hit h : hits) {
            String before = benign(VHText.fold(clauseBefore(text.substring(Math.max(0, h.start - 18), h.start))));
            String after = benign(VHText.fold(clauseAfter(text.substring(h.end, Math.min(text.length(), h.end + 16)))));
            String reason = null;
            for (String w : BEFORE_FEE) if (before.contains(w)) { reason = w.trim(); break; }
            if (reason == null) for (String w : AFTER_FEE) if (after.startsWith(w + " ") || after.startsWith(w)) { reason = w.trim(); break; }
            if (reason == null && after.startsWith(" kargo ") && (after.contains(" kargo ucret") || after.contains(" kargo bedel"))) reason = "kargo";
            if (reason != null) { p.ignored.add(fmt(h.value) + " TL (" + reason + ")"); continue; }
            values.add(h.value);
        }

        boolean foreign = FOREIGN_RX.matcher(text).find();
        if (aggregate) {
            p.status = AGGREGATE;
            return p;
        }
        if (values.isEmpty()) {
            p.status = foreign ? FOREIGN : MISSING;
            return p;
        }
        if (values.size() == 1) {
            p.status = OK;
            p.price = values.iterator().next();
            return p;
        }
        boolean drop = text.contains("→") || text.contains("->") || text.contains("⇒");
        for (String w : DROP_WORDS) if (folded.contains(w)) { drop = true; break; }
        if (values.size() == 2 && drop) {
            double a = 0, b = 0; int i = 0;
            for (double v : values) { if (i++ == 0) a = v; else b = v; }
            p.status = OK;
            p.price = Math.min(a, b);
            p.oldPrice = Math.max(a, b);
            return p;
        }
        p.status = AMBIGUOUS;
        return p;
    }

    private static final String[] DELIMS = {"!", "·", "|", ";", "\n", "•", "?", ". ", ", "};

    /** Tutarın önündeki metni, son cümle/öbek ayracından sonrasıyla sınırla ("Ücretsiz kargo! Nike 180 TL" → "Nike "). */
    private static String clauseBefore(String s) {
        int cut = -1;
        for (String d : DELIMS) cut = Math.max(cut, s.lastIndexOf(d) + (s.lastIndexOf(d) >= 0 ? d.length() : 0));
        return cut > 0 ? s.substring(cut) : s;
    }

    private static String clauseAfter(String s) {
        int cut = s.length();
        for (String d : DELIMS) { int i = s.indexOf(d); if (i >= 0) cut = Math.min(cut, i); }
        return s.substring(0, cut);
    }

    /** Fiyatı ücret yapmayan kalıpları çıkar: "kargo dahil", "ücretsiz kargo", "kargo bedava". */
    private static String benign(String folded) {
        return folded.replace(" ucretsiz kargo ", " ").replace(" kargo dahil ", " ").replace(" kargo bedava ", " ")
                .replace(" kargo ucretsiz ", " ").replace(" alici odemeli ", " ");
    }

    static String fmt(double v) {
        return (v == Math.rint(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
