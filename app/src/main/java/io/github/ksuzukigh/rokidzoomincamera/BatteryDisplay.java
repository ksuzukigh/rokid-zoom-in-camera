package io.github.ksuzukigh.rokidzoomincamera;

final class BatteryDisplay {
    private BatteryDisplay() {}

    static int percentage(int level, int scale) {
        if (level < 0 || scale <= 0) return -1;
        return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    }

    static String label(int percent, boolean charging) {
        if (percent < 0) return "電池 --%";
        if (charging) return "充電中 " + percent + "%";
        if (percent <= 10) return "要充電 " + percent + "%";
        if (percent <= 20) return "電池少 " + percent + "%";
        return "電池 " + percent + "%";
    }
}
