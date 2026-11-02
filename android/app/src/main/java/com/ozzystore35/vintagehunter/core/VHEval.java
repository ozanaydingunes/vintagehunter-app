package com.ozzystore35.vintagehunter.core;

import com.ozzystore35.vintagehunter.core.VHModel.Category;
import com.ozzystore35.vintagehunter.core.VHModel.Config;
import com.ozzystore35.vintagehunter.core.VHModel.Evaluation;
import com.ozzystore35.vintagehunter.core.VHModel.Search;
import com.ozzystore35.vintagehunter.core.VHModel.SearchMatch;

/**
 * Tek kural motoru: servis (bildirim kararı), veritabanı (yeniden değerlendirme) ve arayüz aynı sonucu kullanır.
 *
 * Bir ilan bir arama için "uygun" (inLimit) sayılırsa:
 *   1. arama aktif, platform aramada ve ayarlarda açık,
 *   2. aramanın anahtar kelimelerinden biri tam kelime olarak geçiyor, hariç kelimelerin hiçbiri geçmiyor,
 *   3. fiyat okunmuş (OK, TL, tek ilan),
 *   4. max(arama.min, kategori.min) ≤ fiyat ≤ min(arama.max, kategori.max)   — sınırlar dahil,
 *   5. aramada minimum fırsat oranı varsa: piyasa değeri biliniyor ve oran ≥ minimum.
 */
public final class VHEval {
    private VHEval() {}

    /**
     * @param listingMarketValue ilana özel girilmiş piyasa değeri (null olabilir); yoksa aramanın değeri kullanılır.
     */
    public static Evaluation evaluate(String platform, String text, VHPrice.Parsed price, Double listingMarketValue, Config cfg) {
        Evaluation ev = new Evaluation();
        String hay = VHText.fold(text);
        for (String g : cfg.settings.globalExclude) if (VHText.hasPhrase(hay, g)) { ev.reason = "EXCLUDED"; return ev; }

        SearchMatch best = null;
        for (Search s : cfg.searches) {
            if (!s.active) continue;
            if (!s.platforms.isEmpty() && !s.platforms.contains(platform)) continue;
            String term = null;
            for (String k : s.keywords) {
                if (VHText.hasKeyword(hay, k)) {
                    if (term == null || VHText.fold(k).length() > VHText.fold(term).length()) term = k;
                }
            }
            if (term == null) continue;
            boolean excluded = false;
            for (String x : s.exclude) if (VHText.hasPhrase(hay, x)) { excluded = true; break; }
            if (excluded) continue;

            Category c = cfg.category(s.categoryId);
            SearchMatch m = new SearchMatch();
            m.searchId = s.id;
            m.categoryId = s.categoryId;
            m.term = term;
            m.notify = s.notify;
            m.effMin = Math.max(nz(s.minPrice), c == null ? 0 : nz(c.minPrice));
            m.effMax = minNullable(s.maxPrice, c == null ? null : c.maxPrice);

            if (price == null || !VHPrice.OK.equals(price.status) || price.price == null) {
                String st = price == null ? VHPrice.MISSING : price.status;
                m.reason = VHPrice.AGGREGATE.equals(st) ? "AGGREGATE"
                        : VHPrice.AMBIGUOUS.equals(st) ? "PRICE_AMBIGUOUS"
                        : VHPrice.FOREIGN.equals(st) ? "PRICE_FOREIGN" : "PRICE_MISSING";
            } else if (m.effMax != null && price.price > m.effMax) {
                m.reason = "OVER_MAX";
            } else if (price.price < m.effMin) {
                m.reason = "UNDER_MIN";
            } else {
                Double mv = listingMarketValue != null ? listingMarketValue : s.marketValue;
                Double pct = dealPct(mv, price.price);
                if (s.minDealPct != null && s.minDealPct > 0) {
                    if (pct == null) m.reason = "DEAL_UNKNOWN";
                    else if (pct < s.minDealPct) m.reason = "DEAL_LOW";
                }
                m.inLimit = m.reason == null;
            }
            ev.matches.add(m);
            if (best == null || (m.inLimit && !best.inLimit)) best = m;
        }
        if (best == null) { ev.reason = "NO_MATCH"; return ev; }

        ev.matched = true;
        ev.inLimit = best.inLimit;
        ev.searchId = best.searchId;
        ev.categoryId = best.categoryId;
        ev.term = best.term;
        ev.reason = best.reason;

        Search bs = cfg.search(best.searchId);
        ev.marketValue = listingMarketValue != null ? listingMarketValue : (bs == null ? null : bs.marketValue);
        if (price != null && price.ok()) {
            ev.dealPct = dealPct(ev.marketValue, price.price);
            ev.tier = tier(ev.dealPct, cfg.settings);
        }

        if (ev.inLimit) {
            boolean instant = false, digest = false;
            for (SearchMatch m : ev.matches) {
                if (!m.inLimit) continue;
                if ("INSTANT".equals(m.notify)) instant = true;
                else if ("DIGEST".equals(m.notify)) digest = true;
            }
            boolean high = ev.dealPct != null && ev.dealPct >= cfg.settings.tierHigh;
            if (instant && high && cfg.settings.notifyPriorityHigh) ev.alert = "HIGH_DEAL";
            else if (instant && cfg.settings.notifyInstant) ev.alert = "MATCH";
            else if (instant || digest) ev.alert = "DIGEST";
        }
        return ev;
    }

    public static Double dealPct(Double marketValue, Double price) {
        if (marketValue == null || price == null || marketValue <= 0) return null;
        return (marketValue - price) / marketValue * 100.0;
    }

    public static String tier(Double pct, VHModel.Settings s) {
        if (pct == null) return null;
        if (pct >= s.tierVeryHigh) return "VERY_HIGH";
        if (pct >= s.tierHigh) return "HIGH";
        if (pct >= s.tierMedium) return "MEDIUM";
        return "LOW";
    }

    private static double nz(Double d) { return d == null ? 0 : d; }

    private static Double minNullable(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    /** Sessiz saatte mi? (saat 0-23). quietStart=-1 ise hiç sessiz değil. */
    public static boolean quiet(VHModel.Settings s, int hour) {
        if (s.quietStart < 0 || s.quietEnd < 0 || s.quietStart == s.quietEnd) return false;
        return s.quietStart < s.quietEnd ? (hour >= s.quietStart && hour < s.quietEnd) : (hour >= s.quietStart || hour < s.quietEnd);
    }
}
