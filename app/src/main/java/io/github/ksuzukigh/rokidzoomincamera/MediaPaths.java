package io.github.ksuzukigh.rokidzoomincamera;

final class MediaPaths {
    static final String PHOTO_PATH = "DCIM/Camera";
    static final String VIDEO_PATH = "Movies/Camera";
    static final String LEGACY_VIDEO_PATH = "DCIM/Camera/";
    static final String VIDEO_FILE_PREFIX = "RokidZoomIn_";

    private MediaPaths() {}

    static boolean isLegacyVideo(String displayName, String relativePath) {
        return displayName != null
                && displayName.startsWith(VIDEO_FILE_PREFIX)
                && displayName.endsWith(".mp4")
                && LEGACY_VIDEO_PATH.equals(relativePath);
    }
}
