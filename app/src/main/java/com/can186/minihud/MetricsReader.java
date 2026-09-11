package com.can186.minihud;

import java.io.*;
import java.util.regex.*;

public class MetricsReader {
    public static class Metric {
        public String cpuFreq = "--", cpuTemp = "--", cpuLoad = "--";
        public String gpuFreq = "--", gpuTemp = "--", gpuLoad = "--";
        public String ddrFreq = "--";
        public String ram = "--", ramPercent = "--";
        public String batTemp = "--", batPower = "--", batPercent = "--";
        public String fps = "--";
    }
    private long lastIdle = 0, lastTotal = 0;
    private String cachedFps = "--";
    private long lastFpsTime = 0;

    public Metric read() {
        Metric m = new Metric();
        m.cpuFreq = cpuFreq();
        m.cpuTemp = thermal("cpu");
        m.cpuLoad = cpuLoad();
        gpu(m);
        m.ddrFreq = ddrFreq();
        ram(m);
        bat(m);
        m.fps = getFps();
        return m;
    }

    private String cpuFreq() {
        int max = 0;
        for (int i = 0; i < 8; i++) {
            try {
                int f = Integer.parseInt(read("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq").trim());
                if (f > max) max = f;
            } catch (Exception ignored) {}
        }
        return max == 0 ? "--" : String.format("%.2fG", max / 1e6);
    }

    private String cpuLoad() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/stat"));
            String[] p = r.readLine().split("\\s+");
            r.close();
            long idle = Long.parseLong(p[4]);
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);
            long di = idle - lastIdle, dt = total - lastTotal;
            lastIdle = idle; lastTotal = total;
            if (dt == 0) return "--";
            return ((dt - di) * 100 / dt) + "%";
        } catch (Exception e) { return "--"; }
    }

    private void gpu(Metric m) {
        try {
            String v = read("/proc/gpufreq/gpufreq_var_dump");
            Matcher f = Pattern.compile("Freq:\\s*\\d+\\s*\\((\\d+)\\)").matcher(v);
            if (f.find()) m.gpuFreq = String.format("%.0fM", Integer.parseInt(f.group(1)) / 1000.0);
            Matcher l = Pattern.compile("gpu_loading\\s*:\\s*(\\d+)").matcher(v);
            if (l.find()) m.gpuLoad = l.group(1) + "%";
        } catch (Exception ignored) {}
        m.gpuTemp = thermal("gpu");
    }

    private String thermal(String key) {
        for (int i = 0; i < 40; i++) {
            try {
                String type = read("/sys/class/thermal/thermal_zone" + i + "/type").trim().toLowerCase();
                if (type.contains(key)) {
                    int t = Integer.parseInt(read("/sys/class/thermal/thermal_zone" + i + "/temp").trim());
                    return String.format("%.0f°", t / 1000.0);
                }
            } catch (Exception ignored) {}
        }
        return "--";
    }

    private String ddrFreq() {
        try {
            String v = read("/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_dump");
            Matcher m = Pattern.compile("DDR\\s*:\\s*(\\d+)\\s*khz").matcher(v);
            if (m.find()) return String.format("%.0fM", Integer.parseInt(m.group(1)) / 1000.0);
        } catch (Exception ignored) {}
        return "--";
    }

    private void ram(Metric m) {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"));
            long total = 0, avail = 0; String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) total = Long.parseLong(line.split("\\s+")[1]);
                else if (line.startsWith("MemAvailable:")) { avail = Long.parseLong(line.split("\\s+")[1]); break; }
            }
            r.close();
            long used = total - avail;
            m.ram = String.format("%.1f/%.1fG", used / 1048576.0, total / 1048576.0);
            m.ramPercent = (used * 100 / total) + "%";
        } catch (Exception ignored) {}
    }

    private void bat(Metric m) {
        try {
            int t = Integer.parseInt(read("/sys/class/power_supply/battery/temp").trim());
            m.batTemp = String.format("%.0f°", t / 10.0);
        } catch (Exception ignored) {}
        try {
            long v = Long.parseLong(read("/sys/class/power_supply/battery/voltage_now").trim());
            long c = Long.parseLong(read("/sys/class/power_supply/battery/current_now").trim());
            m.batPower = String.format("%.1fW", Math.abs(v * c) / 1e12);
        } catch (Exception ignored) {}
        try {
            int p = Integer.parseInt(read("/sys/class/power_supply/battery/capacity").trim());
            m.batPercent = p + "%";
        } catch (Exception ignored) {}
    }

    private String getFps() {
        long now = System.currentTimeMillis();
        if (now - lastFpsTime < 1000) return cachedFps;
        lastFpsTime = now;
        try {
            Process p1 = Runtime.getRuntime().exec(new String[]{"su", "-c",
                "dumpsys window windows | grep mCurrentFocus"});
            BufferedReader r1 = new BufferedReader(new InputStreamReader(p1.getInputStream()));
            String focus = r1.readLine();
            r1.close();
            if (focus == null) { cachedFps = "--"; return cachedFps; }

            Matcher m = Pattern.compile("u0\\s+(\\S+?)/").matcher(focus);
            if (!m.find()) { cachedFps = "--"; return cachedFps; }
            String pkg = m.group(1);
            String layer = pkg + "/" + pkg;

            Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c",
                "dumpsys SurfaceFlinger --latency " + layer});
            BufferedReader r2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            String line;
            int count = 0;
            long firstTs = 0, lastTs = 0;
            int idx = 0;
            while ((line = r2.readLine()) != null) {
                if (idx++ == 0) continue;
                String[] cols = line.trim().split("\\s+");
                if (cols.length < 2) continue;
                try {
                    long ts = Long.parseLong(cols[1]);
                    if (ts == 0 || ts == Long.MAX_VALUE) continue;
                    if (firstTs == 0) firstTs = ts;
                    lastTs = ts;
                    count++;
                } catch (Exception ignored) {}
            }
            r2.close();

            if (count < 2) { cachedFps = "--"; return cachedFps; }
            double durationNs = (lastTs - firstTs);
            if (durationNs <= 0) { cachedFps = "--"; return cachedFps; }
            double fps = (count - 1) * 1e9 / durationNs;
            cachedFps = String.format("%.0f", fps);
        } catch (Exception e) {
            cachedFps = "--";
        }
        return cachedFps;
    }

    private String read(String path) {
        try {
            BufferedReader r = new BufferedReader(new FileReader(path));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            r.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
