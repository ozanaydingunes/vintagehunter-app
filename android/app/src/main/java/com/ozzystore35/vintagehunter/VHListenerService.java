package com.ozzystore35.vintagehunter;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.ozzystore35.vintagehunter.core.VHPrice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dolap, Gardrops, Sahibinden ve Letgo uygulamalarının telefona gönderdiği bildirimleri okur.
 * Sistem bu servisi uygulama kapalıyken de çalıştırır. Hiçbir siteye istek atmaz.
 * Diğer uygulamaların bildirimlerine dokunmaz (paket/uygulama adı kontrolü).
 */
public class VHListenerService extends NotificationListenerService {

    static volatile VHListenerService instance;
    static volatile boolean connected;
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();

    @Override
    public void onListenerConnected() {
        instance = this;
        connected = true;
        VHDigestReceiver.schedule(this);
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        instance = null;
        try { requestRebind(new ComponentName(this, VHListenerService.class)); } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        final Context app = getApplicationContext();
        final String pkg = sbn.getPackageName();
        final String platform;
        try { platform = VHPlatforms.forPackage(app, pkg); } catch (Exception e) { return; }
        if (platform == null) return; // platform uygulaması değil — dokunma
        final Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        final String key = sbn.getKey();
        final long post = sbn.getPostTime();
        WORK.execute(() -> {
            try { handle(app, platform, pkg, key, post, n); } catch (Throwable t) {
                VHDb.get(app).logRaw(platform, pkg, "", "", "Hata: " + t.getClass().getSimpleName() + " " + t.getMessage(), null);
            }
        });
    }

    static void handle(Context app, String platform, String pkg, String key, long post, Notification n) {
        Bundle ex = n.extras;
        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE_BIG));
        if (title.isEmpty()) title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
        String big = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (big.length() > text.length()) text = big;
        String sub = str(ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
        String summary = str(ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT));
        CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);

        // Birden çok ilanı tek bildirimde satır satır gösteren platformlar: her satır ayrı ilan
        if (lines != null && lines.length >= 2) {
            int priced = 0;
            for (CharSequence l : lines) if (VHPrice.parse(str(l)).ok()) priced++;
            if (priced >= 2) {
                for (int i = 0; i < lines.length; i++) {
                    String line = str(lines[i]);
                    if (line.isEmpty()) continue;
                    VHPipeline.process(app, platform, pkg, key + "#" + i, title, line, post, null, n.contentIntent);
                }
                return;
            }
        }
        StringBuilder all = new StringBuilder(text);
        if (lines != null) for (CharSequence l : lines) all.append(" · ").append(l);
        if (!sub.isEmpty() && !all.toString().contains(sub)) all.append(" · ").append(sub);
        if (!summary.isEmpty() && !all.toString().contains(summary)) all.append(" · ").append(summary);
        if (title.isEmpty() && all.length() == 0) return;
        VHPipeline.process(app, platform, pkg, key, title, all.toString().trim(), post, n, n.contentIntent);
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }
}
