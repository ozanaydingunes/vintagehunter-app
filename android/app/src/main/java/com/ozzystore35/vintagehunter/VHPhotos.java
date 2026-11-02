package com.ozzystore35.vintagehunter;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Platform bildirimindeki ürün görselini kaydeder. Öncelik: büyük resim (BigPicture), sonra büyük simge.
 * Uygulama logosu olan kaynak (resource) simgeler ürün fotoğrafı sayılmaz.
 */
public final class VHPhotos {
    private static final int MAX = 720, MIN = 96;

    private VHPhotos() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "photos");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    /** Görseli bulur ve kaydeder; yoksa null döner. */
    public static String save(Context c, String id, Notification n) {
        try {
            Bitmap b = extract(c, n);
            if (b == null || Math.max(b.getWidth(), b.getHeight()) < MIN) return null;
            float s = Math.min(1f, (float) MAX / Math.max(b.getWidth(), b.getHeight()));
            if (s < 1f) b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * s), Math.round(b.getHeight() * s), true);
            File f = new File(dir(c), id + ".jpg");
            try (FileOutputStream o = new FileOutputStream(f)) {
                b.compress(Bitmap.CompressFormat.JPEG, 85, o);
            }
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap extract(Context c, Notification n) {
        Bundle ex = n.extras;
        Parcelable pic = ex.getParcelable(Notification.EXTRA_PICTURE);
        if (pic instanceof Bitmap) return (Bitmap) pic;
        if (Build.VERSION.SDK_INT >= 31) {
            Parcelable pi = ex.getParcelable(Notification.EXTRA_PICTURE_ICON);
            if (pi instanceof Icon) { Bitmap b = fromIcon(c, (Icon) pi); if (b != null) return b; }
        }
        Icon li = n.getLargeIcon();
        if (li != null) {
            if (Build.VERSION.SDK_INT >= 28 && li.getType() == Icon.TYPE_RESOURCE) return null; // logo
            Bitmap b = fromIcon(c, li);
            if (b != null) return b;
        }
        Parcelable lb = ex.getParcelable(Notification.EXTRA_LARGE_ICON_BIG);
        if (lb instanceof Bitmap) return (Bitmap) lb;
        return null;
    }

    private static Bitmap fromIcon(Context c, Icon icon) {
        try {
            Drawable d = icon.loadDrawable(c);
            if (d == null) return null;
            if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
            int w = Math.max(1, d.getIntrinsicWidth()), h = Math.max(1, d.getIntrinsicHeight());
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, w, h);
            d.draw(cv);
            return b;
        } catch (Throwable t) {
            return null;
        }
    }
}
