package com.ozzystore35.vintagehunter;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/** 1.1/1.2'nin captures.json kayıtlarını yeni veritabanına taşır (bildirim göndermeden, yeni kurallarla). */
public final class VHMigration {
    private VHMigration() {}

    public static synchronized void run(Context c) {
        if (VHConfigStore.getLong(c, "migrated_12") == 1) return;
        File f = new File(c.getFilesDir(), "captures.json");
        int moved = 0;
        if (f.exists()) {
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0;
                while (off < b.length) { int n = in.read(b, off, b.length - off); if (n < 0) break; off += n; }
                JSONArray arr = new JSONArray(new String(b, 0, off, StandardCharsets.UTF_8));
                for (int i = arr.length() - 1; i >= 0; i--) {   // eskiden yeniye
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    VHPipeline.Outcome out = VHPipeline.process(c, o.optString("platform"), o.optString("pkg"), null,
                            o.optString("title"), o.optString("text"), o.optLong("at"), null, null, true);
                    if ("NEW".equals(out.result)) moved++;
                }
                //noinspection ResultOfMethodCallIgnored
                f.renameTo(new File(c.getFilesDir(), "captures.migrated.json"));
            } catch (Exception ignored) { }
        }
        c.getSharedPreferences("vh_hunter", Context.MODE_PRIVATE).edit().clear().apply();
        VHConfigStore.putLong(c, "migrated_12", 1);
        VHConfigStore.putLong(c, "migrated_12_count", moved);
    }
}
