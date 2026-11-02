package com.vhtest.dolap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** "Platformdaki ilan sayfası" yerine geçer: bildirimden açılınca ilan metnini gösterir. */
public class ListingActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        t.setTextSize(22);
        t.setPadding(40, 120, 40, 40);
        String l = getIntent().getStringExtra("listing");
        t.setText(l == null ? "Dolap Test" : "SIM ILAN SAYFASI: " + l);
        setContentView(t);
    }
}
