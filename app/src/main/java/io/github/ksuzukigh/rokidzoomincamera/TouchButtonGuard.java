package io.github.ksuzukigh.rokidzoomincamera;

final class TouchButtonGuard {
    private TouchButtonGuard() {}

    static boolean shouldIgnore(long lastTouchAt, long eventAt, long suppressionWindowMs) {
        if (lastTouchAt < 0L || eventAt < lastTouchAt) return false;
        return eventAt - lastTouchAt <= suppressionWindowMs;
    }
}
