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
        m.cpuTemp = thermalType("mtktscpu", "cpu");
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
            String line = r.readLine();
            r.close();
            if (line == null) return "--";
            String[] p = line.trim().split("\\s+");
            // p[0]="cpu", p[1]=user, p[2]=nice, p[3]=system, p[4]=idle, p[5]=iowait, ...
            long idle = Long.parseLong(p[4]) + Long.parseLong(p[5]);
            long total = 0;
            for (int i = 1; i < p.length; i++) total += Long.parseLong(p[i]);
            long di = idle - lastIdle, dt = total - lastTotal;
            lastIdle = idle; lastTotal = total;
            if (dt <= 0) return "--";
            int load = (int)((dt - di) * 100 / dt);
            if (load < 0) load = 0;
            if (load > 100) load = 100;
            return load + "%";
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
        // MTK 没有独立 GPU thermal zone，用 PA 或 AP 代替
        String t = thermalType("mtktspa", "pa");
        if ("--".equals(t)) t = thermalType("mtktsAP", "ap");
        m.gpuTemp = t;
    }

    private String thermalType(String... keys) {
        // 遍历所有 zone，按 keys 顺序匹配
        for (int i = 0; i < 30; i++) {
            try {
                String type = read("/sys/class/thermal/thermal_zone" + i + "/type").trim();
                for (String key : keys) {
                    if (type.equalsIgnoreCase(key) || type.toLowerCase().contains(key.toLowerCase())) {
                        int t = Integer.parseInt(read("/sys/class/thermal/thermal_zone" + i + "/temp").trim());
                        return String.format("%.0f°", t / 1000.0);
                    }
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

    // ==== FPS：从 SurfaceFlinger 列表找当前 SurfaceView，读它的 --latency ====
    private String getFps() {
        long now = System.currentTimeMillis();
        if (now - lastFpsTime < 1500) return cachedFps;
        lastFpsTime = now;
        try {
            // 1. 拿当前焦点包名
            String focus = exec("dumpsys window | grep mCurrentFocus");
            if (focus == null) { cachedFps = "--"; return cachedFps; }
            Matcher m = Pattern.compile("u0\\s+(\\S+?)/").matcher(focus);
            if (!m.find()) { cachedFps = "--"; return cachedFps; }
            String pkg = m.group(1);

            // 2. 从 SurfaceFlinger 列表找该包名对应的 SurfaceView
            String list = exec("dumpsys SurfaceFlinger --list");
            if (list == null) { cachedFps = "--"; return cachedFps; }
            String layer = null;
            for (String ln : list.split("\n")) {
                String s = ln.trim();
                if (s.contains(pkg) && s.contains("SurfaceView") && !s.contains("Background")) {
                    layer = s;
                    break;
                }
            }
            if (layer == null) {
                // 兜底：找任何含包名的 layer
                for (String ln : list.split("\n")) {
                    String s = ln.trim();
                    if (s.contains(pkg) && !s.contains("Background") && !s.contains("dim")) {
                        layer = s;
                        break;
                    }
                }
            }
            if (layer == null) { cachedFps = "--"; return cachedFps; }

            // 3. 读该 layer 的帧时间戳
            String lat = exec("dumpsys SurfaceFlinger --latency \"" + layer + "\"");
            if (lat == null) { cachedFps = "--"; return cachedFps; }
            String[] lines = lat.split("\n");
            if (lines.length < 3) { cachedFps = "--"; return cachedFps; }

            int count = 0;
            long firstTs = 0, lastTs = 0;
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].trim().split("\\s+");
                if (cols.length < 2) continue;
                try {
                    long ts = Long.parseLong(cols[1]);
                    if (ts == 0 || ts == Long.MAX_VALUE) continue;
                    if (firstTs == 0) firstTs = ts;
                    lastTs = ts;
                    count++;
                } catch (Exception ignored) {}
            }

            if (count < 2) { cachedFps = "--"; return cachedFps; }
            double durNs = lastTs - firstTs;
            if (durNs <= 0) { cachedFps = "--"; return cachedFps; }
            double fps = (count - 1) * 1e9 / durNs;
            cachedFps = String.format("%.0f", fps);
        } catch (Exception e) {
            cachedFps = "--";
        }
        return cachedFps;
    }

    private String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            r.close();
            p.waitFor();
            return sb.toString();
        } catch (Exception e) { return null; }
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
