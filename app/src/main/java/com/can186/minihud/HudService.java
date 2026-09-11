package com.can186.minihud;

import android.app.*;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.Gravity;
import android.view.WindowManager;

public class HudService extends Service {
    private WindowManager wm;
    private HudView view;
    private MetricsReader reader;
    private Handler handler;
    private volatile boolean running;

    @Override public void onCreate() {
        super.onCreate();
        String ch = "hud";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel nc = new NotificationChannel(ch, "HUD", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(nc);
        }
        startForeground(1, new Notification.Builder(this, ch)
                .setContentTitle("MiniHUD").setContentText("running")
                .setSmallIcon(android.R.drawable.ic_menu_info_details).build());

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        reader = new MetricsReader();
        handler = new Handler(Looper.getMainLooper());

        int type = Build.VERSION.SDK_INT >= 26 ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 20; lp.y = 120;

        view = new HudView(this);
        wm.addView(view, lp);

        running = true;
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try { view.update(reader.read()); } catch (Exception ignored) {}
            handler.postDelayed(this, 500);
        }
    };

    @Override public void onDestroy() {
        running = false;
        if (view != null && wm != null) try { wm.removeView(view); } catch (Exception ignored) {}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
