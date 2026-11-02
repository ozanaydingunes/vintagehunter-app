package com.ozzystore35.vintagehunter;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;

import com.ozzystore35.vintagehunter.core.VHEval;
import com.ozzystore35.vintagehunter.core.VHIdent;
import com.ozzystore35.vintagehunter.core.VHModel;
import com.ozzystore35.vintagehunter.core.VHPrice;
import com.ozzystore35.vintagehunter.core.VHText;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.UUID;

/**
 * Bildirim → kayıt → bildirim akışı. Sıra her zaman aynıdır:
 *   1. metni ayrıştır ve tek kural motoruyla değerlendir,
 *   2. eşleşen ilanı veritabanına yaz (yeni kayıt veya fiyat güncellemesi),
 *   3. ancak kayıt yazıldıktan sonra, o kaydın kimliğiyle bildirim gönder.
 * Böylece bildirimdeki ilan her zaman uygulamadaki listede vardır.
 */
public final class VHPipeline {
    private VHPipeline() {}

    private static final ThreadLocal<Boolean> SILENT = ThreadLocal.withInitial(() -> false);

    public static final class Outcome {
        public String result;     // NEW, PRICE_DROP, DUPLICATE, NO_MATCH, EXCLUDED, PLATFORM_OFF
        public String listingId;
        public String alert = "NONE";
    }

    public static Outcome process(Context c, String platform, String pkg, String notifKey, String title, String text,
                                  long postTime, Notification n, PendingIntent contentIntent) {
        return process(c, platform, pkg, notifKey, title, text, postTime, n, contentIntent, false);
    }

    /** @param silent true ise bildirim gönderilmez (eski sürümden taşıma). */
    public static synchronized Outcome process(Context c, String platform, String pkg, String notifKey, String title, String text,
                                               long postTime, Notification n, PendingIntent contentIntent, boolean silent) {
        SILENT.set(silent);
        Outcome out = new Outcome();
        VHDb db = VHDb.get(c);
        VHModel.Config cfg = VHConfigStore.get(c);
        String full = (title + " " + text).trim();

        if (!cfg.settings.platformOn(platform)) {
            out.result = "PLATFORM_OFF";
            db.logRaw(platform, pkg, title, text, "Platform kapalı", null);
            return out;
        }

        VHPrice.Parsed price = VHPrice.parse(full);
        VHModel.Evaluation ev = VHEval.evaluate(platform, full, price, null, cfg);
        if (!ev.matched) {
            out.result = "EXCLUDED".equals(ev.reason) ? "EXCLUDED" : "NO_MATCH";
            db.logRaw(platform, pkg, title, text, "EXCLUDED".equals(ev.reason) ? "Hariç kelime" : "Kayıtlı aramayla eşleşmedi", null);
            return out;
        }

        String ident = VHIdent.key(platform, title, text);
        JSONObject existing = db.byIdent(ident);
        long now = System.currentTimeMillis();

        if (existing != null) {
            String id = existing.optString("id");
            out.listingId = id;
            Double oldPrice = existing.isNull("price") ? null : existing.optDouble("price");
            ContentValues v = new ContentValues();
            v.put("updated_at", now);
            v.put("notif_key", notifKey);
            v.put("link_state", contentIntent != null ? "LIVE" : existing.optString("link_state", "NONE"));
            if (existing.isNull("photo") && n != null) {
                String photo = VHPhotos.save(c, id, n);
                if (photo != null) v.put("photo", photo);
            }
            if (contentIntent != null) VHLinks.remember(id, contentIntent);

            boolean drop = price.ok() && oldPrice != null && price.price < oldPrice - 0.005;
            if (!drop) {
                db.update(id, v);
                out.result = "DUPLICATE";
                db.logRaw(platform, pkg, title, text, "Tekrar (zaten kayıtlı)", id);
                return out;
            }
            // Fiyat düştü: kaydı yeni fiyatla güncelle ve yeniden değerlendir
            v.put("price", price.price);
            v.put("prev_price", oldPrice);
            v.put("price_status", VHPrice.OK);
            v.put("text", text);
            Double mv = existing.isNull("market_value") ? null : existing.optDouble("market_value");
            VHModel.Evaluation ev2 = VHEval.evaluate(platform, full, price, mv, cfg);
            v.putAll(VHDb.evalValues(ev2));
            db.update(id, v);
            out.result = "PRICE_DROP";
            boolean tracked = existing.optInt("fav", 0) == 1 || !existing.isNull("list_tag");
            boolean wasInLimit = existing.optInt("in_limit", 0) == 1;
            JSONObject row = db.byId(id);
            if (tracked && cfg.settings.notifyPriceDrop) out.alert = "PRICE_DROP";
            else if (ev2.inLimit && !wasInLimit && ("MATCH".equals(ev2.alert) || "HIGH_DEAL".equals(ev2.alert))) out.alert = ev2.alert;
            db.logRaw(platform, pkg, title, text, "Fiyat düştü: " + fmt(oldPrice) + " → " + fmt(price.price) + " TL", id);
            notifyIfNeeded(c, cfg, row, out, contentIntent);
            return out;
        }

        String id = Long.toString(now, 36) + UUID.randomUUID().toString().substring(0, 6);
        out.listingId = id;
        ContentValues v = new ContentValues();
        v.put("id", id);
        v.put("ident", ident);
        v.put("kind", VHPrice.AGGREGATE.equals(price.status) ? "AGGREGATE" : "LISTING");
        v.put("platform", platform);
        v.put("pkg", pkg);
        v.put("title", title);
        v.put("text", text);
        v.put("notif_key", notifKey);
        if (price.price != null) v.put("price", price.price);
        if (price.oldPrice != null) v.put("prev_price", price.oldPrice);
        v.put("price_status", price.status);
        v.put("ignored", String.join(", ", price.ignored));
        v.put("posted_at", postTime > 0 ? postTime : now);
        v.put("captured_at", now);
        v.put("updated_at", now);
        v.put("link_state", contentIntent != null ? "LIVE" : "NONE");
        String folded = VHText.fold(full);
        v.put("condition", VHText.condition(folded));
        v.put("city", VHText.city(folded));
        v.putAll(VHDb.evalValues(ev));
        if (n != null) {
            String photo = VHPhotos.save(c, id, n);
            if (photo != null) v.put("photo", photo);
        }
        db.insert(v);
        if (contentIntent != null) VHLinks.remember(id, contentIntent);
        out.result = "NEW";
        out.alert = ev.alert;
        db.logRaw(platform, pkg, title, text, resultText(ev, price), id);
        notifyIfNeeded(c, cfg, db.byId(id), out, contentIntent);
        return out;
    }

