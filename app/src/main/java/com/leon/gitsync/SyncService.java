package com.leon.gitsync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 前台服务：在后台同步所有仓库 */
public class SyncService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        SyncUtil.ensureChannel(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean onlyAuto = intent != null && intent.getBooleanExtra("onlyAuto", false);
        android.app.Notification n = new android.app.Notification.Builder(this, "sync")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Git 同步")
                .setContentText("正在同步仓库…")
                .setOngoing(true)
                .build();
        startForeground(3, n);

        new Thread(() -> {
            String summary = SyncUtil.syncAll(this, onlyAuto, s -> {
            });
            if (new Prefs(this).notifyResult()) {
                SyncUtil.notifyResult(this, "Git 同步完成", summary);
            }
            stopForeground(true);
            stopSelf();
        }, "git-sync").start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
