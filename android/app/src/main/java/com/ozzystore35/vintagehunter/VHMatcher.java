package com.ozzystore35.vintagehunter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform bildirim metnini marka/seri listesi ve fiyat aralığıyla eşleştirir.
 * Android bağımlılığı yoktur; düz Java ile test edilebilir.
 */
public final class VHMatcher {

    public static final class Rule {
        public final String group, groupName, kind;
        public final String[] terms;
        public final double min, max;

        public Rule(String group, String groupName, String kind, String[] terms, double min, double max) {
            this.group = group; this.groupName = groupName; this.kind = kind;
            this.terms = terms; this.min = min; this.max = max;
        }
    }

    public static final class Result {
        /** MATCH, NO_PRICE, OVER_PRICE, NO_BRAND */
        public String status = "NO_BRAND";
        public String group, groupName, term;
        public Double price;
    }

    private static final Locale TR = Locale.forLanguageTag("tr-TR");
    private static final String NUM = "([0-9]{1,3}(?:[.,][0-9]{3})+(?:,[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)";
    private static final Pattern SUFFIX = Pattern.compile(NUM + "\\s?(?:TL|tl|Tl|₺)(?![a-zA-Z])");
    private static final Pattern PREFIX = Pattern.compile("(?:₺|TL\\s)\\s?" + NUM);

    private VHMatcher() {}

    /** Küçük harf, Türkçe karakterleri sadeleştir, harf/rakam dışını boşluk yap. */
    public static String fold(String s) {
        if (s == null) return " ";
        String t = s.replace('İ', 'i').replace('I', 'ı').toLowerCase(TR);
        t = t.replace('ç', 'c').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ş', 's').replace('ü', 'u')
             .replace('é', 'e').replace('ä', 'a').replace('ë', 'e');
        t = t.replace("'", "").replace("’", "");
        t = t.replaceAll("[^a-z0-9]+", " ").trim();
        return " " + t + " ";
    }

    static Double toNumber(String raw) {
        String v = raw;
        if (v.contains(",")) {
            v = v.replace(".", "").replace(',', '.');
        } else if (v.matches("[0-9]{1,3}(\\.[0-9]{3})+")) {
            v = v.replace(".", "");
        }
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
    }

    /** Metindeki TL fiyatlarının en düşüğü (fiyat düşüşü bildiriminde yeni fiyat). */
    public static Double parsePrice(String text) {
        if (text == null) return null;
        List<Double> found = new ArrayList<>();
        for (Pattern p : new Pattern[]{SUFFIX, PREFIX}) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                Double d = toNumber(m.group(1));
                if (d != null && d > 0) found.add(d);
            }
        }
        if (found.isEmpty()) return null;
        double min = found.get(0);
        for (double d : found) min = Math.min(min, d);
        return min;
    }

    public static Result match(String text, List<Rule> rules) {
        Result r = new Result();
        r.price = parsePrice(text);
        String hay = fold(text);
        Rule bestRule = null; String bestTerm = null; boolean bestInRange = false;
        for (Rule rule : rules) {
            for (String term : rule.terms) {
                String f = fold(term);
                if (f.trim().isEmpty() || !hay.contains(f)) continue;
                boolean inRange = r.price != null && r.price >= rule.min && r.price <= rule.max;
                // Önce fiyatı aralıkta olan kural, sonra daha uzun (daha belirli) terim
                boolean better = bestRule == null
                        || (inRange && !bestInRange)
                        || (inRange == bestInRange && f.length() > fold(bestTerm).length());
                if (better) { bestRule = rule; bestTerm = term; bestInRange = inRange; }
            }
        }
        if (bestRule == null) return r;
        r.group = bestRule.group; r.groupName = bestRule.groupName; r.term = bestTerm;
        if (r.price == null) r.status = "NO_PRICE";
        else r.status = bestInRange ? "MATCH" : "OVER_PRICE";
        return r;
    }

    /** Paket adından platform; tanınmıyorsa null. */
    public static String platformFor(String pkg, List<String> extra) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        if (p.contains("dolap")) return "Dolap";
        if (p.contains("gardrops")) return "Gardrops";
        if (p.contains("sahibinden")) return "Sahibinden";
        if (p.contains("letgo") || p.contains("ambatana")) return "Letgo";
        if (extra != null && extra.contains(pkg)) return pkg;
        return null;
    }
}
