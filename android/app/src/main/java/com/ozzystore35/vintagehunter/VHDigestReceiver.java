package com.ozzystore35.vintagehunter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saat başı: son 1 saatte yakalanan ilanların kategori özeti + o saatin tarama grubu. */
public class VHDigestReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        try { post(c); } catch (Exception ignored) { }
    }

    static void post(Context c) throws Exception {
        JSONObject cfg = VHStore.config(c);
        if (!cfg.optBoolean("digestOn", true)) return;
        int start = cfg.optInt("startHour", 9), end = cfg.optInt("endHour", 22);
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        if (now.get(Calendar.MINUTE) >= 30) hour = (hour + 1) % 24; // erken tetiklenirse gelecek saati say
        if (hour < start || hour > end) return;

        long since = System.currentTimeMillis() - 60L * 60 * 1000;
        JSONArray all = VHStore.list(c);
        Map<String, Integer> counts = new LinkedHashMap<>();
        int total = 0, noBrand = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.getJSONObject(i);
            if (o.optLong("at") < since) break; // liste yeniden eskiye
            String st = o.optString("status");
            if ("MATCH".equals(st) || "NO_PRICE".equals(st)) {
                String g = o.optString("groupName", "Diğer");
                counts.put(g, counts.containsKey(g) ? counts.get(g) + 1 : 1);
                total++;
            } else noBrand++;
        }
        StringBuilder sb = new StringBuilder();
        if (total == 0) sb.append("Son 1 saatte eşleşen yeni ilan yok");
        else for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(e.getKey()).append(": ").append(e.getValue());
        }
        if (noBrand > 0) sb.append(" (+").append(noBrand).append(" diğer)");
        JSONArray groups = cfg.optJSONArray("hourGroups");
        String slot = groups != null && hour - start < groups.length() ? groups.optString(hour - start) : null;
        if (slot != null && !slot.isEmpty()) sb.append("\nBu saatin taraması: ").append(slot);

        VHListenerService.ensureChannels(c);
        String title = String.format("%02d:00 · %s", hour, total == 0 ? "özet" : total + " eşleşen ilan");
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, VHListenerService.CH_DIGEST)
                .setSmallIcon(R.drawable.ic_stat_vh)
                .setContentTitle(title)
                .setContentText(sb.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(sb.toString()))
                .setAutoCancel(true)
                .setContentIntent(VHListenerService.openApp(c, total > 0 ? "avci" : "tarama", 4242));
        try { NotificationManagerCompat.from(c).notify(4242, b.build()); } catch (SecurityException ignored) { }
    }

    /** Her saatin başına kurar (yaklaşık; Android pil tasarrufu birkaç dakika kaydırabilir). */
    static void schedule(Context c, boolean on) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(c, VHDigestReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 77, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        if (!on) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.MINUTE, 0); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.HOUR_OF_DAY, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_HOUR, pi);
    }
}
