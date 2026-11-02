package com.ozzystore35.vintagehunter;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bildirimi gönderen uygulamanın hangi platform olduğunu bulur (paket adı veya uygulama adı ile). */
public final class VHPlatforms {
    private static final Map<String, String> CACHE = new HashMap<>();
    private static final String NONE = "";

    private VHPlatforms() {}

    static String byName(String s) {
        String p = s == null ? "" : s.toLowerCase(Locale.ROOT);
        if (p.contains("dolap")) return "Dolap";
        if (p.contains("gardrops")) return "Gardrops";
        if (p.contains("sahibinden")) return "Sahibinden";
        if (p.contains("letgo") || p.contains("ambatana")) return "Letgo";
        return null;
    }

    /** Paket bir platform uygulamasıysa platform adı, değilse null. Sonuç önbelleğe alınır. */
    public static synchronized String forPackage(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return null;
        String cached = CACHE.get(pkg);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String p = byName(pkg);
        if (p == null) {
            try {
                PackageManager pm = c.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                p = byName(String.valueOf(pm.getApplicationLabel(ai)));
            } catch (Exception ignored) { }
        }
        CACHE.put(pkg, p == null ? NONE : p);
        return p;
    }

    /** Telefonda yüklü platform uygulamaları: platform → paket adı. */
    public static Map<String, String> installed(Context c) {
        Map<String, String> out = new HashMap<>();
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(i, 0);
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            String p = byName(pkg);
            if (p == null) p = byName(String.valueOf(r.loadLabel(pm)));
            if (p != null && !out.containsKey(p)) out.put(p, pkg);
        }
        return out;
    }

    public static boolean launch(Context c, String platform) {
        String pkg = installed(c).get(platform);
        if (pkg == null) return false;
        Intent li = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (li == null) return false;
        li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(li);
        return true;
    }

    /** Paylaşılan bağlantının alan adından platform. */
    public static String fromUrl(String url) {
        String p = byName(url);
        return p == null ? "Diğer" : p;
    }
}
