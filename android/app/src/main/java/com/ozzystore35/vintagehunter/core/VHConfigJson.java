package com.ozzystore35.vintagehunter.core;

import com.ozzystore35.vintagehunter.core.VHModel.Category;
import com.ozzystore35.vintagehunter.core.VHModel.Config;
import com.ozzystore35.vintagehunter.core.VHModel.Search;
import com.ozzystore35.vintagehunter.core.VHModel.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Ayarların JSON ↔ Java dönüşümü. Android org.json ve JSON-java ile uyumlu (açık null yazılmaz). */
public final class VHConfigJson {
    private VHConfigJson() {}

    public static Config parse(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        Config c = new Config();
        JSONArray cats = o.optJSONArray("categories");
        for (int i = 0; cats != null && i < cats.length(); i++) {
            JSONObject j = cats.getJSONObject(i);
            Category k = new Category();
            k.id = j.getString("id");
            k.name = j.optString("name", k.id);
            k.maxPrice = optD(j, "maxPrice");
            k.minPrice = optD(j, "minPrice");
            c.categories.add(k);
        }
        JSONArray ss = o.optJSONArray("searches");
        for (int i = 0; ss != null && i < ss.length(); i++) {
            JSONObject j = ss.getJSONObject(i);
            Search s = new Search();
            s.id = j.getString("id");
            s.name = j.optString("name", s.id);
            s.categoryId = j.optString("categoryId", "");
            s.keywords = strs(j.optJSONArray("keywords"));
            s.exclude = strs(j.optJSONArray("exclude"));
            s.platforms = strs(j.optJSONArray("platforms"));
            s.minPrice = optD(j, "minPrice");
            s.maxPrice = optD(j, "maxPrice");
            s.minDealPct = optD(j, "minDealPct");
            s.marketValue = optD(j, "marketValue");
            s.notify = j.optString("notify", "INSTANT");
            s.active = j.optBoolean("active", true);
            c.searches.add(s);
        }
        JSONObject st = o.optJSONObject("settings");
        Settings s = c.settings;
        if (st != null) {
            JSONObject pe = st.optJSONObject("platformEnabled");
            if (pe != null) {
                Iterator<String> it = pe.keys();
                while (it.hasNext()) { String k = it.next(); s.platformEnabled.put(k, pe.optBoolean(k, true)); }
            }
            s.tierVeryHigh = st.optDouble("tierVeryHigh", 60);
            s.tierHigh = st.optDouble("tierHigh", 45);
            s.tierMedium = st.optDouble("tierMedium", 25);
            s.notifyInstant = st.optBoolean("notifyInstant", true);
            s.notifyPriorityHigh = st.optBoolean("notifyPriorityHigh", true);
            s.notifyPriceDrop = st.optBoolean("notifyPriceDrop", true);
            s.digestMinutes = st.optInt("digestMinutes", 30);
            s.digestStartHour = st.optInt("digestStartHour", 9);
            s.digestEndHour = st.optInt("digestEndHour", 22);
            s.quietStart = st.optInt("quietStart", -1);
            s.quietEnd = st.optInt("quietEnd", -1);
            s.globalExclude = strs(st.optJSONArray("globalExclude"));
        }
        return c;
    }

    private static Double optD(JSONObject j, String k) {
        if (!j.has(k) || j.isNull(k)) return null;
        Object v = j.opt(k);
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            String t = ((String) v).trim();
            if (t.isEmpty()) return null;
            Double d = VHPrice.toNumber(t);
            if (d != null) return d;
            try { return Double.parseDouble(t); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static List<String> strs(JSONArray a) {
        List<String> out = new ArrayList<>();
        for (int i = 0; a != null && i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** Ayar doğrulaması: arayüzden gelen hatalı değeri kaydetmeden önce yakalar. Hata yoksa null. */
    public static String validate(Config c) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Category k : c.categories) {
            if (k.id == null || k.id.isEmpty()) return "Kategori kimliği boş";
            if (!ids.add("c:" + k.id)) return "Aynı kategori iki kez tanımlı: " + k.name;
            if (k.maxPrice != null && k.maxPrice < 0) return k.name + ": üst sınır negatif olamaz";
            if (k.minPrice != null && k.maxPrice != null && k.minPrice > k.maxPrice) return k.name + ": alt sınır üst sınırdan büyük";
        }
        for (Search s : c.searches) {
            if (!ids.add("s:" + s.id)) return "Aynı arama iki kez tanımlı: " + s.name;
            if (s.keywords.isEmpty()) return s.name + ": en az bir anahtar kelime gerekli";
            if (c.category(s.categoryId) == null) return s.name + ": kategori bulunamadı";
            if (s.minPrice != null && s.maxPrice != null && s.minPrice > s.maxPrice) return s.name + ": en düşük fiyat en yüksekten büyük";
            if (s.minDealPct != null && (s.minDealPct < 0 || s.minDealPct > 100)) return s.name + ": fırsat oranı %0–100 olmalı";
        }
        Settings st = c.settings;
        if (!(st.tierVeryHigh >= st.tierHigh && st.tierHigh >= st.tierMedium && st.tierMedium >= 0 && st.tierVeryHigh <= 100)) {
            return "Fırsat eşikleri sırası: çok yüksek ≥ yüksek ≥ orta ≥ 0";
        }
        if (st.digestMinutes != 0 && st.digestMinutes != 30 && st.digestMinutes != 60) return "Özet sıklığı 0, 30 veya 60 olmalı";
        return null;
    }
}
