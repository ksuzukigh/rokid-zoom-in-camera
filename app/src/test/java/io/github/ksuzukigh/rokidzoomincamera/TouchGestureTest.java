package io.github.ksuzukigh.rokidzoomincamera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TouchGestureTest {
    @Test public void tapAndStationaryLongPressDoNothing() {
        assertEquals(0, TouchGesture.zoomDelta(100f, 100f, 100f, 100f, 55f, false));
        assertEquals(0, TouchGesture.zoomDelta(100f, 100f, 110f, 105f, 55f, false));
    }

    @Test public void horizontalSwipeChangesZoom() {
        assertEquals(1, TouchGesture.zoomDelta(100f, 100f, 180f, 105f, 55f, false));
        assertEquals(-1, TouchGesture.zoomDelta(180f, 100f, 100f, 105f, 55f, false));
    }

    @Test public void verticalMovementAndRecordingDoNothing() {
        assertEquals(0, TouchGesture.zoomDelta(100f, 100f, 140f, 180f, 55f, false));
        assertEquals(0, TouchGesture.zoomDelta(100f, 100f, 180f, 105f, 55f, true));
    }
}
