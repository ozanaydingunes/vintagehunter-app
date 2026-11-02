package com.vhtest.dolap;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.Base64;

import java.nio.charset.StandardCharsets;

/** adb shell am broadcast -n com.vhtest.dolap/.Post --ei id 1 --es t64 <base64> --es x64 <base64> [--ez pic true] [--es l64 <base64 satırlar | ile>] */
public class Post extends BroadcastReceiver {
    static String dec(Intent i, String k) {
        String v = i.getStringExtra(k);
        return v == null ? "" : new String(Base64.decode(v, Base64.DEFAULT), StandardCharsets.UTF_8);
    }

    @Override
    public void onReceive(Context c, Intent in) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("sim", "Arama bildirimleri", NotificationManager.IMPORTANCE_HIGH));
        int id = in.getIntExtra("id", 1);
        String title = dec(in, "t64"), text = dec(in, "x64"), lines = dec(in, "l64");
        Intent open = new Intent(c, ListingActivity.class).putExtra("listing", title + " | " + text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(c, "sim").setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true);
        if (in.getBooleanExtra("pic", false)) {
            Bitmap bm = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 640, 640, Color.rgb(60, 82, 140), Color.rgb(170, 110, 60), Shader.TileMode.CLAMP));
            cv.drawRect(0, 0, 640, 640, p);
            p.setShader(null);
            p.setColor(Color.WHITE);
            p.setTextSize(54);
            cv.drawText("TEST", 40, 320, p);
            b.setLargeIcon(bm).setStyle(new Notification.BigPictureStyle().bigPicture(bm));
        } else if (!lines.isEmpty()) {
            Notification.InboxStyle s = new Notification.InboxStyle();
            for (String l : lines.split("\\|")) s.addLine(l);
            b.setStyle(s);
        } else {
            b.setStyle(new Notification.BigTextStyle().bigText(text));
        }
        nm.notify(id, b.build());
    }
}
