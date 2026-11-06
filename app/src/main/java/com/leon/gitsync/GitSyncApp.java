package com.leon.gitsync;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import androidx.multidex.MultiDexApplication;

public class GitSyncApp extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        // 必须在任何 JGit 操作之前替换全局 FS
        AndroidFS.install();
        SyncUtil.ensureChannel(this);
        SyncUtil.schedule(this, new Prefs(this).intervalMinutes());

        // 网络恢复后自动重试失败的同步
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            NetworkRequest req = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Prefs p = new Prefs(GitSyncApp.this);
                    if (p.retryOnNetwork() && p.bool("pendingRetry", false)) {
                        SyncUtil.startSync(GitSyncApp.this, true);
                    }
                }
            });
        } catch (Exception ignored) {
        }
    }
}
