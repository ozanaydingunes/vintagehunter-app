package com.ozzystore35.vintagehunter;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Telefon yeniden başlayınca saatlik özeti yeniden kurar. */
public class VHBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        VHDigestReceiver.schedule(c, VHStore.config(c).optBoolean("digestOn", true));
    }
}
