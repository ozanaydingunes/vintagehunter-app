package com.ozzystore35.vintagehunter.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ayarlar ve değerlendirme sonuçları için düz Java veri sınıfları. */
public final class VHModel {
    private VHModel() {}

    public static final String[] PLATFORMS = {"Dolap", "Gardrops", "Sahibinden", "Letgo"};

    public static final class Category {
        public String id, name;
        /** Kategori üst sınırı (TL, dahil). null = sınırsız. */
        public Double maxPrice;
        public Double minPrice;
    }

    public static final class Search {
        public String id, name, categoryId;
        public List<String> keywords = new ArrayList<>();
        public List<String> exclude = new ArrayList<>();
        /** Boşsa tüm platformlar. */
        public List<String> platforms = new ArrayList<>();
        public Double minPrice, maxPrice;
        /** Bu arama için gereken en düşük fırsat oranı (%). null = şart yok. */
        public Double minDealPct;
        /** Kullanıcının bu arama için girdiği tahmini piyasa değeri (TL). null = bilinmiyor. */
        public Double marketValue;
        /** INSTANT | DIGEST | OFF */
        public String notify = "INSTANT";
        public boolean active = true;
    }

    public static final class Settings {
        public Map<String, Boolean> platformEnabled = new LinkedHashMap<>();
        public double tierVeryHigh = 60, tierHigh = 45, tierMedium = 25;
        public boolean notifyInstant = true, notifyPriorityHigh = true, notifyPriceDrop = true;
        /** 0 = kapalı, 30 veya 60 dakika. */
        public int digestMinutes = 30;
        public int digestStartHour = 9, digestEndHour = 22;
        /** -1 = sessiz saat yok. */
        public int quietStart = -1, quietEnd = -1;
        public List<String> globalExclude = new ArrayList<>();

        public boolean platformOn(String p) {
            Boolean b = platformEnabled.get(p);
            return b == null || b;
        }
    }

    public static final class Config {
        public List<Category> categories = new ArrayList<>();
        public List<Search> searches = new ArrayList<>();
        public Settings settings = new Settings();

        public Category category(String id) {
            for (Category c : categories) if (c.id.equals(id)) return c;
            return null;
        }

        public Search search(String id) {
            for (Search s : searches) if (s.id.equals(id)) return s;
            return null;
        }
    }

    /** Bir aramanın ilanla eşleşme sonucu. */
    public static final class SearchMatch {
        public String searchId, categoryId, term;
        public boolean inLimit;
        /** inLimit=false ise nedeni: PRICE_MISSING, PRICE_AMBIGUOUS, PRICE_FOREIGN, AGGREGATE, OVER_MAX, UNDER_MIN, DEAL_UNKNOWN, DEAL_LOW */
        public String reason;
        public double effMin;
        public Double effMax;
        public String notify;
    }

    public static final class Evaluation {
        public final List<SearchMatch> matches = new ArrayList<>();
        /** En az bir aktif kayıtlı aramayla anahtar kelime eşleşmesi var mı? */
        public boolean matched;
        /** Fiyatı okunmuş ve en az bir aramanın + kategorisinin fiyat kuralına uyuyor mu? */
        public boolean inLimit;
        public String categoryId, searchId, term, reason;
        public Double marketValue, dealPct;
        /** VERY_HIGH, HIGH, MEDIUM, LOW, null (piyasa değeri yok) */
        public String tier;
        /** NONE, HIGH_DEAL, MATCH, DIGEST */
        public String alert = "NONE";

        public String searchIdsCsv() {
            StringBuilder b = new StringBuilder(",");
            for (SearchMatch m : matches) b.append(m.searchId).append(',');
            return matches.isEmpty() ? "" : b.toString();
        }
    }
}
