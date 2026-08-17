package io.github.ksuzukigh.rokidzoomincamera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RecordingDisplayTest {
    @Test public void showsElapsedTime() {
        assertEquals("録画中 00:00", RecordingDisplay.label(0L));
        assertEquals("録画中 02:05", RecordingDisplay.label(125L));
        assertEquals("録画中 1:01:01", RecordingDisplay.label(3661L));
    }

    @Test public void clampsNegativeElapsedTime() {
        assertEquals("録画中 00:00", RecordingDisplay.label(-10L));
    }
}
