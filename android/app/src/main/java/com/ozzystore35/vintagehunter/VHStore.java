package com.ozzystore35.vintagehunter;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ayarlar (kurallar) ve yakalanan bildirimler için cihaz içi depolama. */
public final class VHStore {
    private static final String PREFS = "vh_hunter";
    private static final String FILE = "captures.json";
    private static final int MAX_CAPTURES = 500;
    private static final Object LOCK = new Object();

    private static String cachedCfg = null;
    private static List<VHMatcher.Rule> cachedRules = new ArrayList<>();

    private VHStore() {}

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static void saveConfig(Context c, String json) { prefs(c).edit().putString("config", json).apply(); }

    public static JSONObject config(Context c) {
        try { return new JSONObject(prefs(c).getString("config", "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    public static synchronized List<VHMatcher.Rule> rules(Context c) {
        String raw = prefs(c).getString("config", "{}");
        if (raw.equals(cachedCfg)) return cachedRules;
        List<VHMatcher.Rule> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(raw).optJSONArray("rules");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray t = o.getJSONArray("terms");
                String[] terms = new String[t.length()];
                for (int j = 0; j < t.length(); j++) terms[j] = t.getString(j);
                out.add(new VHMatcher.Rule(o.optString("group"), o.optString("groupName"), o.optString("kind"),
                        terms, o.optDouble("min", 0), o.optDouble("max", Double.MAX_VALUE)));
            }
        } catch (Exception ignored) { }
        cachedCfg = raw; cachedRules = out;
        return out;
    }

    public static List<String> extraPackages(Context c) {
        List<String> out = new ArrayList<>();
        JSONArray a = config(c).optJSONArray("extraPackages");
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optString(i));
        return out;
    }

    /** Aynı bildirim tekrar gelirse (güncelleme) ikinci kez kaydetme. */
    public static boolean seenBefore(Context c, String key) {
        synchronized (LOCK) {
            SharedPreferences p = prefs(c);
            LinkedHashSet<String> keys = new LinkedHashSet<>();
            try {
                JSONArray a = new JSONArray(p.getString("seen", "[]"));
                for (int i = 0; i < a.length(); i++) keys.add(a.getString(i));
            } catch (Exception ignored) { }
            if (keys.contains(key)) return true;
            keys.add(key);
            List<String> list = new ArrayList<>(keys);
            if (list.size() > 300) list = list.subList(list.size() - 300, list.size());
            p.edit().putString("seen", new JSONArray(list).toString()).apply();
            return false;
        }
    }

    public static JSONArray list(Context c) {
        synchronized (LOCK) {
            File f = new File(c.getFilesDir(), FILE);
            if (!f.exists()) return new JSONArray();
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0;
                while (off < b.length) { int n = in.read(b, off, b.length - off); if (n < 0) break; off += n; }
                return new JSONArray(new String(b, 0, off, StandardCharsets.UTF_8));
            } catch (Exception e) { return new JSONArray(); }
        }
    }

    public static void add(Context c, JSONObject capture) {
        synchronized (LOCK) {
            JSONArray old = list(c);
            JSONArray out = new JSONArray();
            out.put(capture);
            for (int i = 0; i < old.length() && out.length() < MAX_CAPTURES; i++) out.put(old.opt(i));
            write(c, out);
        }
    }

    public static void clear(Context c) { synchronized (LOCK) { write(c, new JSONArray()); } }

    private static void write(Context c, JSONArray arr) {
        File f = new File(c.getFilesDir(), FILE);
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    public static void setPendingRoute(Context c, String route) { prefs(c).edit().putString("route", route).apply(); }

    public static String takePendingRoute(Context c) {
        String r = prefs(c).getString("route", null);
        if (r != null) prefs(c).edit().remove("route").apply();
        return r;
    }
}
