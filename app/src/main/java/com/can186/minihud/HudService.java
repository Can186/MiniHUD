package com.can186.minihud;

import android.app.*;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.Gravity;
import android.view.WindowManager;
import java.util.List;

public class HudService extends Service {
    private WindowManager wm;
    private HudView view;
    private MetricsReader reader;
    private Handler handler;
    private volatile boolean running;
    private Config cfg;

    @Override public void onCreate() {
        super.onCreate();
        cfg = Config.load();

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
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = cfg.posX; lp.y = cfg.posY;

        view = new HudView(this, cfg);
        wm.addView(view, lp);

        running = true;
        handler.post(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            try {
                boolean visible = shouldShow();
                view.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
                if (visible) view.update(reader.read());
            } catch (Exception ignored) {}
            handler.postDelayed(this, Math.max(100, cfg.interval));
        }
    };

    private boolean shouldShow() {
        if ("off".equals(cfg.pkgMode) || cfg.pkgList.isEmpty()) return true;
        String fg = getForegroundPackage();
        if (fg == null || fg.isEmpty()) return false;
        boolean inList = cfg.pkgList.contains(fg);
        if ("whitelist".equals(cfg.pkgMode)) return inList;
        if ("blacklist".equals(cfg.pkgMode)) return !inList;
        return true;
    }

    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            List<UsageStats> list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now);
            if (list == null || list.isEmpty()) return null;
            UsageStats recent = null;
            for (UsageStats s : list) {
                if (recent == null || s.getLastTimeUsed() > recent.getLastTimeUsed()) recent = s;
            }
            return recent != null ? recent.getPackageName() : null;
        } catch (Exception e) { return null; }
    }

    @Override public void onDestroy() {
        running = false;
        if (view != null && wm != null) try { wm.removeView(view); } catch (Exception ignored) {}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return null; }
}
