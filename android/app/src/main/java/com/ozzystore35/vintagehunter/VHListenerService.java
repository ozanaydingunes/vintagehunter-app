package com.ozzystore35.vintagehunter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dolap, Gardrops, Sahibinden ve Letgo uygulamalarının telefona gönderdiği bildirimleri okur,
 * marka/fiyat kurallarıyla eşleştirir, kaydeder ve eşleşmede alarm bildirimi çıkarır.
 * Hiçbir siteye istek göndermez; yalnızca cihazdaki bildirimlerle çalışır.
 */
public class VHListenerService extends NotificationListenerService {

    public static final String CH_ALARM = "vh_alarm";
    public static final String CH_DIGEST = "vh_digest";

    /** Orijinal ilanı açmak için platformun kendi bağlantısı (bellekte, son 200). */
    public static final Map<String, PendingIntent> INTENTS = new LinkedHashMap<String, PendingIntent>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, PendingIntent> e) { return size() > 200; }
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try { handle(sbn); } catch (Exception ignored) { }
    }

    private void handle(StatusBarNotification sbn) throws Exception {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName())) return;
        String platform = VHMatcher.platformFor(pkg, VHStore.extraPackages(this));
        if (platform == null) return; // diğer uygulamaların bildirimlerine dokunulmaz

        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle ex = n.extras;
        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
        String sub = str(ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
        StringBuilder lines = new StringBuilder();
        CharSequence[] tl = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (tl != null) for (CharSequence l : tl) lines.append(' ').append(l);
        String body = big.length() > text.length() ? big : text;
        String full = (title + " " + body + " " + sub + lines).trim();
        if (full.isEmpty()) return;

        String key = pkg + "|" + full.hashCode();
        if (VHStore.seenBefore(this, key)) return;

        VHMatcher.Result r = VHMatcher.match(full, VHStore.rules(this));
        String id = Long.toString(System.currentTimeMillis(), 36) + Integer.toHexString((int) (Math.random() * 0xffff));

        JSONObject c = new JSONObject();
        c.put("id", id);
        c.put("pkg", pkg);
        c.put("platform", platform);
        c.put("title", title);
        c.put("text", (body + " " + lines).trim());
        c.put("status", r.status);
        c.put("group", r.group == null ? JSONObject.NULL : r.group);
        c.put("groupName", r.groupName == null ? JSONObject.NULL : r.groupName);
        c.put("term", r.term == null ? JSONObject.NULL : r.term);
        c.put("price", r.price == null ? JSONObject.NULL : r.price);
        c.put("at", System.currentTimeMillis());
        VHStore.add(this, c);
        if (n.contentIntent != null) synchronized (INTENTS) { INTENTS.put(id, n.contentIntent); }

        boolean alarmOn = VHStore.config(this).optBoolean("alarmOn", true);
        boolean alertNoPrice = VHStore.config(this).optBoolean("alertNoPrice", true);
        if (alarmOn && ("MATCH".equals(r.status) || ("NO_PRICE".equals(r.status) && alertNoPrice))) {
            postAlarm(this, id, platform, title, body, r, n.contentIntent);
        }
    }

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel a = new NotificationChannel(CH_ALARM, "Fırsat alarmı", NotificationManager.IMPORTANCE_HIGH);
        a.setDescription("Markanız ve fiyat sınırınızla eşleşen yeni ilan");
        a.enableVibration(true);
        a.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
        nm.createNotificationChannel(a);
        NotificationChannel d = new NotificationChannel(CH_DIGEST, "Saatlik özet", NotificationManager.IMPORTANCE_DEFAULT);
        d.setDescription("Son 1 saatte yakalanan ilanların kategori özeti");
        nm.createNotificationChannel(d);
    }

    static PendingIntent openApp(Context c, String route, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("vh_route", route);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void postAlarm(Context c, String id, String platform, String title, String body,
                                  VHMatcher.Result r, PendingIntent original) {
        ensureChannels(c);
        String price = r.price == null ? "fiyat görünmüyor"
                : String.format(Locale.forLanguageTag("tr-TR"), "%,.0f TL", r.price);
        String head = (r.term != null ? r.term : platform) + " · " + price;
        String line = platform + " · " + (r.groupName != null ? r.groupName : "") + "\n" + (title + " " + body).trim();
        int nid = id.hashCode();
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_ALARM)
                .setSmallIcon(R.drawable.ic_stat_vh)
                .setContentTitle(("MATCH".equals(r.status) ? "Fırsat: " : "Yeni ilan: ") + head)
                .setContentText(line)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(line))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true)
                .setContentIntent(original != null ? original : openApp(c, "avci", nid))
                .addAction(0, "Listede gör", openApp(c, "avci", nid + 1));
        try { NotificationManagerCompat.from(c).notify(nid, b.build()); } catch (SecurityException ignored) { }
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString(); }
}
