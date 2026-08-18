package io.github.ksuzukigh.rokidzoomincamera;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VideoStabilizationTest {
    @Test public void supportedWhenOnModeIsAvailable() {
        assertTrue(VideoStabilization.isSupported(new int[]{0, 1}));
    }

    @Test public void unsupportedWhenOnlyOffModeIsAvailable() {
        assertFalse(VideoStabilization.isSupported(new int[]{0}));
    }

    @Test public void unsupportedWhenModesAreMissing() {
        assertFalse(VideoStabilization.isSupported(null));
    }
}
