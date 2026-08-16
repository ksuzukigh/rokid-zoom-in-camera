package io.github.ksuzukigh.rokidzoomincamera;

import android.graphics.Rect;

final class ZoomMath {
    private ZoomMath() {}

    static float clamp(float requested, float maximum) {
        return Math.max(1f, Math.min(requested, Math.max(1f, maximum)));
    }

    static Rect crop(Rect sensor, float requestedZoom, float maximumZoom) {
        int[] edges = cropEdges(sensor.left, sensor.top, sensor.right, sensor.bottom,
                requestedZoom, maximumZoom);
        return new Rect(edges[0], edges[1], edges[2], edges[3]);
    }

    static int[] cropEdges(int sensorLeft, int sensorTop, int sensorRight, int sensorBottom,
                           float requestedZoom, float maximumZoom) {
        float zoom = clamp(requestedZoom, maximumZoom);
        int sensorWidth = sensorRight - sensorLeft;
        int sensorHeight = sensorBottom - sensorTop;
        int width = Math.max(2, Math.round(sensorWidth / zoom));
        int height = Math.max(2, Math.round(sensorHeight / zoom));
        width -= width & 1;
        height -= height & 1;
        int centerX = sensorLeft + sensorWidth / 2;
        int centerY = sensorTop + sensorHeight / 2;
        int left = centerX - width / 2;
        int top = centerY - height / 2;
        return new int[]{left, top, left + width, top + height};
    }
}
