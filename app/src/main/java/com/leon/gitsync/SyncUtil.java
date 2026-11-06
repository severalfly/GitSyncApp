package com.leon.gitsync;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

/** 后台同步：定时、开机、网络恢复、快捷磁贴共用 */
public class SyncUtil {

    public static final String ACTION_ALARM = "com.leon.gitsync.ALARM";
    private static final String CHANNEL = "sync";
    private static final int NOTI_ID = 2;

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            NotificationChannel c = new NotificationChannel(CHANNEL, "自动同步",
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(c);
        }
    }

    public static void notifyResult(Context ctx, String title, String text) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            android.app.Notification n = new android.app.Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build();
            nm.notify(NOTI_ID, n);
        } catch (Exception ignored) {
        }
    }

    public static void startSync(Context ctx, boolean onlyAuto) {
        Intent i = new Intent(ctx, SyncService.class);
        i.putExtra("onlyAuto", onlyAuto);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception e) {
            // Android 12+ 后台启动前台服务可能被限制，此时打开应用会补同步
            notifyResult(ctx, "无法在后台启动同步", "打开一次应用即可同步：" + GitEngine.err(e));
        }
    }

    public static void startSync(Context ctx) {
        startSync(ctx, false);
    }

    public static void schedule(Context ctx, int minutes) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ctx, SyncReceiver.class).setAction(ACTION_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (minutes > 0) {
            long interval = minutes * 60_000L;
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + interval, interval, pi);
        }
    }

    public static String syncAll(Context ctx, boolean onlyAuto, GitEngine.Log log) {
        RepoStore store = new RepoStore(ctx);
        int ok = 0, fail = 0;
        for (Repo r : new ArrayList<>(store.list())) {
            if (onlyAuto && !r.autoSync) continue;
            log.line("== " + r.name + " ==");
            GitEngine.Result res = GitEngine.sync(ctx, r, log);
            r.lastSync = System.currentTimeMillis();
            r.lastResult = (res.ok ? "成功 · " : "失败 · ") + res.message;
            store.save(r);
            if (res.ok) ok++;
            else fail++;
        }
        if (ok + fail == 0) {
            return "没有需要同步的仓库";
        }
        new Prefs(ctx).put("pendingRetry", fail > 0);
        return ok + " 个成功 / " + fail + " 个失败";
    }

    /**
     * 端到端自检：建裸仓库 → 提交推送 → 克隆 → 改文件 → 一键同步 → 再克隆核对 → 看历史。
     * 不需要网络、不需要权限，用来确认 git 引擎在本机可用。
     */
    public static String selfTest(Context ctx, GitEngine.Log log) {
        File base = new File(ctx.getFilesDir(), "selftest");
        deleteRec(base);
        File bare = new File(base, "remote.git");
        File work = new File(base, "work");
        File clone = new File(base, "clone");
        File verify = new File(base, "verify");
        String url = "file://" + bare.getAbsolutePath();
        try {
            log.line("[0/7] FS=" + org.eclipse.jgit.util.FS.DETECTED.getClass().getName());
            // 1 裸仓库
            try (Git b = Git.init().setFs(AndroidFS.INSTANCE).setBare(true)
                    .setDirectory(bare).call()) {
                log.line("[1/7] 裸仓库创建 OK");
            }

            // 2 首次提交 + 推送
            try (Git git = Git.init().setFs(AndroidFS.INSTANCE).setDirectory(work).call()) {
                write(new File(work, "note.txt"), "v1\n");
                git.add().addFilepattern(".").call();
                git.commit().setMessage("自检：首次提交")
                        .setAuthor("自检", "selftest@local")
                        .setCommitter("自检", "selftest@local").call();
                StoredConfig cfg = git.getRepository().getConfig();
                cfg.setString("remote", "origin", "url", url);
                cfg.setString("remote", "origin", "fetch",
                        "+refs/heads/*:refs/remotes/origin/*");
                cfg.save();
                String branch = git.getRepository().getFullBranch();
                git.push().setRemote("origin")
                        .setRefSpecs(new RefSpec(branch + ":" + branch)).call();
                log.line("[2/7] 首次提交并推送 OK，分支 " + branch);
            }

            // 3 克隆
            try (Git g = Git.cloneRepository().setFs(AndroidFS.INSTANCE)
                    .setURI(url).setDirectory(clone).call()) {
                log.line("[3/7] 克隆 OK，内容=" + read(new File(clone, "note.txt")).trim());
            }

            // 4 改文件 + 一键同步
            write(new File(clone, "note.txt"), "v2 edited by one-key sync\n");
            Repo r = new Repo();
            r.name = "自检仓库";
            r.localPath = clone.getAbsolutePath();
            r.remoteUrl = url;
            r.messageTemplate = "自检 %date";
            GitEngine.Result sync = GitEngine.sync(ctx, r, log);
            if (!sync.ok) return "自检失败（一键同步）：" + sync.message;
            log.line("[4/7] 一键同步 OK：" + sync.message);

            // 5 再克隆核对远程内容
            try (Git v = Git.cloneRepository().setFs(AndroidFS.INSTANCE)
                    .setURI(url).setDirectory(verify).call()) {
                String content = read(new File(verify, "note.txt"));
                if (!content.contains("v2 edited")) {
                    return "自检失败：推送后远程内容未更新，实际是「" + content.trim() + "」";
                }
                log.line("[5/7] 远程内容已更新为「" + content.trim() + "」");
            }

            // 6 提交历史
            int n = GitEngine.history(ctx, r, 20).size();
            if (n < 2) return "自检失败：提交历史只有 " + n + " 条";
            log.line("[6/7] 提交历史 " + n + " 条 OK");

            // 7 状态 + 分支 + 远程
            int branches = GitEngine.branches(ctx, r).size();
            int remotes = GitEngine.remotes(ctx, r).size();
            log.line("[7/7] 分支 " + branches + " 个 / 远程 " + remotes + " 个 OK");

            return "自检通过：创建仓库、推送、克隆、一键同步、提交历史、分支/远程 全部正常";
        } catch (Throwable t) {
            android.util.Log.e("GitSyncSelfTest", "self test failed", t);
            return "自检失败：" + GitEngine.err(t);
        }
    }

    private static void write(File f, String s) throws Exception {
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static void deleteRec(File f) {
        if (!f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRec(k);
        f.delete();
    }
}
