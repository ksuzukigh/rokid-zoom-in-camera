package io.github.ksuzukigh.rokidzoomincamera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PreviewGeometryTest {
    @Test public void rotatedFullHdFillsPortraitHudWithoutStretching() {
        PreviewGeometry.Layout layout = PreviewGeometry.centerCrop(
                480, 640, 1920, 1080, true);
        assertEquals(480, layout.width);
        assertEquals(854, layout.height);
        assertAspectClose(1080.0 / 1920.0, layout);
    }

    @Test public void landscapeBufferCropsSidesWithoutStretching() {
        PreviewGeometry.Layout layout = PreviewGeometry.centerCrop(
                480, 640, 1920, 1080, false);
        assertEquals(1138, layout.width);
        assertEquals(640, layout.height);
        assertAspectClose(1920.0 / 1080.0, layout);
    }

    @Test public void matchingPortraitRatioNeedsNoCrop() {
        PreviewGeometry.Layout layout = PreviewGeometry.centerCrop(
                480, 640, 640, 480, true);
        assertEquals(480, layout.width);
        assertEquals(640, layout.height);
    }

    private static void assertAspectClose(double expected, PreviewGeometry.Layout layout) {
        double actual = (double) layout.width / layout.height;
        assertTrue(Math.abs(expected - actual) < 0.002);
    }
}
