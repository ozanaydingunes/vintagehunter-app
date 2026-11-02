package com.ozzystore35.vintagehunter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.ozzystore35.vintagehunter.core.VHEval;
import com.ozzystore35.vintagehunter.core.VHFilter;
import com.ozzystore35.vintagehunter.core.VHModel;
import com.ozzystore35.vintagehunter.core.VHPrice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Tek veri kaynağı: ilanlar, bildirim geçmişi, ham bildirim günlüğü. */
public final class VHDb extends SQLiteOpenHelper {
    private static final String NAME = "vh.db";
    private static final int VERSION = 1;
    public static final int MAX_LISTINGS = 3000;
    private static VHDb instance;

    public static synchronized VHDb get(Context c) {
        if (instance == null) instance = new VHDb(c.getApplicationContext());
        return instance;
    }

    private VHDb(Context c) { super(c, NAME, null, VERSION); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE listings(" +
                "id TEXT PRIMARY KEY, ident TEXT NOT NULL, kind TEXT NOT NULL, platform TEXT, pkg TEXT," +
                "title TEXT, text TEXT, url TEXT, notif_key TEXT," +
                "price REAL, prev_price REAL, price_status TEXT, ignored TEXT, photo TEXT," +
                "posted_at INTEGER, captured_at INTEGER, updated_at INTEGER, link_state TEXT," +
                "search_ids TEXT, search_id TEXT, category_id TEXT, term TEXT, in_limit INTEGER DEFAULT 0, reason TEXT," +
                "market_value REAL, deal_pct REAL, tier TEXT, condition TEXT, city TEXT," +
                "fav INTEGER DEFAULT 0, list_tag TEXT, buy_price REAL, sell_price REAL, fees TEXT, note TEXT," +
                "viewed_at INTEGER, alerted TEXT)");
        db.execSQL("CREATE UNIQUE INDEX ix_ident ON listings(ident)");
        db.execSQL("CREATE INDEX ix_captured ON listings(captured_at DESC)");
        db.execSQL("CREATE TABLE notif_log(id INTEGER PRIMARY KEY AUTOINCREMENT, listing_id TEXT, kind TEXT, title TEXT, body TEXT, at INTEGER)");
        db.execSQL("CREATE TABLE raw_log(id INTEGER PRIMARY KEY AUTOINCREMENT, platform TEXT, pkg TEXT, title TEXT, text TEXT, result TEXT, listing_id TEXT, at INTEGER)");
        db.execSQL("CREATE INDEX ix_raw_at ON raw_log(at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    // ------------------------------------------------------------------ yazma

    public synchronized JSONObject byIdent(String ident) {
        return one("SELECT * FROM listings WHERE ident=?", ident);
    }

    public synchronized JSONObject byId(String id) {
        return one("SELECT * FROM listings WHERE id=?", id);
    }

    private JSONObject one(String sql, String arg) {
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{arg})) {
            return c.moveToFirst() ? row(c) : null;
        }
    }

    public synchronized void insert(ContentValues v) {
        getWritableDatabase().insertOrThrow("listings", null, v);
        trim();
    }

    public synchronized void update(String id, ContentValues v) {
        getWritableDatabase().update("listings", v, "id=?", new String[]{id});
    }

    public synchronized void delete(String id) {
        JSONObject r = byId(id);
        if (r != null) deletePhoto(r.optString("photo", ""));
        getWritableDatabase().delete("listings", "id=?", new String[]{id});
    }

    /** En eski, favoride/listede olmayan kayıtları sınırın üstünde siler. */
    private void trim() {
        SQLiteDatabase db = getWritableDatabase();
        long n = android.database.DatabaseUtils.queryNumEntries(db, "listings");
        if (n <= MAX_LISTINGS) return;
        try (Cursor c = db.rawQuery("SELECT id, photo FROM listings WHERE fav=0 AND list_tag IS NULL ORDER BY captured_at ASC LIMIT ?",
                new String[]{String.valueOf(n - MAX_LISTINGS)})) {
            while (c.moveToNext()) {
                deletePhoto(c.getString(1));
                db.delete("listings", "id=?", new String[]{c.getString(0)});
            }
        }
    }

