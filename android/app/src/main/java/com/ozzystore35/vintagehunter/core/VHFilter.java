package com.ozzystore35.vintagehunter.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Arama ekranındaki filtre paneli. Sorgu aşamasında (veritabanından okurken) uygulanır;
 * arayüz yalnızca bu filtreden geçen kayıtları gösterir.
 */
public final class VHFilter {

    /** Filtrelenecek bir ilan kaydının özeti. */
    public static final class Row {
        public String id, platform, title, text, priceStatus, categoryId, condition, city, listTag, kind;
        public Double price, dealPct;
        public boolean inLimit, fav;
        public long at;
    }

    public String q;
    public List<String> exclude = new ArrayList<>();
    public Set<String> categories = new HashSet<>();
    public Set<String> platforms = new HashSet<>();
    public Double minPrice, maxPrice, minDealPct;
    /** NEW, USED veya null */
    public String condition;
    public long sinceMs;
    public String city;
    public boolean favOnly, inLimitOnly;
    /** TRACK, CONSIDER, BOUGHT veya null */
    public String listTag;
    /** LISTING, AGGREGATE, SHARED, MANUAL veya null (hepsi) */
    public String kind;
    /** true: toplu arama bildirimlerini (AGGREGATE) gösterme. */
    public boolean listingsOnly;

    public boolean test(Row r) {
        if (kind != null && !kind.equals(r.kind)) return false;
        if (listingsOnly && "AGGREGATE".equals(r.kind)) return false;
        if (inLimitOnly && !r.inLimit) return false;
        if (favOnly && !r.fav) return false;
        if (listTag != null && !listTag.equals(r.listTag)) return false;
        if (!platforms.isEmpty() && !platforms.contains(r.platform)) return false;
        if (!categories.isEmpty() && (r.categoryId == null || !categories.contains(r.categoryId))) return false;
        if (sinceMs > 0 && r.at < sinceMs) return false;
        if (minPrice != null || maxPrice != null) {
            // Fiyat filtresi varken fiyatı okunamayan ilan asla geçmez.
            if (!VHPrice.OK.equals(r.priceStatus) || r.price == null) return false;
            if (minPrice != null && r.price < minPrice) return false;
            if (maxPrice != null && r.price > maxPrice) return false;
        }
        if (minDealPct != null && minDealPct > 0) {
            if (r.dealPct == null || r.dealPct < minDealPct) return false;
        }
        if (condition != null && !condition.equals(r.condition)) return false;
        String hay = VHText.fold((r.title == null ? "" : r.title) + " " + (r.text == null ? "" : r.text));
        if (city != null && !city.trim().isEmpty() && !VHText.hasPhrase(hay, city)) return false;
        if (q != null && !q.trim().isEmpty() && !VHText.hasAllWords(hay, q)) return false;
        for (String x : exclude) if (VHText.hasPhrase(hay, x)) return false;
        return true;
    }
}
