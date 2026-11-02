package com.ozzystore35.vintagehunter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yeniden başlayınca veya uygulama güncellenince özet zamanlayıcısını yeniden kurar. */
public class VHBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            VHDigestReceiver.schedule(c);
        }
    }
}
