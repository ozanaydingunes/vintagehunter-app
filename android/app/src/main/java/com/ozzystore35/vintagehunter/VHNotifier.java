package com.ozzystore35.vintagehunter;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.ozzystore35.vintagehunter.core.VHModel;

import org.json.JSONObject;

/** Uygulamanın kendi bildirimleri. Her ilan bildirimi kayıt kimliğini taşır; dokununca o ilanın detayı açılır. */
public final class VHNotifier {
    public static final String CH_HIGH = "vh_high_deal";
    public static final String CH_MATCH = "vh_match";
    public static final String CH_DROP = "vh_price_drop";
    public static final String CH_DIGEST = "vh_summary";
    public static final String CH_TEST = "vh_test";
    public static final String EXTRA_LISTING = "vh_listing";
    public static final String EXTRA_ROUTE = "vh_route";

    private VHNotifier() {}

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        for (String old : new String[]{"vh_alarm", "vh_digest"}) {
            try { nm.deleteNotificationChannel(old); } catch (Exception ignored) { }
        }
        NotificationChannel h = new NotificationChannel(CH_HIGH, "Yüksek fırsat", NotificationManager.IMPORTANCE_HIGH);
        h.setDescription("Fırsat oranı yüksek ve fiyat sınırınıza uyan ilan — öncelikli");
        h.enableVibration(true);
        h.setVibrationPattern(new long[]{0, 500, 150, 500, 150, 800});
        nm.createNotificationChannel(h);
        NotificationChannel m = new NotificationChannel(CH_MATCH, "Uygun ilan", NotificationManager.IMPORTANCE_HIGH);
        m.setDescription("Kayıtlı aramanız ve kategori fiyat sınırınızla eşleşen yeni ilan");
        m.enableVibration(true);
        m.setVibrationPattern(new long[]{0, 350, 200, 350});
        nm.createNotificationChannel(m);
        NotificationChannel d = new NotificationChannel(CH_DROP, "Fiyat düşüşü", NotificationManager.IMPORTANCE_HIGH);
        d.setDescription("Takip ettiğiniz ilanın fiyatı düştü");
        nm.createNotificationChannel(d);
        NotificationChannel s = new NotificationChannel(CH_DIGEST, "Özet", NotificationManager.IMPORTANCE_DEFAULT);
        s.setDescription("Son 30/60 dakikada bulunan uygun ilanların özeti");
        nm.createNotificationChannel(s);
        NotificationChannel t = new NotificationChannel(CH_TEST, "Bildirim testi", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(t);
    }

    public static int idFor(String listingId) { return 0x5000 + (listingId.hashCode() & 0x0FFFFFFF); }

    static PendingIntent openApp(Context c, String extraKey, String value, int req) {
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra(extraKey, value);
        i.setAction("vh.open." + req);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** @param kind MATCH, HIGH_DEAL veya PRICE_DROP */
    public static void listing(Context c, VHModel.Config cfg, JSONObject r, String kind, PendingIntent original) {
        ensureChannels(c);
        String id = r.optString("id");
        int nid = idFor(id);
        String platform = r.optString("platform", "");
        Double price = r.isNull("price") ? null : r.optDouble("price");
        Double prev = r.isNull("prev_price") ? null : r.optDouble("prev_price");
        String term = r.optString("term", "");
        VHModel.Category cat = cfg.category(r.optString("category_id", ""));
        String catName = cat == null ? "" : cat.name;
        String priceTxt = VHPipeline.fmt(price) + " TL";

        String title, channel;
        if ("HIGH_DEAL".equals(kind)) {
            channel = CH_HIGH;
            title = "Yüksek fırsat %" + Math.round(r.optDouble("deal_pct", 0)) + " · " + priceTxt;
        } else if ("PRICE_DROP".equals(kind)) {
            channel = CH_DROP;
            title = "Fiyat düştü · " + VHPipeline.fmt(prev) + " → " + priceTxt;
        } else {
            channel = CH_MATCH;
            title = "Uygun ilan · " + priceTxt + (cat != null && cat.maxPrice != null ? " (sınır " + VHPipeline.fmt(cat.maxPrice) + ")" : "");
        }
        String head = (term.isEmpty() ? "" : term + " · ") + platform + (catName.isEmpty() ? "" : " · " + catName);
        String body = (r.optString("title", "") + " — " + r.optString("text", "")).trim();
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_stat_vh)
                .setContentTitle(title)
                .setContentText(head)
                .setSubText(platform)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setColor(0xFFC98A4B)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setWhen(r.optLong("captured_at", System.currentTimeMillis()))
                .setShowWhen(true)
                .setContentIntent(openApp(c, EXTRA_LISTING, id, nid));
        Bitmap photo = null;
        String path = r.optString("photo", "");
        if (!path.isEmpty()) {
            try { photo = BitmapFactory.decodeFile(path); } catch (Throwable ignored) { }
        }
        if (photo != null) {
            b.setLargeIcon(photo);
            b.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(photo).setSummaryText(head + "\n" + body));
        } else {
            b.setStyle(new NotificationCompat.BigTextStyle().bigText(head + "\n" + body));
        }
        if (original != null) b.addAction(0, "Platformda aç", original);
        b.addAction(0, "Detay", openApp(c, EXTRA_LISTING, id, nid + 1));
        try {
            NotificationManagerCompat.from(c).notify(nid, b.build());
        } catch (SecurityException e) {
            VHDb.get(c).logNotif(id, kind + "_BLOCKED", title, "Bildirim izni yok");
            return;
        }
        VHDb.get(c).logNotif(id, kind, title, head);
        android.content.ContentValues v = new android.content.ContentValues();
        v.put("alerted", kind);
        VHDb.get(c).update(id, v);
    }

    public static void digest(Context c, String title, String text) {
        ensureChannels(c);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_DIGEST)
                .setSmallIcon(R.drawable.ic_stat_vh)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(0xFFC98A4B)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openApp(c, EXTRA_ROUTE, "deals", 4242));
        try {
            NotificationManagerCompat.from(c).notify(4242, b.build());
            VHDb.get(c).logNotif(null, "DIGEST", title, text);
        } catch (SecurityException ignored) { }
    }

    /** Kullanıcının alarm ayarlarını denemesi için. İlan değildir, ilan olarak kaydedilmez. */
    public static boolean test(Context c) {
        ensureChannels(c);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_TEST)
                .setSmallIcon(R.drawable.ic_stat_vh)
                .setContentTitle("Bildirim testi")
                .setContentText("VintageHunter bildirimleri çalışıyor. Bu bir ilan değildir.")
                .setColor(0xFFC98A4B)
                .setAutoCancel(true)
                .setContentIntent(openApp(c, EXTRA_ROUTE, "status", 4243));
        try {
            NotificationManagerCompat.from(c).notify(4243, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public static void cancel(Context c, String listingId) {
        NotificationManagerCompat.from(c).cancel(idFor(listingId));
    }
}
