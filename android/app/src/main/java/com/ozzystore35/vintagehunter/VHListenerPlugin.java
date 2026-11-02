package com.ozzystore35.vintagehunter;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.ozzystore35.vintagehunter.core.VHConfigJson;
import com.ozzystore35.vintagehunter.core.VHEval;
import com.ozzystore35.vintagehunter.core.VHFilter;
import com.ozzystore35.vintagehunter.core.VHModel;
import com.ozzystore35.vintagehunter.core.VHPrice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Arayüz ↔ yerel katman köprüsü. JS: Capacitor.Plugins.VHListener */
@CapacitorPlugin(name = "VHListener")
public class VHListenerPlugin extends Plugin {

    static volatile VHListenerPlugin instance;

    @Override
    public void load() { instance = this; }

    /** MainActivity yeni bir yönlendirme (bildirim/paylaşım) aldığında arayüze haber verir. */
    static void pendingChanged() {
        VHListenerPlugin p = instance;
        if (p != null) p.notifyListeners("pending", new JSObject());
    }

    private Context ctx() { return getContext(); }

    private static JSObject js(JSONObject o) {
        try { return new JSObject(o == null ? "{}" : o.toString()); } catch (Exception e) { return new JSObject(); }
    }

    private static JSArray jsa(JSONArray a) {
        try { return new JSArray(a == null ? "[]" : a.toString()); } catch (Exception e) { return new JSArray(); }
    }

    // ------------------------------------------------------------------ ayarlar

    @PluginMethod
    public void getConfig(PluginCall call) {
        JSObject r = new JSObject();
        try { r.put("config", new JSObject(VHConfigStore.json(ctx()))); } catch (Exception e) { call.reject(e.getMessage()); return; }
        call.resolve(r);
    }

    @PluginMethod
    public void setConfig(PluginCall call) {
        JSObject cfg = call.getObject("config");
        JSObject r = new JSObject();
        if (cfg == null) { r.put("ok", false); r.put("error", "Ayar boş"); call.resolve(r); return; }
        String err = VHConfigStore.set(ctx(), cfg.toString());
        if (err != null) { r.put("ok", false); r.put("error", err); call.resolve(r); return; }
        int changed = VHDb.get(ctx()).reevaluateAll(VHConfigStore.get(ctx()));
        VHDigestReceiver.schedule(ctx());
        r.put("ok", true);
        r.put("changed", changed);
        call.resolve(r);
    }

    @PluginMethod
    public void resetConfig(PluginCall call) {
        VHConfigStore.set(ctx(), VHConfigStore.defaults(ctx()));
        VHDb.get(ctx()).reevaluateAll(VHConfigStore.get(ctx()));
        getConfig(call);
    }

    /** Arayüz tercihleri (filtre, tema, sıralama): uygulama yeniden açılınca korunur. */
    @PluginMethod
    public void getUi(PluginCall call) {
        JSObject r = new JSObject();
        String s = VHConfigStore.getStr(ctx(), "ui_state");
        try { r.put("ui", s == null ? new JSObject() : new JSObject(s)); } catch (Exception e) { r.put("ui", new JSObject()); }
        call.resolve(r);
    }

    @PluginMethod
    public void setUi(PluginCall call) {
        JSObject ui = call.getObject("ui");
        VHConfigStore.putStr(ctx(), "ui_state", ui == null ? null : ui.toString());
        call.resolve();
    }

    // ------------------------------------------------------------------ durum

