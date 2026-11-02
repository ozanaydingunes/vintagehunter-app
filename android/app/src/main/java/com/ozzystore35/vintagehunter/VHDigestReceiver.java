package com.ozzystore35.vintagehunter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ozzystore35.vintagehunter.core.VHEval;
import com.ozzystore35.vintagehunter.core.VHModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 30/60 dakikalık özet. YALNIZCA dönemde en az bir yeni uygun ilan varsa gönderilir
 * (1.2'de "yeni ilan yok" özeti de gönderiliyordu; bu, ilansız bildirim karmaşasının nedenlerinden biriydi).
 */
public class VHDigestReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent intent) {
        try { run(c); } catch (Exception ignored) { }
        schedule(c);
    }

    static void run(Context c) {
        VHModel.Config cfg = VHConfigStore.get(c);
        VHModel.Settings s = cfg.settings;
        if (s.digestMinutes <= 0) return;
        Calendar now = Calendar.getInstance();
        int h = now.get(Calendar.HOUR_OF_DAY);
        if (h < s.digestStartHour || h > s.digestEndHour) return;
        if (VHEval.quiet(s, h)) return;
        long last = VHConfigStore.getLong(c, "digest_last");
        long since = Math.max(last, System.currentTimeMillis() - s.digestMinutes * 60_000L - 120_000L);
        JSONArray items = VHDb.get(c).inLimitSince(since);
        VHConfigStore.putLong(c, "digest_last", System.currentTimeMillis());
        if (items.length() == 0) return;
        Map<String, Integer> byCat = new LinkedHashMap<>();
        double best = -1;
        for (int i = 0; i < items.length(); i++) {
            JSONObject r = items.optJSONObject(i);
            VHModel.Category k = cfg.category(r.optString("category_id", ""));
            String name = k == null ? "Diğer" : k.name;
            byCat.put(name, byCat.containsKey(name) ? byCat.get(name) + 1 : 1);
            if (!r.isNull("deal_pct")) best = Math.max(best, r.optDouble("deal_pct"));
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Integer> e : byCat.entrySet()) {
            if (b.length() > 0) b.append(" · ");
            b.append(e.getKey()).append(": ").append(e.getValue());
        }
        if (best >= 0) b.append("\nEn iyi fırsat: %").append(Math.round(best));
        VHNotifier.digest(c, items.length() + " yeni uygun ilan · son " + s.digestMinutes + " dk", b.toString());
    }

    /** Bir sonraki yarım/tam saate kurar. Tek seferlik alarm; her çalışmada yeniden kurulur. */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(c, 77, new Intent(c, VHDigestReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        int every = VHConfigStore.get(c).settings.digestMinutes;
        if (every <= 0) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        int m = next.get(Calendar.MINUTE);
        if (every == 30 && m < 30) next.set(Calendar.MINUTE, 30);
        else { next.set(Calendar.MINUTE, 0); next.add(Calendar.HOUR_OF_DAY, 1); }
        am.setWindow(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), 5 * 60_000L, pi);
        VHConfigStore.putLong(c, "digest_next", next.getTimeInMillis());
    }
}
