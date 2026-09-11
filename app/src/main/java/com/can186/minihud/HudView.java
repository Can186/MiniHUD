package com.can186.minihud;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class HudView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private MetricsReader.Metric data = new MetricsReader.Metric();
    private final Config cfg;
    private final int bgColor, textColor, cLow, cMid, cHigh;

    public HudView(Context c, Config cfg) {
        super(c);
        this.cfg = cfg;
        this.bgColor = Config.parseColor(cfg.bgColor, 0xFF1A1A1A);
        this.textColor = Config.parseColor(cfg.textColor, 0xFFFFFFFF);
        this.cLow = Config.parseColor(cfg.colorLow, 0xFF00FF00);
        this.cMid = Config.parseColor(cfg.colorMid, 0xFFFFFF00);
        this.cHigh = Config.parseColor(cfg.colorHigh, 0xFFFF0000);

        int a = (int)(Math.max(0, Math.min(100, cfg.bgAlpha)) * 255 / 100);
        bg.setColor((a << 24) | (bgColor & 0x00FFFFFF));
        tx.setColor(textColor);
        tx.setTextSize(cfg.fontSize);
        tx.setTypeface(Typeface.MONOSPACE);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void update(MetricsReader.Metric m) { this.data = m; invalidate(); }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int lineH = cfg.fontSize + 12;
        int rows = 0;
        if (cfg.showCpu) rows++;
        if (cfg.showGpu) rows++;
        if (cfg.showDdr) rows++;
        if (cfg.showRam) rows++;
        if (cfg.showBat) rows++;
        int width = (int)(cfg.fontSize * 22);
        int height = lineH * rows + 28;
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        c.drawRoundRect(new RectF(0, 0, w, h), cfg.cornerRadius, cfg.cornerRadius, bg);

        int pad = 14;
        int y = cfg.fontSize + pad;
        int lh = cfg.fontSize + 12;

        if (cfg.showCpu) {
            int load = parseIntSafe(data.cpuLoad);
            tx.setColor(loadColor(load));
            c.drawText("CPU " + data.cpuFreq + " " + data.cpuTemp + " " + data.cpuLoad, pad, y, tx);
            y += lh;
        }
        if (cfg.showGpu) {
            int load = parseIntSafe(data.gpuLoad);
            tx.setColor(loadColor(load));
            c.drawText("GPU " + data.gpuFreq + " " + data.gpuTemp + " " + data.gpuLoad, pad, y, tx);
            y += lh;
        }
        if (cfg.showDdr) {
            tx.setColor(textColor);
            c.drawText("DDR " + data.ddrFreq, pad, y, tx);
            y += lh;
        }
        if (cfg.showRam) {
            int load = parseIntSafe(data.ramPercent);
            tx.setColor(loadColor(load));
            c.drawText("RAM " + data.ram, pad, y, tx);
            y += lh;
        }
        if (cfg.showBat) {
            tx.setColor(textColor);
            c.drawText("BAT " + data.batPercent + " " + data.batTemp + " " + data.batPower, pad, y, tx);
        }
    }

    private int loadColor(int load) {
        if (load < 0) return textColor;
        if (load < cfg.thresholdLow) return cLow;
        if (load < cfg.thresholdHigh) return cMid;
        return cHigh;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replace("%", "").trim());
        } catch (Exception e) { return -1; }
    }
}
