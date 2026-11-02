package com.ozzystore35.vintagehunter;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONObject;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Yalnızca hata ayıklama derlemesinde (uçtan uca testler için); kullanıcıya giden release APK'da kapalı.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true);
        }
        registerPlugin(VHListenerPlugin.class);
        handle(getIntent());
        super.onCreate(savedInstanceState);
        applyChrome(false);
        VHNotifier.ensureChannels(this);
        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            VHMigration.run(app);
            VHDigestReceiver.schedule(app);
        }).start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
        VHListenerPlugin.pendingChanged();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Bazı üreticiler uygulamayı durdurunca dinleyici bağlantısı kopar; izin varsa yeniden bağlan.
        if (!VHListenerService.connected
                && NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {
            try { NotificationListenerService.requestRebind(new ComponentName(this, VHListenerService.class)); } catch (Exception ignored) { }
        }
    }

    /** Pencere zemini ve durum çubuğu simgeleri arayüz temasına uysun. */
    void applyChrome(boolean light) {
        int bg = light ? 0xFFF3F0EA : 0xFF0C0C0E;
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        getWindow().getDecorView().setBackgroundColor(bg);
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(light);
        c.setAppearanceLightNavigationBars(light);
    }

    /** Bildirimden veya paylaş menüsünden gelen yönlendirmeyi arayüz için sakla. */
    private void handle(Intent i) {
        if (i == null) return;
        String listing = i.getStringExtra(VHNotifier.EXTRA_LISTING);
        if (listing != null) {
            VHLinks.harvest(this, listing);       // "Platformda aç" bağlantısını kaybetmeden önce al
            VHNotifier.cancel(this, listing);
            VHConfigStore.putStr(this, "pending_route", "listing/" + listing);
            return;
        }
        String route = i.getStringExtra(VHNotifier.EXTRA_ROUTE);
        if (route != null) {
            VHConfigStore.putStr(this, "pending_route", route);
            return;
        }
        if (Intent.ACTION_SEND.equals(i.getAction()) && "text/plain".equals(i.getType())) {
            try {
                JSONObject o = new JSONObject();
                o.put("text", String.valueOf(i.getStringExtra(Intent.EXTRA_TEXT)));
                o.put("subject", String.valueOf(i.getStringExtra(Intent.EXTRA_SUBJECT)));
                VHConfigStore.putStr(this, "pending_share", o.toString());
                VHConfigStore.putStr(this, "pending_route", "share");
            } catch (Exception ignored) { }
        }
    }
}
