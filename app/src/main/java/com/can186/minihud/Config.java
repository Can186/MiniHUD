package com.can186.minihud;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Config {
    public boolean showCpu = true, showGpu = true, showDdr = true, showRam = true, showBat = true, showFps = true;
    public int interval = 500;
    public int fontSize = 30;
    public int posX = 20, posY = 120;
    public String bgColor = "#1A1A1A";
    public int bgAlpha = 0;
    public String textColor = "#FFFFFF";
    public int cornerRadius = 8;
    public String colorLow = "#00FF00";
    public String colorMid = "#FFFF00";
    public String colorHigh = "#FF0000";
    public int thresholdLow = 50;
    public int thresholdHigh = 80;

    // 默认黑名单：避免浮层挡在管理器和桌面上
    public String pkgMode = "blacklist";
    public List<String> pkgList = new ArrayList<>(Arrays.asList(
        "com.resukisu.resukisu",
        "com.can186.minihud",
        "com.android.settings",
        "com.miui.home",
        "com.omarea.vtools",
        "com.termux"
    ));

    private static final String PATH = "/data/adb/minihud/config.json";

    public static Config load() {
        Config c = new Config();
        try {
            BufferedReader r = new BufferedReader(new FileReader(PATH));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l);
            r.close();
            JSONObject o = new JSONObject(sb.toString());
            if (o.has("showCpu")) c.showCpu = o.getBoolean("showCpu");
            if (o.has("showGpu")) c.showGpu = o.getBoolean("showGpu");
            if (o.has("showDdr")) c.showDdr = o.getBoolean("showDdr");
            if (o.has("showRam")) c.showRam = o.getBoolean("showRam");
            if (o.has("showBat")) c.showBat = o.getBoolean("showBat");
            if (o.has("showFps")) c.showFps = o.getBoolean("showFps");
            if (o.has("interval")) c.interval = o.getInt("interval");
            if (o.has("fontSize")) c.fontSize = o.getInt("fontSize");
            if (o.has("posX")) c.posX = o.getInt("posX");
            if (o.has("posY")) c.posY = o.getInt("posY");
            if (o.has("bgColor")) c.bgColor = o.getString("bgColor");
            if (o.has("bgAlpha")) c.bgAlpha = o.getInt("bgAlpha");
            if (o.has("textColor")) c.textColor = o.getString("textColor");
            if (o.has("cornerRadius")) c.cornerRadius = o.getInt("cornerRadius");
            if (o.has("colorLow")) c.colorLow = o.getString("colorLow");
            if (o.has("colorMid")) c.colorMid = o.getString("colorMid");
            if (o.has("colorHigh")) c.colorHigh = o.getString("colorHigh");
            if (o.has("thresholdLow")) c.thresholdLow = o.getInt("thresholdLow");
            if (o.has("thresholdHigh")) c.thresholdHigh = o.getInt("thresholdHigh");
            if (o.has("pkgMode")) c.pkgMode = o.getString("pkgMode");
            if (o.has("pkgList")) {
                JSONArray arr = o.getJSONArray("pkgList");
                c.pkgList.clear();
                for (int i = 0; i < arr.length(); i++) c.pkgList.add(arr.getString(i));
            }
        } catch (Exception e) {
            Log.w("MiniHUD", "load config failed, use defaults: " + e);
        }
        return c;
    }

    public static int parseColor(String hex, int def) {
        try {
            if (hex.startsWith("#")) return android.graphics.Color.parseColor(hex);
        } catch (Exception ignored) {}
        return def;
    }
}
