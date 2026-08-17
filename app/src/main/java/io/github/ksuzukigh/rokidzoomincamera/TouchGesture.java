package io.github.ksuzukigh.rokidzoomincamera;

final class TouchGesture {
    private TouchGesture() {}

    static int zoomDelta(float downX, float downY, float upX, float upY,
                         float swipeThreshold, boolean recording) {
        if (recording) return 0;
        float dx = upX - downX;
        float dy = upY - downY;
        if (Math.abs(dx) <= swipeThreshold || Math.abs(dx) <= Math.abs(dy)) return 0;
        return dx > 0f ? 1 : -1;
    }
}
