package com.ozzystore35.vintagehunter;

import android.content.Context;
import android.content.SharedPreferences;

import com.ozzystore35.vintagehunter.core.VHConfigJson;
import com.ozzystore35.vintagehunter.core.VHModel;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Ayarlar (kategoriler, kayıtlı aramalar, bildirim tercihleri). Servis ve arayüz aynı kaynağı okur. */
public final class VHConfigStore {
    private static final String PREFS = "vh_cfg";
    private static final String KEY = "config_v2";
    private static Context app;
    private static String cachedJson;
    private static VHModel.Config cached;

    private VHConfigStore() {}

    static SharedPreferences prefs(Context c) {
        if (c != null) app = c.getApplicationContext();
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized String json(Context c) {
        String j = prefs(c).getString(KEY, null);
        if (j == null) {
            j = defaults(c);
            prefs(c).edit().putString(KEY, j).apply();
        }
        return j;
    }

    public static synchronized VHModel.Config get(Context c) {
        String j = json(c);
        if (j.equals(cachedJson) && cached != null) return cached;
        try {
            cached = VHConfigJson.parse(j);
        } catch (Exception e) {
            try { cached = VHConfigJson.parse(defaults(c)); } catch (Exception e2) { cached = new VHModel.Config(); }
        }
        cachedJson = j;
        return cached;
    }

    /** Doğrular ve kaydeder. Hata varsa mesaj döner, hiçbir şey kaydedilmez. */
    public static synchronized String set(Context c, String json) {
        VHModel.Config cfg;
        try {
            cfg = VHConfigJson.parse(json);
        } catch (Exception e) {
            return "Ayar okunamadı: " + e.getMessage();
        }
        String err = VHConfigJson.validate(cfg);
        if (err != null) return err;
        prefs(c).edit().putString(KEY, json).commit();
        cached = cfg;
        cachedJson = json;
        return null;
    }

    public static String defaults(Context c) {
        try (InputStream in = c.getResources().openRawResource(R.raw.vh_default_config)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{\"categories\":[],\"searches\":[],\"settings\":{}}";
        }
    }

    public static long getLong(Context c, String k) { return prefs(c).getLong(k, 0); }

    public static void putLong(Context c, String k, long v) { prefs(c).edit().putLong(k, v).apply(); }

    public static String getStr(Context c, String k) { return prefs(c).getString(k, null); }

    public static void putStr(Context c, String k, String v) {
        if (v == null) prefs(c).edit().remove(k).apply(); else prefs(c).edit().putString(k, v).apply();
    }
}
