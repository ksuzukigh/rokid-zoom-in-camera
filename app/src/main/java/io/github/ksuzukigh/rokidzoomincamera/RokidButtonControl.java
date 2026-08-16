package io.github.ksuzukigh.rokidzoomincamera;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

final class RokidButtonControl {
    private static final String TAG = "RokidZoomInCamera";
    private static final String ACTION = "com.rokid.os.master.assist.server.cmd";
    private static final String PACKAGE = "com.rokid.os.sprite.assistserver";
    private static final String SHORT_PRESS_KEY = "settings_interaction_shortPressFun";
    private static final String LONG_PRESS_KEY = "settings_interaction_longPressFun";

    private RokidButtonControl() {}

    static void claim(Context context) {
        set(context, "none", "none");
    }

    static void restore(Context context) {
        set(context, "picture", "video");
    }

    private static void set(Context context, String shortPressValue, String longPressValue) {
        try {
            Intent intent = new Intent(ACTION).setPackage(PACKAGE);
            intent.putExtra("cmd_type", "setting_change");
            intent.putExtra("value", "[{\"key\":\"" + SHORT_PRESS_KEY + "\",\"value\":\"" +
                    shortPressValue + "\"},{\"key\":\"" + LONG_PRESS_KEY + "\",\"value\":\"" +
                    longPressValue + "\"}]");
            context.sendBroadcast(intent);
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not update the Rokid camera button", error);
        }
    }
}
