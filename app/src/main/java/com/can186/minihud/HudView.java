package com.can186.minihud;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public class HudView extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private MetricsReader.Metric data = new MetricsReader.Metric();

    public HudView(Context c) {
        super(c);
        bg.setColor(0xC0000000);
        tx.setColor(0xFFFFFFFF);
        tx.setTextSize(30);
        tx.setTypeface(Typeface.MONOSPACE);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void update(MetricsReader.Metric m) { this.data = m; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        c.drawRoundRect(new RectF(0, 0, w, h), 20, 20, bg);
        int y = 44, lh = 40;
        c.drawText("CPU  " + data.cpuFreq + "  " + data.cpuTemp + "  " + data.cpuLoad, 24, y, tx); y += lh;
        c.drawText("GPU  " + data.gpuFreq + "  " + data.gpuTemp, 24, y, tx); y += lh;
        c.drawText("DDR  " + data.ddrFreq, 24, y, tx); y += lh;
        c.drawText("RAM  " + data.ram, 24, y, tx); y += lh;
        c.drawText("BAT  " + data.batTemp + "  " + data.batPower, 24, y, tx);
    }
}