    private static String resultText(VHModel.Evaluation ev, VHPrice.Parsed p) {
        if (ev.inLimit) return "Uygun ilan · " + fmt(p.price) + " TL";
        switch (ev.reason == null ? "" : ev.reason) {
            case "OVER_MAX": return "Sınır üstü · " + fmt(p.price) + " TL";
            case "UNDER_MIN": return "Alt sınırın altında · " + fmt(p.price) + " TL";
            case "PRICE_MISSING": return "Eşleşti, fiyat okunamadı";
            case "PRICE_AMBIGUOUS": return "Eşleşti, fiyat belirsiz (birden çok tutar)";
            case "PRICE_FOREIGN": return "Eşleşti, yabancı para birimi";
            case "AGGREGATE": return "Toplu arama bildirimi";
            case "DEAL_UNKNOWN": return "Eşleşti, piyasa değeri yok (fırsat şartı)";
            case "DEAL_LOW": return "Eşleşti, fırsat oranı düşük";
            default: return "Eşleşti";
        }
    }

    static String fmt(Double d) {
        if (d == null) return "?";
        return (d == Math.rint(d)) ? String.format(java.util.Locale.forLanguageTag("tr-TR"), "%,.0f", d)
                : String.format(java.util.Locale.forLanguageTag("tr-TR"), "%,.2f", d);
    }

    private static void notifyIfNeeded(Context c, VHModel.Config cfg, JSONObject row, Outcome out, PendingIntent original) {
        if (row == null || SILENT.get()) return;
        String a = out.alert;
        if (!("MATCH".equals(a) || "HIGH_DEAL".equals(a) || "PRICE_DROP".equals(a))) return;
        if (VHEval.quiet(cfg.settings, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) {
            out.alert = "QUIET";
            return;
        }
        VHNotifier.listing(c, cfg, row, a, original);
    }
}
