package io.github.ksuzukigh.rokidzoomincamera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaPathsTest {
    @Test public void pathsMatchHiRokidFolders() {
        assertEquals("DCIM/Camera", MediaPaths.PHOTO_PATH);
        assertEquals("Movies/Camera", MediaPaths.VIDEO_PATH);
    }

    @Test public void identifiesOnlyLegacyAppVideos() {
        assertTrue(MediaPaths.isLegacyVideo(
                "RokidZoomIn_20260818_172433_3.0x.mp4", "DCIM/Camera/"));
        assertFalse(MediaPaths.isLegacyVideo(
                "RokidZoomIn_20260818_172433_3.0x.jpg", "DCIM/Camera/"));
        assertFalse(MediaPaths.isLegacyVideo(
                "RokidZoomIn_20260818_172433_3.0x.mp4", "Movies/Camera/"));
        assertFalse(MediaPaths.isLegacyVideo("other.mp4", "DCIM/Camera/"));
    }
}
