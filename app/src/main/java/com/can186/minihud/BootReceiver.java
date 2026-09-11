package com.can186.minihud;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try { c.startService(new Intent(c, HudService.class)); } catch (Exception ignored) {}
    }
}
