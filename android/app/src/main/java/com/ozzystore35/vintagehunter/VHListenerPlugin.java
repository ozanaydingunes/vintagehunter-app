package com.ozzystore35.vintagehunter;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Web arayüzü ↔ bildirim yakalayıcı köprüsü. JS: Capacitor.Plugins.VHListener */
@CapacitorPlugin(name = "VHListener")
public class VHListenerPlugin extends Plugin {

    private Context ctx() { return getContext(); }

    @PluginMethod
    public void status(PluginCall call) {
        JSObject r = new JSObject();
        r.put("enabled", NotificationManagerCompat.getEnabledListenerPackages(ctx()).contains(ctx().getPackageName()));
        r.put("notificationsAllowed", NotificationManagerCompat.from(ctx()).areNotificationsEnabled());
        r.put("count", VHStore.list(ctx()).length());
        r.put("sdk", Build.VERSION.SDK_INT);
        call.resolve(r);
    }

    @PluginMethod
    public void openListenerSettings(PluginCall call) {
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(ctx(), VHListenerService.class).flattenToString());
        } else {
            i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { ctx().startActivity(i); }
        catch (Exception e) {
            Intent f = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            f.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx().startActivity(f);
        }
        call.resolve();
    }

    @PluginMethod
    public void openAppDetails(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void setConfig(PluginCall call) {
        JSObject cfg = call.getData();
        VHStore.saveConfig(ctx(), cfg.toString());
        VHDigestReceiver.schedule(ctx(), cfg.optBoolean("digestOn", true));
        VHListenerService.ensureChannels(ctx());
        call.resolve();
    }

    @PluginMethod
    public void getCaptures(PluginCall call) {
        JSObject r = new JSObject();
        try { r.put("items", new JSArray(VHStore.list(ctx()).toString())); }
        catch (Exception e) { r.put("items", new JSArray()); }
        call.resolve(r);
    }

    @PluginMethod
    public void clearCaptures(PluginCall call) { VHStore.clear(ctx()); call.resolve(); }

    @PluginMethod
    public void openCapture(PluginCall call) {
        String id = call.getString("id", "");
        String pkg = call.getString("pkg", "");
        PendingIntent pi;
        synchronized (VHListenerService.INTENTS) { pi = VHListenerService.INTENTS.get(id); }
        if (pi != null) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions o = ActivityOptions.makeBasic();
                    o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    pi.send(ctx(), 0, null, null, null, null, o.toBundle());
                } else {
                    pi.send();
                }
                JSObject r = new JSObject(); r.put("opened", "listing"); call.resolve(r); return;
            } catch (Exception ignored) { }
        }
        Intent li = ctx().getPackageManager().getLaunchIntentForPackage(pkg);
        if (li != null) {
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx().startActivity(li);
            JSObject r = new JSObject(); r.put("opened", "app"); call.resolve(r);
        } else {
            call.reject("Platform uygulaması bu telefonda bulunamadı");
        }
    }

    /** Uygulama içinden örnek metinle eşleştiriciyi denemek için. */
    @PluginMethod
    public void test(PluginCall call) {
        VHMatcher.Result m = VHMatcher.match(call.getString("text", ""), VHStore.rules(ctx()));
        JSObject r = new JSObject();
        r.put("status", m.status); r.put("term", m.term); r.put("groupName", m.groupName); r.put("price", m.price);
        call.resolve(r);
    }

    @PluginMethod
    public void takeRoute(PluginCall call) {
        JSObject r = new JSObject();
        r.put("route", VHStore.takePendingRoute(ctx()));
        call.resolve(r);
    }

    @PluginMethod
    public void digestNow(PluginCall call) {
        try { VHDigestReceiver.post(ctx()); call.resolve(); } catch (Exception e) { call.reject(e.getMessage()); }
    }
}
