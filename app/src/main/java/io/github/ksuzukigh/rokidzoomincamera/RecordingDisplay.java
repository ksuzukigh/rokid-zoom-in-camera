package io.github.ksuzukigh.rokidzoomincamera;

import java.util.Locale;

final class RecordingDisplay {
    private RecordingDisplay() {}

    static String label(long elapsedSeconds) {
        long seconds = Math.max(0L, elapsedSeconds);
        String elapsed;
        if (seconds >= 3600L) {
            elapsed = String.format(Locale.JAPAN, "%d:%02d:%02d",
                    seconds / 3600L, (seconds / 60L) % 60L, seconds % 60L);
        } else {
            elapsed = String.format(Locale.JAPAN, "%02d:%02d",
                    seconds / 60L, seconds % 60L);
        }
        return "録画中 " + elapsed;
    }
}
