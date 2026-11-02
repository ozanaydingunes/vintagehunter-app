package com.ozzystore35.vintagehunter;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * İlanın orijinal sayfasını açma.
 *
 * Bildirimden gelen ilanlarda platform bize adres vermez; yalnızca kendi bildiriminin "aç" bağlantısını
 * (PendingIntent) verir. Bu bağlantı sırasıyla şuralarda aranır:
 *   1. bellek (uygulama açıkken),
 *   2. uygulamanın kendi bildirimindeki "Platformda aç" düğmesi (bildirim duruyorsa, uygulama kapanmış olsa bile),
 *   3. platformun kendi bildirimi (bildirim çubuğunda hâlâ duruyorsa).
 * Hiçbiri yoksa bağlantı "süresi doldu" olarak işaretlenir ve platform uygulaması açılır.
 * Paylaş menüsüyle eklenen ilanlarda gerçek adres saklanır ve doğrudan açılır.
 */
public final class VHLinks {
    private static final Map<String, PendingIntent> MEM = new LinkedHashMap<String, PendingIntent>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, PendingIntent> e) { return size() > 300; }
    };

    private VHLinks() {}

    public static void remember(String listingId, PendingIntent pi) {
        synchronized (MEM) { MEM.put(listingId, pi); }
    }

    /** Uygulamanın kendi bildirimindeki "Platformda aç" bağlantısını belleğe alır. */
    public static void harvest(Context c, String listingId) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            int nid = VHNotifier.idFor(listingId);
            for (StatusBarNotification s : nm.getActiveNotifications()) {
                if (s.getId() != nid) continue;
                Notification.Action[] acts = s.getNotification().actions;
                if (acts == null) continue;
                for (Notification.Action a : acts) {
                    if ("Platformda aç".contentEquals(a.title) && a.actionIntent != null) remember(listingId, a.actionIntent);
                }
            }
        } catch (Exception ignored) { }
    }

    private static PendingIntent find(Context c, JSONObject r) {
        String id = r.optString("id");
        synchronized (MEM) { if (MEM.containsKey(id)) return MEM.get(id); }
        harvest(c, id);
        synchronized (MEM) { if (MEM.containsKey(id)) return MEM.get(id); }
        String key = r.optString("notif_key", "");
        VHListenerService svc = VHListenerService.instance;
        if (!key.isEmpty() && svc != null) {
            try {
                StatusBarNotification[] arr = svc.getActiveNotifications(new String[]{key});
                if (arr != null && arr.length > 0 && arr[0].getNotification().contentIntent != null) {
                    PendingIntent pi = arr[0].getNotification().contentIntent;
                    remember(id, pi);
                    return pi;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** @return "url", "listing", "app", "none" */
    public static String open(Context c, String listingId) {
        VHDb db = VHDb.get(c);
        JSONObject r = db.byId(listingId);
        if (r == null) return "none";
        String url = r.optString("url", "");
        if (!url.isEmpty()) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return "url";
            } catch (Exception ignored) { }
        }
        PendingIntent pi = find(c, r);
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions o = ActivityOptions.makeBasic();
                    o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    Bundle opts = o.toBundle();
                    pi.send(c, 0, null, null, null, null, opts);
                } else {
                    pi.send();
                }
                setLink(db, listingId, "LIVE");
                return "listing";
            } catch (PendingIntent.CanceledException e) {
                synchronized (MEM) { MEM.remove(listingId); }
            }
        }
        setLink(db, listingId, "EXPIRED");
        return VHPlatforms.launch(c, r.optString("platform", "")) ? "app" : "none";
    }

    private static void setLink(VHDb db, String id, String state) {
        ContentValues v = new ContentValues();
        v.put("link_state", state);
        db.update(id, v);
    }

    /** Kayıt için bağlantı hâlâ açılabilir mi (göndermeden kontrol). */
    public static boolean available(Context c, JSONObject r) {
        if (!r.optString("url", "").isEmpty()) return true;
        return find(c, r) != null;
    }
}
