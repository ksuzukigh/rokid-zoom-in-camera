package io.github.ksuzukigh.rokidzoomincamera;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        RokidButtonControl.restore(context);
    }
}