    @PluginMethod
    public void status(PluginCall call) {
        Context c = ctx();
        JSObject r = new JSObject();
        r.put("listenerEnabled", NotificationManagerCompat.getEnabledListenerPackages(c).contains(c.getPackageName()));
        r.put("listenerConnected", VHListenerService.connected);
        r.put("notificationsAllowed", NotificationManagerCompat.from(c).areNotificationsEnabled());
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        r.put("batteryUnrestricted", pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName()));
        r.put("sdk", Build.VERSION.SDK_INT);
        r.put("manufacturer", Build.MANUFACTURER);
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            r.put("versionName", pi.versionName);
        } catch (Exception ignored) { }
        r.put("digestNext", VHConfigStore.getLong(c, "digest_next"));
        r.put("migratedCount", VHConfigStore.getLong(c, "migrated_12_count"));
        call.resolve(r);
    }

    @PluginMethod
    public void platforms(PluginCall call) {
        Context c = ctx();
        VHModel.Config cfg = VHConfigStore.get(c);
        Map<String, String> inst = VHPlatforms.installed(c);
        JSArray arr = new JSArray();
        for (String p : VHModel.PLATFORMS) {
            JSObject o = js(VHDb.get(c).platformStats(p));
            o.put("platform", p);
            o.put("enabled", cfg.settings.platformOn(p));
            o.put("installed", inst.containsKey(p));
            o.put("pkg", inst.get(p));
            arr.put(o);
        }
        JSObject r = new JSObject();
        r.put("items", arr);
        call.resolve(r);
    }

    @PluginMethod
    public void home(PluginCall call) {
        Context c = ctx();
        VHDb db = VHDb.get(c);
        VHModel.Config cfg = VHConfigStore.get(c);
        JSObject r = new JSObject();
        r.put("stats", js(db.homeStats(cfg, VHConfigStore.getLong(c, "notif_seen_at"))));
        VHFilter newest = new VHFilter();
        newest.listingsOnly = true;
        r.put("newest", jsa(db.list(newest, 0, 12, "new").optJSONArray("items")));
        VHFilter top = new VHFilter();
        top.inLimitOnly = true;
        top.listingsOnly = true;
        r.put("top", jsa(db.list(top, 0, 12, "deal").optJSONArray("items")));
        r.put("recent", jsa(db.list(new VHFilter(), 0, 10, "viewed").optJSONArray("items")));
        VHFilter agg = new VHFilter();
        agg.kind = "AGGREGATE";
        r.put("aggregates", jsa(db.list(agg, 0, 5, "new").optJSONArray("items")));
        r.put("searchCounts", js(db.searchCounts()));
        call.resolve(r);
    }

    // ------------------------------------------------------------------ ilanlar

    @PluginMethod
    public void list(PluginCall call) {
        VHFilter f = new VHFilter();
        f.q = call.getString("q");
        f.exclude = strList(call.getArray("exclude"));
        f.categories.addAll(strList(call.getArray("categories")));
        f.platforms.addAll(strList(call.getArray("platforms")));
        f.minPrice = call.getDouble("minPrice");
        f.maxPrice = call.getDouble("maxPrice");
        f.minDealPct = call.getDouble("minDealPct");
        f.condition = call.getString("condition");
        Long since = call.getLong("sinceMs");
        f.sinceMs = since == null ? 0 : since;
        f.city = call.getString("city");
        f.favOnly = Boolean.TRUE.equals(call.getBoolean("favOnly", false));
        f.inLimitOnly = Boolean.TRUE.equals(call.getBoolean("inLimitOnly", false));
        f.listingsOnly = Boolean.TRUE.equals(call.getBoolean("listingsOnly", false));
        f.listTag = call.getString("listTag");
        f.kind = call.getString("kind");
        String searchId = call.getString("searchId");
        int offset = call.getInt("offset", 0), limit = call.getInt("limit", 30);
        JSONObject res = VHDb.get(ctx()).list(f, 0, Integer.MAX_VALUE, call.getString("order", "new"));
        JSONArray all = res.optJSONArray("items");
        JSONArray page = new JSONArray();
        int total = 0;
        for (int i = 0; all != null && i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (searchId != null && !o.optString("search_ids", "").contains("," + searchId + ",")) continue;
            if (total >= offset && page.length() < limit) page.put(o);
            total++;
        }
        JSObject r = new JSObject();
        r.put("items", jsa(page));
        r.put("total", total);
        call.resolve(r);
    }

    private static List<String> strList(JSArray a) {
        try { return a == null ? new java.util.ArrayList<>() : a.toList(); } catch (Exception e) { return new java.util.ArrayList<>(); }
    }

    @PluginMethod
    public void get(PluginCall call) {
        String id = call.getString("id", "");
        VHDb db = VHDb.get(ctx());
        JSONObject item = db.byId(id);
        if (item == null) { call.reject("İlan bulunamadı (silinmiş olabilir)"); return; }
        JSObject r = new JSObject();
        r.put("item", js(item));
        r.put("notifs", jsa(db.notifsFor(id)));
        r.put("linkAvailable", VHLinks.available(ctx(), item));
        call.resolve(r);
    }

    private void updateAndReturn(PluginCall call, String id, ContentValues v, boolean reeval) {
        VHDb db = VHDb.get(ctx());
        if (db.byId(id) == null) { call.reject("İlan bulunamadı"); return; }
        db.update(id, v);
        if (reeval) db.update(id, VHDb.evalValues(db.byId(id), VHConfigStore.get(ctx())));
        JSObject r = new JSObject();
        r.put("item", js(db.byId(id)));
        call.resolve(r);
    }

    @PluginMethod
    public void setFav(PluginCall call) {
        ContentValues v = new ContentValues();
        v.put("fav", Boolean.TRUE.equals(call.getBoolean("fav", false)) ? 1 : 0);
        updateAndReturn(call, call.getString("id", ""), v, false);
    }

    @PluginMethod
    public void setList(PluginCall call) {
        String tag = call.getString("list");
        ContentValues v = new ContentValues();
        if (tag == null || tag.isEmpty()) v.putNull("list_tag"); else v.put("list_tag", tag);
        updateAndReturn(call, call.getString("id", ""), v, false);
    }

    @PluginMethod
    public void setMarket(PluginCall call) {
        Double mv = call.getDouble("value");
        ContentValues v = new ContentValues();
        if (mv == null || mv <= 0) v.putNull("market_value"); else v.put("market_value", mv);
        updateAndReturn(call, call.getString("id", ""), v, true);
    }

    @PluginMethod
    public void setPurchase(PluginCall call) {
        ContentValues v = new ContentValues();
        Double buy = call.getDouble("buy"), sell = call.getDouble("sell");
        if (buy == null) v.putNull("buy_price"); else v.put("buy_price", buy);
        if (sell == null) v.putNull("sell_price"); else v.put("sell_price", sell);
        JSObject fees = call.getObject("fees");
        v.put("fees", fees == null ? "{}" : fees.toString());
        v.put("note", call.getString("note", ""));
        updateAndReturn(call, call.getString("id", ""), v, false);
    }

    @PluginMethod
    public void setPrice(PluginCall call) {
        Double p = call.getDouble("price");
        ContentValues v = new ContentValues();
        if (p == null || p <= 0) { v.putNull("price"); v.put("price_status", VHPrice.MISSING); }
        else { v.put("price", p); v.put("price_status", VHPrice.OK); }
        updateAndReturn(call, call.getString("id", ""), v, true);
    }

    @PluginMethod
    public void viewed(PluginCall call) {
        ContentValues v = new ContentValues();
        v.put("viewed_at", System.currentTimeMillis());
        VHDb.get(ctx()).update(call.getString("id", ""), v);
        call.resolve();
    }

    @PluginMethod
    public void remove(PluginCall call) {
        String id = call.getString("id", "");
        VHDb.get(ctx()).delete(id);
        VHNotifier.cancel(ctx(), id);
        call.resolve();
    }

    @PluginMethod
    public void addShared(PluginCall call) {
        String url = call.getString("url", "").trim();
        String title = call.getString("title", "").trim();
        if (title.isEmpty() && url.isEmpty()) { call.reject("Başlık veya bağlantı gerekli"); return; }
        String platform = call.getString("platform", url.isEmpty() ? "Diğer" : VHPlatforms.fromUrl(url));
        Double price = call.getDouble("price");
        long now = System.currentTimeMillis();
        String id = Long.toString(now, 36) + UUID.randomUUID().toString().substring(0, 6);
        VHDb db = VHDb.get(ctx());
        String ident = url.isEmpty() ? "manual|" + id : "url|" + url;
        JSONObject ex = db.byIdent(ident);
        if (ex != null) { JSObject r = new JSObject(); r.put("id", ex.optString("id")); r.put("existing", true); call.resolve(r); return; }
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("ident", ident);
        v.put("kind", url.isEmpty() ? "MANUAL" : "SHARED");
        v.put("platform", platform);
        v.put("title", title.isEmpty() ? url : title);
        v.put("text", call.getString("note", ""));
        if (!url.isEmpty()) v.put("url", url);
        if (price != null && price > 0) { v.put("price", price); v.put("price_status", VHPrice.OK); }
        else v.put("price_status", VHPrice.MISSING);
        v.put("posted_at", now);
        v.put("captured_at", now);
        v.put("updated_at", now);
        v.put("link_state", url.isEmpty() ? "NONE" : "URL");
        v.put("condition", "UNKNOWN");
        db.insert(v);
        db.update(id, VHDb.evalValues(db.byId(id), VHConfigStore.get(ctx())));
        JSObject r = new JSObject();
        r.put("id", id);
        call.resolve(r);
    }

    // ------------------------------------------------------------------ açma

    @PluginMethod
    public void openListing(PluginCall call) {
        JSObject r = new JSObject();
        r.put("opened", VHLinks.open(ctx(), call.getString("id", "")));
        call.resolve(r);
    }

    @PluginMethod
    public void openUrl(PluginCall call) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(call.getString("url", ""))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject("Bağlantı açılamadı");
        }
    }

    @PluginMethod
    public void openPlatform(PluginCall call) {
        JSObject r = new JSObject();
        r.put("ok", VHPlatforms.launch(ctx(), call.getString("platform", "")));
        call.resolve(r);
    }

    @PluginMethod
    public void takePending(PluginCall call) {
        Context c = ctx();
        JSObject r = new JSObject();
        r.put("route", VHConfigStore.getStr(c, "pending_route"));
        String share = VHConfigStore.getStr(c, "pending_share");
        if (share != null) { try { r.put("share", new JSObject(share)); } catch (Exception ignored) { } }
        VHConfigStore.putStr(c, "pending_route", null);
        VHConfigStore.putStr(c, "pending_share", null);
        call.resolve(r);
    }

    // ------------------------------------------------------------------ günlükler

    @PluginMethod
    public void notifLog(PluginCall call) {
        JSObject r = new JSObject();
        r.put("items", jsa(VHDb.get(ctx()).notifLog(call.getInt("limit", 100))));
        if (Boolean.TRUE.equals(call.getBoolean("markSeen", false))) VHConfigStore.putLong(ctx(), "notif_seen_at", System.currentTimeMillis());
        call.resolve(r);
    }

    @PluginMethod
    public void rawLog(PluginCall call) {
        JSObject r = new JSObject();
        r.put("items", jsa(VHDb.get(ctx()).rawLog(call.getInt("limit", 100))));
        call.resolve(r);
    }

    @PluginMethod
    public void clearRawLog(PluginCall call) { VHDb.get(ctx()).clearRawLog(); call.resolve(); }

    /** Metni kaydetmeden ayrıştırıp değerlendirir (ayarlar ekranındaki deneme kutusu). */
    @PluginMethod
    public void tryText(PluginCall call) {
        String text = call.getString("text", "");
        String platform = call.getString("platform", "Dolap");
        VHPrice.Parsed p = VHPrice.parse(text);
        VHModel.Evaluation ev = VHEval.evaluate(platform, text, p, null, VHConfigStore.get(ctx()));
        JSObject r = new JSObject();
        r.put("priceStatus", p.status);
        r.put("price", p.price);
        r.put("oldPrice", p.oldPrice);
        r.put("ignored", String.join(", ", p.ignored));
        r.put("matched", ev.matched);
        r.put("inLimit", ev.inLimit);
        r.put("reason", ev.reason);
        r.put("searchId", ev.searchId);
        r.put("categoryId", ev.categoryId);
        r.put("term", ev.term);
        r.put("dealPct", ev.dealPct);
        r.put("alert", ev.alert);
        call.resolve(r);
    }

    // ------------------------------------------------------------------ veri

    @PluginMethod
    public void exportBackup(PluginCall call) {
        try {
            Context c = ctx();
            JSONObject o = new JSONObject();
            o.put("app", "VintageHunter");
            o.put("format", 2);
            o.put("exportedAt", System.currentTimeMillis());
            o.put("config", new JSONObject(VHConfigStore.json(c)));
            String ui = VHConfigStore.getStr(c, "ui_state");
            if (ui != null) o.put("ui", new JSONObject(ui));
            o.put("listings", VHDb.get(c).all());
            String name = "vintagehunter-yedek-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".json";
            File dir = new File(c.getCacheDir(), "backup");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File f = new File(dir, name);
            byte[] bytes = o.toString().getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream out = new FileOutputStream(f)) { out.write(bytes); }
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
            Intent send = new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, "Yedeği kaydet veya gönder").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(chooser);
            JSObject r = new JSObject();
            r.put("bytes", bytes.length);
            r.put("name", name);
            r.put("listings", o.getJSONArray("listings").length());
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Yedek oluşturulamadı: " + e.getMessage());
        }
    }

    @PluginMethod
    public void importBackup(PluginCall call) {
        try {
            JSONObject o = new JSONObject(call.getString("json", ""));
            if (!"VintageHunter".equals(o.optString("app"))) { call.reject("Bu dosya bir VintageHunter yedeği değil"); return; }
            Context c = ctx();
            JSObject r = new JSObject();
            JSONObject cfg = o.optJSONObject("config");
            if (cfg != null) {
                String err = VHConfigStore.set(c, cfg.toString());
                if (err != null) { call.reject("Yedekteki ayar geçersiz: " + err); return; }
            }
            JSONObject ui = o.optJSONObject("ui");
            if (ui != null) VHConfigStore.putStr(c, "ui_state", ui.toString());
            JSONArray rows = o.optJSONArray("listings");
            int n = rows == null ? 0 : VHDb.get(c).importRows(rows);
            VHDb.get(c).reevaluateAll(VHConfigStore.get(c));
            r.put("listings", n);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Yedek okunamadı: " + e.getMessage());
        }
    }

    @PluginMethod
    public void clearCache(PluginCall call) {
        long freed = VHDb.get(ctx()).cleanOrphanPhotos(VHPhotos.dir(ctx()));
        File bdir = new File(ctx().getCacheDir(), "backup");
        File[] bs = bdir.listFiles();
        if (bs != null) for (File f : bs) { freed += f.length(); //noinspection ResultOfMethodCallIgnored
            f.delete(); }
        final long total = freed;
        getBridge().executeOnMainThread(() -> {
            try { getBridge().getWebView().clearCache(true); } catch (Exception ignored) { }
            JSObject r = new JSObject();
            r.put("freed", total);
            call.resolve(r);
        });
    }

    @PluginMethod
    public void clearHistory(PluginCall call) {
        JSObject r = new JSObject();
        r.put("deleted", VHDb.get(ctx()).clearHistory());
        call.resolve(r);
    }

    @PluginMethod
    public void testNotify(PluginCall call) {
        JSObject r = new JSObject();
        r.put("ok", VHNotifier.test(ctx()));
        call.resolve(r);
    }

    // ------------------------------------------------------------------ sistem ayarları

    @PluginMethod
    public void openListenerSettings(PluginCall call) {
        Context c = ctx();
        Intent i;
        if (Build.VERSION.SDK_INT >= 30) {
            i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            i.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, new ComponentName(c, VHListenerService.class).flattenToString());
        } else {
            i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception e) {
            c.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        call.resolve();
    }

    @PluginMethod
    public void openAppDetails(PluginCall call) {
        ctx().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx().getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        call.resolve();
    }

    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx().getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void requestIgnoreBattery(PluginCall call) {
        try {
            ctx().startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx().getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            ctx().startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        call.resolve();
    }

    @PluginMethod
    public void requestPostNotifications(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null) {
            getActivity().requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 4711);
        }
        call.resolve();
    }

    static String validate(String json) {
        try { return VHConfigJson.validate(VHConfigJson.parse(json)); } catch (Exception e) { return e.getMessage(); }
    }
}
