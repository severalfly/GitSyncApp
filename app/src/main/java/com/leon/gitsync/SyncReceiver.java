package com.leon.gitsync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 定时闹钟 / 开机 / 网络恢复的入口 */
public class SyncReceiver extends BroadcastReceiver {

    public static final String ACTION_NETWORK = "com.leon.gitsync.NETWORK";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        Prefs p = new Prefs(ctx);
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            SyncUtil.schedule(ctx, p.intervalMinutes());
        } else if (SyncUtil.ACTION_ALARM.equals(action)) {
            SyncUtil.startSync(ctx, true);
        } else if (ACTION_NETWORK.equals(action)) {
            if (p.retryOnNetwork() && p.bool("pendingRetry", false)) {
                SyncUtil.startSync(ctx, true);
            }
        }
    }
}