    static void deletePhoto(String path) {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    public synchronized void logNotif(String listingId, String kind, String title, String body) {
        ContentValues v = new ContentValues();
        v.put("listing_id", listingId);
        v.put("kind", kind);
        v.put("title", title);
        v.put("body", body);
        v.put("at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insert("notif_log", null, v);
        db.execSQL("DELETE FROM notif_log WHERE id NOT IN (SELECT id FROM notif_log ORDER BY at DESC LIMIT 500)");
    }

    public synchronized void logRaw(String platform, String pkg, String title, String text, String result, String listingId) {
        ContentValues v = new ContentValues();
        v.put("platform", platform);
        v.put("pkg", pkg);
        v.put("title", cut(title, 160));
        v.put("text", cut(text, 400));
        v.put("result", result);
        v.put("listing_id", listingId);
        v.put("at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insert("raw_log", null, v);
        db.execSQL("DELETE FROM raw_log WHERE id NOT IN (SELECT id FROM raw_log ORDER BY at DESC LIMIT 300)");
    }

    static String cut(String s, int n) { return s == null ? "" : (s.length() > n ? s.substring(0, n) : s); }

    // ------------------------------------------------------------------ yeniden değerlendirme

    /** Ayar değişince tüm kayıtlar aynı kuralla yeniden değerlendirilir. Değişen kayıt sayısını döndürür. */
    public synchronized int reevaluateAll(VHModel.Config cfg) {
        SQLiteDatabase db = getWritableDatabase();
        int changed = 0;
        db.beginTransaction();
        try (Cursor c = db.rawQuery("SELECT * FROM listings", null)) {
            while (c.moveToNext()) {
                JSONObject r = row(c);
                ContentValues v = evalValues(r, cfg);
                if (v.getAsInteger("in_limit") != r.optInt("in_limit", 0)
                        || !eqs(v.getAsString("search_ids"), r.optString("search_ids", ""))
                        || !eqs(v.getAsString("tier"), r.optString("tier", ""))) changed++;
                db.update("listings", v, "id=?", new String[]{r.optString("id")});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return changed;
    }

    private static boolean eqs(String a, String b) { return (a == null ? "" : a).equals(b == null ? "" : b); }

    /** Kayıttan değerlendirme sütunlarını hesaplar (servis ve yeniden değerlendirme aynı fonksiyonu kullanır). */
    static ContentValues evalValues(JSONObject r, VHModel.Config cfg) {
        VHPrice.Parsed p = new VHPrice.Parsed();
        p.status = r.optString("price_status", VHPrice.MISSING);
        p.price = r.isNull("price") ? null : r.optDouble("price");
        Double mv = r.isNull("market_value") ? null : r.optDouble("market_value");
        String text = r.optString("title", "") + " " + r.optString("text", "");
        VHModel.Evaluation ev = VHEval.evaluate(r.optString("platform", ""), text, p, mv, cfg);
        return evalValues(ev);
    }

    static ContentValues evalValues(VHModel.Evaluation ev) {
        ContentValues v = new ContentValues();
        v.put("search_ids", ev.searchIdsCsv());
        v.put("search_id", ev.searchId);
        v.put("category_id", ev.categoryId);
        v.put("term", ev.term);
        v.put("in_limit", ev.inLimit ? 1 : 0);
        v.put("reason", ev.reason);
        if (ev.dealPct != null) v.put("deal_pct", ev.dealPct); else v.putNull("deal_pct");
        v.put("tier", ev.tier);
        return v;
    }

    // ------------------------------------------------------------------ okuma

    public synchronized JSONObject list(VHFilter f, int offset, int limit, String order) {
        JSONArray items = new JSONArray();
        int total = 0;
        String orderBy = "deal".equals(order) ? "deal_pct IS NULL, deal_pct DESC, captured_at DESC"
                : "price".equals(order) ? "price IS NULL, price ASC" : "viewed".equals(order) ? "viewed_at DESC" : "captured_at DESC";
        String where = "viewed".equals(order) ? " WHERE viewed_at IS NOT NULL" : "";
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM listings" + where + " ORDER BY " + orderBy, null)) {
            while (c.moveToNext()) {
                JSONObject r = row(c);
                if (!f.test(toRow(r))) continue;
                if (total >= offset && items.length() < limit) items.put(r);
                total++;
            }
        }
        JSONObject out = new JSONObject();
        try { out.put("items", items); out.put("total", total); } catch (Exception ignored) { }
        return out;
    }

    static VHFilter.Row toRow(JSONObject r) {
        VHFilter.Row x = new VHFilter.Row();
        x.id = r.optString("id");
        x.platform = r.optString("platform", "");
        x.title = r.optString("title", "");
        x.text = r.optString("text", "");
        x.priceStatus = r.optString("price_status", "");
        x.price = r.isNull("price") ? null : r.optDouble("price");
        x.dealPct = r.isNull("deal_pct") ? null : r.optDouble("deal_pct");
        x.categoryId = r.isNull("category_id") ? null : r.optString("category_id");
        x.condition = r.optString("condition", "UNKNOWN");
        x.city = r.isNull("city") ? null : r.optString("city");
        x.listTag = r.isNull("list_tag") ? null : r.optString("list_tag");
        x.kind = r.optString("kind", "LISTING");
        x.inLimit = r.optInt("in_limit", 0) == 1;
        x.fav = r.optInt("fav", 0) == 1;
        x.at = r.optLong("captured_at", 0);
        return x;
    }

    public synchronized JSONObject homeStats(VHModel.Config cfg, long notifSeenAt) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        String since = String.valueOf(cal.getTimeInMillis());
        SQLiteDatabase db = getReadableDatabase();
        JSONObject o = new JSONObject();
        try {
            o.put("today", count(db, "SELECT COUNT(*) FROM listings WHERE kind IN ('LISTING','SHARED','MANUAL') AND captured_at>=?", since));
            o.put("todayInLimit", count(db, "SELECT COUNT(*) FROM listings WHERE in_limit=1 AND captured_at>=?", since));
            o.put("todayHigh", count(db, "SELECT COUNT(*) FROM listings WHERE in_limit=1 AND tier IN ('HIGH','VERY_HIGH') AND captured_at>=?", since));
            o.put("inLimitTotal", count(db, "SELECT COUNT(*) FROM listings WHERE in_limit=1", null));
            o.put("total", count(db, "SELECT COUNT(*) FROM listings", null));
            int active = 0;
            for (VHModel.Search s : cfg.searches) if (s.active) active++;
            o.put("activeSearches", active);
            o.put("lastCaptureAt", count(db, "SELECT IFNULL(MAX(captured_at),0) FROM listings", null));
            o.put("lastNotificationAt", count(db, "SELECT IFNULL(MAX(at),0) FROM raw_log", null));
            o.put("unreadNotifs", count(db, "SELECT COUNT(*) FROM notif_log WHERE at>?", String.valueOf(notifSeenAt)));
        } catch (Exception ignored) { }
        return o;
    }

    /** Arama başına toplam ve uygun ilan sayısı. */
    public synchronized JSONObject searchCounts() {
        JSONObject o = new JSONObject();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT search_ids, in_limit FROM listings WHERE search_ids IS NOT NULL AND search_ids<>''", null)) {
            while (c.moveToNext()) {
                for (String id : c.getString(0).split(",")) {
                    if (id.isEmpty()) continue;
                    JSONObject x = o.optJSONObject(id);
                    if (x == null) { x = new JSONObject(); o.put(id, x); }
                    x.put("total", x.optInt("total") + 1);
                    if (c.getInt(1) == 1) x.put("inLimit", x.optInt("inLimit") + 1);
                }
            }
        } catch (Exception ignored) { }
        return o;
    }

    public synchronized JSONObject platformStats(String platform) {
        SQLiteDatabase db = getReadableDatabase();
        String day = String.valueOf(System.currentTimeMillis() - 24L * 3600 * 1000);
        JSONObject o = new JSONObject();
        try {
            o.put("lastNotificationAt", count(db, "SELECT IFNULL(MAX(at),0) FROM raw_log WHERE platform=?", platform));
            o.put("lastListingAt", count(db, "SELECT IFNULL(MAX(captured_at),0) FROM listings WHERE platform=?", platform));
            o.put("notifs24h", count2(db, "SELECT COUNT(*) FROM raw_log WHERE platform=? AND at>=?", platform, day));
            o.put("matched24h", count2(db, "SELECT COUNT(*) FROM listings WHERE platform=? AND captured_at>=?", platform, day));
            o.put("inLimit24h", count2(db, "SELECT COUNT(*) FROM listings WHERE platform=? AND in_limit=1 AND captured_at>=?", platform, day));
            o.put("unreadable24h", count2(db, "SELECT COUNT(*) FROM listings WHERE platform=? AND price_status<>'OK' AND kind='LISTING' AND captured_at>=?", platform, day));
            o.put("totalListings", count(db, "SELECT COUNT(*) FROM listings WHERE platform=?", platform));
        } catch (Exception ignored) { }
        return o;
    }

    public synchronized long countInLimitSince(long since) {
        return count(getReadableDatabase(), "SELECT COUNT(*) FROM listings WHERE in_limit=1 AND captured_at>=?", String.valueOf(since));
    }

    public synchronized JSONArray inLimitSince(long since) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM listings WHERE in_limit=1 AND captured_at>=? ORDER BY captured_at DESC",
                new String[]{String.valueOf(since)})) {
            while (c.moveToNext()) a.put(row(c));
        }
        return a;
    }

    private static long count(SQLiteDatabase db, String sql, String arg) {
        try (Cursor c = db.rawQuery(sql, arg == null ? null : new String[]{arg})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    private static long count2(SQLiteDatabase db, String sql, String a, String b) {
        try (Cursor c = db.rawQuery(sql, new String[]{a, b})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    public synchronized JSONArray notifLog(int limit) {
        return rows("SELECT n.*, l.title AS l_title, l.price AS l_price, l.platform AS l_platform, l.photo AS l_photo " +
                "FROM notif_log n LEFT JOIN listings l ON l.id=n.listing_id ORDER BY n.at DESC LIMIT " + limit);
    }

    public synchronized JSONArray rawLog(int limit) {
        return rows("SELECT * FROM raw_log ORDER BY at DESC LIMIT " + limit);
    }

    public synchronized void clearRawLog() { getWritableDatabase().delete("raw_log", null, null); }

    public synchronized JSONArray notifsFor(String listingId) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM notif_log WHERE listing_id=? ORDER BY at DESC", new String[]{listingId})) {
            while (c.moveToNext()) a.put(row(c));
        }
        return a;
    }

    public synchronized JSONArray all() { return rows("SELECT * FROM listings ORDER BY captured_at DESC"); }

    private JSONArray rows(String sql) {
        JSONArray a = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) a.put(row(c));
        }
        return a;
    }

    static JSONObject row(Cursor c) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < c.getColumnCount(); i++) {
                String k = c.getColumnName(i);
                switch (c.getType(i)) {
                    case Cursor.FIELD_TYPE_NULL: break;               // null yazılmaz → isNull(k) true
                    case Cursor.FIELD_TYPE_INTEGER: o.put(k, c.getLong(i)); break;
                    case Cursor.FIELD_TYPE_FLOAT: o.put(k, c.getDouble(i)); break;
                    default: o.put(k, c.getString(i));
                }
            }
        } catch (Exception ignored) { }
        return o;
    }

    /** Favori/listede olmayan ilanları siler. */
    public synchronized int clearHistory() {
        SQLiteDatabase db = getWritableDatabase();
        int n = 0;
        try (Cursor c = db.rawQuery("SELECT id, photo FROM listings WHERE fav=0 AND list_tag IS NULL", null)) {
            while (c.moveToNext()) { deletePhoto(c.getString(1)); n++; }
        }
        db.delete("listings", "fav=0 AND list_tag IS NULL", null);
        db.delete("notif_log", "listing_id NOT IN (SELECT id FROM listings)", null);
        return n;
    }

    /** Kaydı olmayan fotoğraf dosyalarını siler. Silinen bayt. */
    public synchronized long cleanOrphanPhotos(File dir) {
        long freed = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        java.util.Set<String> used = new java.util.HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT photo FROM listings WHERE photo IS NOT NULL", null)) {
            while (c.moveToNext()) used.add(c.getString(0));
        }
        for (File f : files) {
            if (!used.contains(f.getAbsolutePath())) { freed += f.length(); //noinspection ResultOfMethodCallIgnored
                f.delete(); }
        }
        return freed;
    }

    /** Yedekten kayıt ekleme/güncelleme (kimliğe göre). */
    public synchronized int importRows(JSONArray rows) {
        SQLiteDatabase db = getWritableDatabase();
        int n = 0;
        db.beginTransaction();
        try {
            for (int i = 0; i < rows.length(); i++) {
                JSONObject r = rows.optJSONObject(i);
                if (r == null || r.optString("id", "").isEmpty() || r.optString("ident", "").isEmpty()) continue;
                ContentValues v = new ContentValues();
                java.util.Iterator<String> it = r.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if ("photo".equals(k)) continue;
                    Object val = r.opt(k);
                    if (val instanceof Integer || val instanceof Long) v.put(k, ((Number) val).longValue());
                    else if (val instanceof Number) v.put(k, ((Number) val).doubleValue());
                    else if (val != null && val != JSONObject.NULL) v.put(k, String.valueOf(val));
                }
                db.delete("listings", "id=? OR ident=?", new String[]{r.optString("id"), r.optString("ident")});
                db.insert("listings", null, v);
                n++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return n;
    }

    public static List<String> listTags() {
        List<String> l = new ArrayList<>();
        l.add("TRACK"); l.add("CONSIDER"); l.add("BOUGHT");
        return l;
    }
}
