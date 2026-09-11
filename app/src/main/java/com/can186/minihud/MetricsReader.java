package com.can186.minihud;

import java.io.*;

public class MetricsReader {
    public static class Metric {
        public String cpuFreq = "--", cpuTemp = "--", cpuLoad = "--";
        public String gpuFreq = "--", gpuTemp = "--";
        public String ddrFreq = "--";
        public String ram = "--";
        public String batTemp = "--", batPower = "--";
    }
    private long lastIdle = 0, lastTotal = 0;

    public Metric read() {
        Metric m = new Metric();
        m.cpuFreq = cpuFreq();
        m.cpuTemp = thermal("cpu");
        m.cpuLoad = cpuLoad();
        m.gpuFreq = gpuFreq();
        m.gpuTemp = thermal("gpu");
        m.ddrFreq = ddrFreq();
        m.ram = ram();
        m.batTemp = batTemp();
        m.batPower = batPower();
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
        return max == 0 ? "--" : String.format("%.2fGHz", max / 1e6);
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

    private String thermal(String key) {
        for (int i = 0; i < 40; i++) {
            try {
                String type = read("/sys/class/thermal/thermal_zone" + i + "/type").trim().toLowerCase();
                if (type.contains(key)) {
                    int t = Integer.parseInt(read("/sys/class/thermal/thermal_zone" + i + "/temp").trim());
                    return String.format("%.1f°C", t / 1000.0);
                }
            } catch (Exception ignored) {}
        }
        return "--";
    }

    private String gpuFreq() {
        // MTK GPU 频率
        String[] paths = {
            "/proc/gpufreq/gpufreq_opp_freq",
            "/proc/gpufreqv2/stack",
            "/sys/kernel/debug/gpufreq/gpufreq_opp_freq"
        };
        for (String p : paths) {
            String v = read(p).trim();
            if (!v.isEmpty()) {
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("(\\d+)\\s*(kHz|KHz|KHz)?").matcher(v);
                if (mt.find()) {
                    try {
                        int f = Integer.parseInt(mt.group(1));
                        return String.format("%.0fMHz", f / 1000.0);
                    } catch (Exception ignored) {}
                }
                return v.length() > 12 ? v.substring(0, 12) : v;
            }
        }
        return "--";
    }

    private String ddrFreq() {
        String[] paths = {
            "/sys/kernel/helio-dvfsrc/dvfsrc_opp_table",
            "/sys/devices/platform/soc/10012000.dvfsrc/helio-dvfsrc/dvfsrc_opp_table",
            "/proc/dvfsrc/dvfsrc_dump"
        };
        for (String p : paths) {
            String v = read(p).trim();
            if (!v.isEmpty()) {
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("(\\d{3,4})\\s*(?:MHz|mbps|Mbps)").matcher(v);
                if (mt.find()) return mt.group(1) + "MHz";
            }
        }
        return "--";
    }

    private String ram() {
        try {
            BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"));
            long total = 0, avail = 0; String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) total = Long.parseLong(line.split("\\s+")[1]);
                else if (line.startsWith("MemAvailable:")) { avail = Long.parseLong(line.split("\\s+")[1]); break; }
            }
            r.close();
            return String.format("%.1f/%.1fG", (total - avail) / 1048576.0, total / 1048576.0);
        } catch (Exception e) { return "--"; }
    }

    private String batTemp() {
        try {
            int t = Integer.parseInt(read("/sys/class/power_supply/battery/temp").trim());
            return String.format("%.1f°C", t / 10.0);
        } catch (Exception e) { return "--"; }
    }

    private String batPower() {
        try {
            long v = Long.parseLong(read("/sys/class/power_supply/battery/voltage_now").trim());
            long c = Long.parseLong(read("/sys/class/power_supply/battery/current_now").trim());
            return String.format("%.2fW", Math.abs(v * c) / 1e12);
        } catch (Exception e) { return "--"; }
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