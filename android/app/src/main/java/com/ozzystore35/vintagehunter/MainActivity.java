package com.ozzystore35.vintagehunter;

import android.content.Intent;
import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(VHListenerPlugin.class);
        rememberRoute(getIntent());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        rememberRoute(intent);
    }

    /** Bildirimden açıldıysa hangi ekrana gidileceğini web arayüzü için sakla. */
    private void rememberRoute(Intent i) {
        if (i == null) return;
        String r = i.getStringExtra("vh_route");
        if (r != null) VHStore.setPendingRoute(this, r);
    }
}
