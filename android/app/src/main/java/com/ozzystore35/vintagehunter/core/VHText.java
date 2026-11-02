package com.ozzystore35.vintagehunter.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Türkçe metin sadeleştirme ve tam kelime/öbek eşleştirme. Android bağımlılığı yok. */
public final class VHText {
    private static final Locale TR = Locale.forLanguageTag("tr-TR");

    private VHText() {}

    /**
     * Küçük harf, Türkçe karakterleri ASCII'ye indir, kesme işaretini ve noktalama işaretlerini boşluk yap.
     * Sonuç başında ve sonunda bir boşlukla döner: " levi s 501 kot " — böylece " kelime " araması tam kelime olur.
     */
    public static String fold(String s) {
        if (s == null || s.isEmpty()) return " ";
        String t = s.replace('İ', 'i').replace('I', 'ı').toLowerCase(TR);
        StringBuilder b = new StringBuilder(t.length() + 2);
        b.append(' ');
        boolean space = true;
        for (int i = 0; i < t.length(); i++) {
            char c = map(t.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                b.append(c);
                space = false;
            } else if (!space) {
                b.append(' ');
                space = true;
            }
        }
        if (!space) b.append(' ');
        return b.toString();
    }

    private static char map(char c) {
        switch (c) {
            case 'ç': return 'c';
            case 'ğ': return 'g';
            case 'ı': return 'i';
            case 'ö': case 'ó': case 'ò': case 'ô': return 'o';
            case 'ş': return 's';
            case 'ü': case 'ú': case 'ù': case 'û': return 'u';
            case 'â': case 'á': case 'à': case 'ä': case 'å': return 'a';
            case 'î': case 'í': case 'ì': case 'ï': return 'i';
            case 'é': case 'è': case 'ê': case 'ë': return 'e';
            case 'ñ': return 'n';
            default: return c;
        }
    }

    /** Sadeleştirilmiş metinde terim tam kelime/öbek olarak geçiyor mu? hay, fold() çıktısı olmalı. */
    public static boolean hasPhrase(String foldedHay, String term) {
        String f = fold(term);
        if (f.trim().isEmpty()) return false;
        return foldedHay.contains(f);
    }

    /**
     * Kayıtlı arama anahtar kelimesi: "Levi's 501" öbek olarak geçmeli; "Nike + ceket" ise her parça ayrı ayrı geçmeli.
     */
    public static boolean hasKeyword(String foldedHay, String keyword) {
        if (keyword == null) return false;
        if (keyword.indexOf('+') < 0) return hasPhrase(foldedHay, keyword);
        boolean any = false;
        for (String part : keyword.split("\\+")) {
            if (part.trim().isEmpty()) continue;
            if (!hasPhrase(foldedHay, part)) return false;
            any = true;
        }
        return any;
    }

    /** Metindeki tüm kelimeler (sırası önemsiz) geçiyor mu? "vintage nike ceket" → üçü de var mı. */
    public static boolean hasAllWords(String foldedHay, String query) {
        String f = fold(query).trim();
        if (f.isEmpty()) return true;
        for (String w : f.split(" ")) {
            if (!foldedHay.contains(" " + w + " ")) return false;
        }
        return true;
    }

    public static List<String> splitList(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String p : csv.split("[,\n;]")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static final String[] CITIES = {
        "adana", "adiyaman", "afyon", "afyonkarahisar", "agri", "aksaray", "amasya", "ankara", "antalya", "ardahan", "artvin",
        "aydin", "balikesir", "bartin", "batman", "bayburt", "bilecik", "bingol", "bitlis", "bolu", "burdur", "bursa",
        "canakkale", "cankiri", "corum", "denizli", "diyarbakir", "duzce", "edirne", "elazig", "erzincan", "erzurum",
        "eskisehir", "gaziantep", "giresun", "gumushane", "hakkari", "hatay", "igdir", "isparta", "istanbul", "izmir",
        "kahramanmaras", "karabuk", "karaman", "kars", "kastamonu", "kayseri", "kilis", "kirikkale", "kirklareli",
        "kirsehir", "kocaeli", "konya", "kutahya", "malatya", "manisa", "mardin", "mersin", "mugla", "mus", "nevsehir",
        "nigde", "ordu", "osmaniye", "rize", "sakarya", "samsun", "sanliurfa", "siirt", "sinop", "sirnak", "sivas",
        "tekirdag", "tokat", "trabzon", "tunceli", "usak", "van", "yalova", "yozgat", "zonguldak"
    };

    /** Bildirimde il adı geçiyorsa döndürür (ör. "istanbul"), yoksa null. */
    public static String city(String foldedHay) {
        for (String c : CITIES) if (foldedHay.contains(" " + c + " ")) return c;
        return null;
    }

    /** "NEW", "USED" veya "UNKNOWN" — yalnızca bildirim metninde açıkça yazıyorsa. */
    public static String condition(String foldedHay) {
        if (foldedHay.contains(" az kullanilmis ") || foldedHay.contains(" ikinci el ") || foldedHay.contains(" 2 el ")
                || foldedHay.contains(" kullanilmis ") || foldedHay.contains(" yeni gibi ") || foldedHay.contains(" temiz kullanilmis ")) {
            return "USED";
        }
        if (foldedHay.contains(" sifir ") || foldedHay.contains(" etiketli ") || foldedHay.contains(" kullanilmamis ")
                || foldedHay.contains(" hic kullanilmadi ") || foldedHay.contains(" kutusu acilmamis ")) {
            return "NEW";
        }
        return "UNKNOWN";
    }
}
