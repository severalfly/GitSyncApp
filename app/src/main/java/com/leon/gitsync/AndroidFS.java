package com.leon.gitsync;

import android.util.Log;

import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Android 专用 JGit 文件系统。
 *
 * JGit 默认 FS_POSIX 用 Files.createLink()（硬链接）原子创建 .lock 文件，
 * Android 的 /sdcard 是 FUSE、应用私有目录也受 SELinux 限制，都不允许 link，
 * 于是 init / commit / push 一律抛 AccessDeniedException。
 *
 * 这里直接继承 FS 基类（完全不碰 FS_POSIX 的硬链接逻辑），并通过反射把
 * 全局 FS.DETECTED 换成本实现，保证 JGit 内部任何取 FS 的路径都拿到它。
 */
public class AndroidFS extends FS {

    public static final AndroidFS INSTANCE = new AndroidFS();
    private static final String TAG = "GitSyncFS";

    static {
        install();
    }

    /** 把 JGit 全局 FS.DETECTED 替换掉（ART 上 static final 引用字段可通过反射改） */
    public static void install() {
        try {
            Field f = FS.class.getDeclaredField("DETECTED");
            f.setAccessible(true);
            clearFinal(f);
            f.set(null, INSTANCE);
            Log.i(TAG, "FS.DETECTED -> " + FS.DETECTED.getClass().getName());
        } catch (Throwable t) {
            Log.e(TAG, "install failed: " + t);
        }
    }

    private static void clearFinal(Field f) throws Exception {
        for (String name : new String[]{"accessFlags", "modifiers"}) {
            try {
                Field m = Field.class.getDeclaredField(name);
                m.setAccessible(true);
                m.setInt(f, m.getInt(f) & ~Modifier.FINAL);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    @Override
    public FS newInstance() {
        return new AndroidFS();
    }

    @Override
    public boolean supportsExecute() {
        return false;
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }

    @Override
    public boolean canExecute(File f) {
        return false;
    }

    @Override
    public boolean setExecute(File f, boolean executable) {
        return false;
    }

    @Override
    public boolean retryFailedLockFileCommit() {
        return false;
    }

    @Override
    protected File discoverGitExe() {
        return null;
    }

    @Override
    public ProcessBuilder runInShell(String cmd, String[] args) {
        List<String> argv = new ArrayList<>();
        argv.add(cmd);
        argv.addAll(Arrays.asList(args));
        return new ProcessBuilder(argv);
    }

    @Override
    public boolean supportsSymlinks() {
        return false;
    }

    @Override
    public boolean supportsAtomicCreateNewFile() {
        return false;
    }

    @Override
    public boolean createNewFile(File f) throws IOException {
        return f.createNewFile();
    }

    /** Android 没有 dos:hidden 属性视图，JGit 默认实现会抛 UnsupportedOperationException */
    @Override
    public void setHidden(File f, boolean hidden) throws IOException {
        // 什么都不做
    }

    @Override
    public void createSymLink(File f, String target) throws IOException {
        // Android 上不使用符号链接
    }

    @Override
    public String readSymLink(File f) throws IOException {
        return null;
    }

    @Override
    public void setLastModified(File f, long time) throws IOException {
        f.setLastModified(time);
    }

    @Override
    public FS.Attributes getAttributes(File f) {
        return new FS.Attributes(f, this);
    }
}
