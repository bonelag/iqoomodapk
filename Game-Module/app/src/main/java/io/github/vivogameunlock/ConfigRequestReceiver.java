package io.github.vivogameunlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Manifest permission DUMP restricts requests to privileged callers, including the system UID hosts. */
public final class ConfigRequestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String pkg = intent.getStringExtra("target");
        if (ConfigContract.targetAllowed(pkg)) ConfigStore.send(ctx, pkg);
    }
}
