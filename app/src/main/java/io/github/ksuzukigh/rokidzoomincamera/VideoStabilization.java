package io.github.ksuzukigh.rokidzoomincamera;

final class VideoStabilization {
    private static final int MODE_ON = 1;

    private VideoStabilization() {}

    static boolean isSupported(int[] availableModes) {
        if (availableModes == null) return false;
        for (int mode : availableModes) {
            if (mode == MODE_ON) return true;
        }
        return false;
    }
}
