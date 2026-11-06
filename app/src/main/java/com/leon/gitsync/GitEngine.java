package com.leon.gitsync;

import android.content.Context;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.FS;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 所有 git 操作（基于 JGit + AndroidFS） */
public class GitEngine {

    public interface Log {
        void line(String s);
    }

    public static class Result {
        public boolean ok;
        public String message;
        public List<String> conflicts = new ArrayList<>();
        public int changed;
        public boolean committed;
        public boolean pushed;

        public Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    public static class CommitInfo {
        public String hash;
        public String shortMessage;
        public String fullMessage;
        public String author;
        public long time;

        public CommitInfo(String hash, String shortMessage, String fullMessage,
                          String author, long time) {
            this.hash = hash;
            this.shortMessage = shortMessage;
            this.fullMessage = fullMessage;
            this.author = author;
            this.time = time;
        }

        @Override
        public String toString() {
            return shortMessage;
        }
    }

    // ------------------------------------------------------------ 仓库打开 / 创建

    public static File gitDir(File dir) {
        return new File(dir, ".git");
    }

    public static boolean isRepo(File dir) {
        return new File(dir, ".git").isDirectory() || new File(dir, "HEAD").isFile();
    }

    /** 用 AndroidFS 打开仓库（不能走 Git.open，它使用默认 FS_POSIX 会硬链接失败） */
    public static Git open(File dir) throws IOException {
        Repository repo = new FileRepositoryBuilder()
                .setFS(AndroidFS.INSTANCE)
                .setGitDir(gitDir(dir))
                .setWorkTree(dir)
                .build();
        return new Git(repo);
    }

    private static CredentialsProvider credentials(Repo r) {
        if ("https".equals(r.authType)) {
            return new UsernamePasswordCredentialsProvider(r.username, r.token);
        }
        return null;
    }

    private static void setupSsh(Repo r) {
        if (!"ssh".equals(r.authType) || r.sshKey == null || r.sshKey.isEmpty()) {
            return;
        }
        final byte[] key = r.sshKey.getBytes(StandardCharsets.UTF_8);
        final byte[] pass = (r.sshPassphrase == null || r.sshPassphrase.isEmpty())
                ? null : r.sshPassphrase.getBytes(StandardCharsets.UTF_8);
        SshSessionFactory.setInstance(new JschConfigSessionFactory() {
            @Override
            protected void configure(OpenSshConfig.Host host, Session session) {
                // Android 上没有 ~/.ssh/known_hosts，关闭主机密钥校验以便开箱即用
                session.setConfig("StrictHostKeyChecking", "no");
            }

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch jsch = super.createDefaultJSch(fs);
                jsch.addIdentity("gitsync", key, null, pass);
                return jsch;
            }
        });
    }

    /** 写仓库级配置：作者、SSL 校验 */
    private static void applyConfig(Git git, Context ctx, Repo r) {
        try {
            StoredConfig cfg = git.getRepository().getConfig();
            Prefs p = new Prefs(ctx);
            cfg.setString("user", null, "name", p.authorName());
            cfg.setString("user", null, "email", p.authorEmail());
            cfg.setBoolean("http", null, "sslVerify", r.sslVerify);
            cfg.save();
        } catch (Exception e) {
            // 配置写失败不影响主流程
        }
    }

    private static String remoteName(Repo r) {
        return "origin";
    }

    private static String currentBranch(Git git) {
        try {
            String full = git.getRepository().getFullBranch();
            return full == null ? "master" : full;
        } catch (Exception e) {
            return "master";
        }
    }

    private static String shortBranch(Git git) {
        try {
            String b = git.getRepository().getBranch();
            return b == null ? "master" : b;
        } catch (Exception e) {
            return "master";
        }
    }

    public static String message(Context ctx, Repo r) {
        String t = (r.messageTemplate == null || r.messageTemplate.isEmpty())
                ? new Prefs(ctx).messageTemplate() : r.messageTemplate;
        String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        return t.replace("%date", date);
    }

    // ------------------------------------------------------------ clone / init

    public static Result cloneRepo(Context ctx, Repo r, Log log) {
        File dir = new File(r.localPath);
        try {
            if (dir.exists() && isRepo(dir)) {
                log.line("目录已是仓库，跳过克隆");
                return new Result(true, "已是仓库");
            }
            if (dir.exists() && dir.listFiles() != null && dir.listFiles().length > 0) {
                return new Result(false, "目标目录不是空的，不能克隆进去");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                return new Result(false, "无法创建目录：" + r.localPath);
            }
            setupSsh(r);
            org.eclipse.jgit.api.CloneCommand c = Git.cloneRepository()
                    .setFs(AndroidFS.INSTANCE)
                    .setURI(r.remoteUrl)
                    .setDirectory(dir)
                    .setTimeout(60);
            if (r.branch != null && !r.branch.isEmpty()) {
                c.setBranch(r.branch);
            }
            CredentialsProvider cp = credentials(r);
            if (cp != null) c.setCredentialsProvider(cp);
            log.line("克隆 " + r.remoteUrl + " …");
            try (Git git = c.call()) {
                applyConfig(git, ctx, r);
                log.line("克隆完成，分支 " + shortBranch(git));
            }
            if (r.cloneDepth > 0) {
                log.line("提示：当前 JGit 版本不提供浅克隆参数，已执行完整克隆");
            }
            return new Result(true, "克隆完成");
        } catch (Exception e) {
            String m = err(e);
            log.line("克隆失败：" + m);
            return new Result(false, m);
        }
    }

    public static Result initRepo(Context ctx, Repo r, Log log) {
        File dir = new File(r.localPath);
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return new Result(false, "无法创建目录：" + r.localPath);
            }
            try (Git git = Git.init().setFs(AndroidFS.INSTANCE).setDirectory(dir).call()) {
                applyConfig(git, ctx, r);
                if (r.remoteUrl != null && !r.remoteUrl.isEmpty()) {
                    addRemote(git.getRepository(), "origin", r.remoteUrl);
                }
                log.line("已初始化空仓库 " + r.localPath);
            }
            return new Result(true, "已初始化仓库");
        } catch (Exception e) {
            String m = err(e);
            log.line("初始化失败：" + m);
            return new Result(false, m);
        }
    }

    // ------------------------------------------------------------ 单步操作

    public static Result fetch(Context ctx, Repo r, Log log) {
        try (Git git = open(new File(r.localPath))) {
            applyConfig(git, ctx, r);
            setupSsh(r);
            org.eclipse.jgit.api.FetchCommand c = git.fetch().setRemote(remoteName(r)).setTimeout(60);
            CredentialsProvider cp = credentials(r);
            if (cp != null) c.setCredentialsProvider(cp);
            c.call();
            log.line("获取（fetch）完成");
            return new Result(true, "获取完成");
        } catch (Exception e) {
            return fail(log, "获取", e);
        }
    }

    public static Result pull(Context ctx, Repo r, Log log) {
        try (Git git = open(new File(r.localPath))) {
            applyConfig(git, ctx, r);
            setupSsh(r);
            org.eclipse.jgit.api.PullCommand c = git.pull()
                    .setRemote(remoteName(r))
                    .setRemoteBranchName(shortBranch(git))
                    .setTimeout(60);
            CredentialsProvider cp = credentials(r);
            if (cp != null) c.setCredentialsProvider(cp);
            PullResult pr = c.call();
            if (!pr.isSuccessful()) {
                Status st = git.status().call();
                Result res = new Result(false, "拉取出现冲突，共 " + st.getConflicting().size() + " 个文件");
                res.conflicts.addAll(st.getConflicting());
                log.line(res.message + "（去「冲突」里处理）");
                return res;
            }
            log.line("拉取（pull）完成，已是最新");
            return new Result(true, "拉取完成");
        } catch (Exception e) {
            return fail(log, "拉取", e);
        }
    }

    public static Result stage(Context ctx, Repo r, Log log) {
        try (Git git = open(new File(r.localPath))) {
            Status st = git.status().call();
            int n = count(st);
            git.add().addFilepattern(".").call();
            for (String p : st.getMissing()) {
                try {
                    git.rm().addFilepattern(p).call();
                } catch (Exception ignored) {
                }
            }
            log.line("暂存完成，共 " + n + " 个变更");
            Result res = new Result(true, "已暂存 " + n + " 个变更");
            res.changed = n;
            return res;
        } catch (Exception e) {
            return fail(log, "暂存", e);
        }
    }

    public static Result commit(Context ctx, Repo r, String msg, Log log) {
        try (Git git = open(new File(r.localPath))) {
            applyConfig(git, ctx, r);
            Status st = git.status().call();
            if (count(st) == 0) {
                log.line("没有需要提交的修改");
                return new Result(true, "没有修改，无需提交");
            }
            git.add().addFilepattern(".").call();
            for (String p : st.getMissing()) {
                try {
                    git.rm().addFilepattern(p).call();
                } catch (Exception ignored) {
                }
            }
            RevCommit c = git.commit().setMessage(msg)
                    .setAuthor(new Prefs(ctx).authorName(), new Prefs(ctx).authorEmail())
                    .setCommitter(new Prefs(ctx).authorName(), new Prefs(ctx).authorEmail())
                    .call();
            log.line("提交 " + c.name().substring(0, 7) + "：" + msg);
            Result res = new Result(true, "提交成功 " + c.name().substring(0, 7));
            res.committed = true;
            return res;
        } catch (Exception e) {
            return fail(log, "提交", e);
        }
    }

    public static Result push(Context ctx, Repo r, Log log) {
        try (Git git = open(new File(r.localPath))) {
            applyConfig(git, ctx, r);
            setupSsh(r);
            String b = currentBranch(git);
            org.eclipse.jgit.api.PushCommand c = git.push()
                    .setRemote(remoteName(r))
                    .setRefSpecs(new RefSpec(b + ":" + b))
                    .setTimeout(60);
            CredentialsProvider cp = credentials(r);
            if (cp != null) c.setCredentialsProvider(cp);
            c.call();
            log.line("推送（push）完成 → " + b);
            Result res = new Result(true, "推送完成");
            res.pushed = true;
            return res;
        } catch (Exception e) {
            return fail(log, "推送", e);
        }
    }

    public static Result status(Context ctx, Repo r, Log log) {
        try (Git git = open(new File(r.localPath))) {
            Status st = git.status().call();
            List<String> lines = statusLines(st);
            for (String s : lines) log.line("  " + s);
            log.line("分支 " + shortBranch(git) + "，共 " + lines.size() + " 项");
            Result res = new Result(true, "共 " + lines.size() + " 项变更");
            res.changed = lines.size();
            return res;
        } catch (Exception e) {
            return fail(log, "状态", e);
        }
    }

    /** 一键同步：拉取 → 暂存本地修改 → 提交 → 推送 */
    public static Result sync(Context ctx, Repo r, Log log) {
        File dir = new File(r.localPath);
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                return new Result(false, "无法创建目录：" + r.localPath);
            }
            if (!isRepo(dir)) {
                if (r.remoteUrl == null || r.remoteUrl.isEmpty()) {
                    return new Result(false, "本地不是 git 仓库，也没填远程地址（先克隆或初始化）");
                }
                Result c = cloneRepo(ctx, r, log);
                if (!c.ok) return c;
            }
            try (Git git = open(dir)) {
                applyConfig(git, ctx, r);
                setupSsh(r);

                // 1. 拉取远程
                if (r.remoteUrl != null && !r.remoteUrl.isEmpty()) {
                    log.line("[1/4] 拉取远程…");
                    org.eclipse.jgit.api.PullCommand c = git.pull()
                            .setRemote(remoteName(r))
                            .setRemoteBranchName(shortBranch(git))
                            .setTimeout(60);
                    CredentialsProvider cp = credentials(r);
                    if (cp != null) c.setCredentialsProvider(cp);
                    PullResult pr = c.call();
                    if (!pr.isSuccessful()) {
                        Status st = git.status().call();
                        Result res = new Result(false, "拉取冲突，需先处理 " + st.getConflicting().size() + " 个文件");
                        res.conflicts.addAll(st.getConflicting());
                        log.line("!! " + res.message);
                        return res;
                    }
                    log.line("      远程已合并");
                } else {
                    log.line("[1/4] 没有远程地址，跳过拉取");
                }

                // 2. 检查本地修改
                Status st = git.status().call();
                if (!st.getConflicting().isEmpty()) {
                    Result res = new Result(false, "存在未解决的冲突：" + st.getConflicting().size());
                    res.conflicts.addAll(st.getConflicting());
                    return res;
                }
                int n = count(st);
                Result out = new Result(true, "");
                out.changed = n;

                // 3. 有修改就提交
                if (n > 0) {
                    log.line("[2/4] 暂存 " + n + " 个本地修改…");
                    git.add().addFilepattern(".").call();
                    for (String p : st.getMissing()) {
                        try {
                            git.rm().addFilepattern(p).call();
                        } catch (Exception ignored) {
                        }
                    }
                    String msg = message(ctx, r);
                    RevCommit c = git.commit().setMessage(msg)
                            .setAuthor(new Prefs(ctx).authorName(), new Prefs(ctx).authorEmail())
                            .setCommitter(new Prefs(ctx).authorName(), new Prefs(ctx).authorEmail())
                            .call();
                    out.committed = true;
                    log.line("[3/4] 提交 " + c.name().substring(0, 7) + "：" + msg);
                } else {
                    log.line("[2/4] 本地没有修改，跳过暂存");
                    log.line("[3/4] 跳过提交");
                }

                // 4. 推送（即使本次没新提交，也可能有历史未推送的提交）
                if (r.remoteUrl != null && !r.remoteUrl.isEmpty()) {
                    log.line("[4/4] 推送…");
                    String b = currentBranch(git);
                    org.eclipse.jgit.api.PushCommand c2 = git.push()
                            .setRemote(remoteName(r))
                            .setRefSpecs(new RefSpec(b + ":" + b))
                            .setTimeout(60);
                    CredentialsProvider cp2 = credentials(r);
                    if (cp2 != null) c2.setCredentialsProvider(cp2);
                    c2.call();
                    out.pushed = true;
                    log.line("      推送完成");
                } else {
                    log.line("[4/4] 没有远程地址，跳过推送");
                }
                out.message = "同步完成"
                        + (out.committed ? "，提交 " + n + " 个修改" : "，无本地修改")
                        + (out.pushed ? "，已推送" : "");
                log.line("== " + out.message + " ==");
                return out;
            }
        } catch (Exception e) {
            return fail(log, "同步", e);
        }
    }

    // ------------------------------------------------------------ 历史 / diff

    public static List<CommitInfo> history(Context ctx, Repo r, int max) throws Exception {
        List<CommitInfo> out = new ArrayList<>();
        try (Git git = open(new File(r.localPath))) {
            for (RevCommit c : git.log().setMaxCount(max).call()) {
                out.add(new CommitInfo(c.name(), c.getShortMessage(), c.getFullMessage(),
                        c.getAuthorIdent().getName(), c.getCommitTime() * 1000L));
            }
        }
        return out;
    }

    /** 某个提交相对父提交的补丁文本 */
    public static String commitDiff(Context ctx, Repo r, String hash) throws Exception {
        try (Git git = open(new File(r.localPath))) {
            Repository repo = git.getRepository();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DiffFormatter df = new DiffFormatter(bos);
            df.setRepository(repo);
            try (RevWalk rw = new RevWalk(repo)) {
                RevCommit c = rw.parseCommit(repo.resolve(hash));
                CanonicalTreeParser newTree = new CanonicalTreeParser();
                try (org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader()) {
                    newTree.reset(reader, c.getTree());
                }
                List<DiffEntry> entries;
                if (c.getParentCount() > 0) {
                    RevCommit parent = rw.parseCommit(c.getParent(0).getId());
                    CanonicalTreeParser oldTree = new CanonicalTreeParser();
                    try (org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader()) {
                        oldTree.reset(reader, parent.getTree());
                    }
                    entries = df.scan(oldTree, newTree);
                } else {
                    entries = df.scan(new EmptyTreeIterator(), newTree);
                }
                for (DiffEntry e : entries) {
                    bos.write(("\n=== " + e.getChangeType() + " " + e.getNewPath() + " ===\n")
                            .getBytes(StandardCharsets.UTF_8));
                    df.format(e);
                }
            }
            df.close();
            String s = bos.toString("UTF-8");
            return s.isEmpty() ? "（这个提交没有文件变更）" : s;
        }
    }

    /** 工作树当前的 diff（未提交的修改） */
    public static String workingDiff(Context ctx, Repo r) throws Exception {
        try (Git git = open(new File(r.localPath))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            git.diff().setOutputStream(bos).call();
            String s = bos.toString("UTF-8");
            return s.isEmpty() ? "（工作区没有未暂存的内容差异）" : s;
        }
    }

    // ------------------------------------------------------------ 分支

    public static List<String[]> branches(Context ctx, Repo r) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (Git git = open(new File(r.localPath))) {
            String cur = currentBranch(git);
            for (Ref ref : git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()) {
                String name = ref.getName();
                if (name.startsWith("refs/heads/")) {
                    out.add(new String[]{name.substring("refs/heads/".length()),
                            name.equals(cur) ? "当前分支" : "本地"});
                } else if (name.startsWith("refs/remotes/")) {
                    out.add(new String[]{name.substring("refs/remotes/".length()), "远程"});
                }
            }
        }
        return out;
    }

    public static Result createBranch(Context ctx, Repo r, String name) {
        try (Git git = open(new File(r.localPath))) {
            git.branchCreate().setName(name).call();
            return new Result(true, "已创建分支 " + name);
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    public static Result checkout(Context ctx, Repo r, String name) {
        try (Git git = open(new File(r.localPath))) {
            git.checkout().setName(name).call();
            return new Result(true, "已切换到 " + name);
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    public static Result deleteBranch(Context ctx, Repo r, String name) {
        try (Git git = open(new File(r.localPath))) {
            git.branchDelete().setBranchNames(name).call();
            return new Result(true, "已删除分支 " + name);
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    public static Result renameBranch(Context ctx, Repo r, String oldName, String newName) {
        try (Git git = open(new File(r.localPath))) {
            git.branchRename().setOldName(oldName).setNewName(newName).call();
            return new Result(true, "已重命名为 " + newName);
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    // ------------------------------------------------------------ 远程

    private static void addRemote(Repository repo, String name, String url) throws IOException {
        StoredConfig cfg = repo.getConfig();
        cfg.setString("remote", name, "url", url);
        cfg.setString("remote", name, "fetch",
                "+refs/heads/*:refs/remotes/" + name + "/*");
        cfg.save();
    }

    public static List<String[]> remotes(Context ctx, Repo r) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (Git git = open(new File(r.localPath))) {
            StoredConfig cfg = git.getRepository().getConfig();
            for (String name : cfg.getSubsections("remote")) {
                out.add(new String[]{name, String.valueOf(cfg.getString("remote", name, "url"))});
            }
        }
        return out;
    }

    public static Result remoteAdd(Context ctx, Repo r, String name, String url) {
        try (Git git = open(new File(r.localPath))) {
            addRemote(git.getRepository(), name, url);
            return new Result(true, "已添加远程 " + name);
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    public static Result remoteRemove(Context ctx, Repo r, String name) {
        try (Git git = open(new File(r.localPath))) {
            StoredConfig cfg = git.getRepository().getConfig();
            cfg.unsetSection("remote", name);
            cfg.save();
            return new Result(true, "已删除远程 " + name);
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    public static Result remoteSetUrl(Context ctx, Repo r, String name, String url) {
        try (Git git = open(new File(r.localPath))) {
            addRemote(git.getRepository(), name, url);
            return new Result(true, "已更新 " + name + " 的地址");
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    // ------------------------------------------------------------ 冲突

    public static List<String> conflicts(Context ctx, Repo r) {
        try (Git git = open(new File(r.localPath))) {
            return new ArrayList<>(git.status().call().getConflicting());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static Result resolveConflict(Context ctx, Repo r, String path, boolean ours) {
        try (Git git = open(new File(r.localPath))) {
            CheckoutCommand c = git.checkout()
                    .setStage(ours ? CheckoutCommand.Stage.OURS : CheckoutCommand.Stage.THEIRS);
            c.addPath(path);
            c.call();
            git.add().addFilepattern(path).call();
            return new Result(true, "已用" + (ours ? "本地" : "远程") + "版本覆盖 " + path + "，记得提交");
        } catch (Exception e) {
            return new Result(false, err(e));
        }
    }

    // ------------------------------------------------------------ 文件 / 忽略规则

    public static List<File> listFiles(File dir, int max) {
        List<File> out = new ArrayList<>();
        collectFiles(dir, dir, out, max, 0);
        return out;
    }

    private static void collectFiles(File root, File cur, List<File> out, int max, int depth) {
        if (depth > 6 || out.size() >= max) return;
        File[] kids = cur.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (out.size() >= max) return;
            if (f.getName().equals(".git")) continue;
            if (f.isDirectory()) {
                collectFiles(root, f, out, max, depth + 1);
            } else {
                out.add(f);
            }
        }
    }

    public static String relative(File root, File f) {
        String r = root.getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(r) ? p.substring(r.length() + 1) : p;
    }

    public static String readText(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static void writeText(File f, String s) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    public static File ignoreFile(Repo r) {
        return new File(r.localPath, ".gitignore");
    }

    public static File excludeFile(Repo r) {
        return new File(gitDir(new File(r.localPath)), "info/exclude");
    }

    // ------------------------------------------------------------ 辅助

    public static int count(Status st) {
        return st.getAdded().size() + st.getChanged().size() + st.getRemoved().size()
                + st.getModified().size() + st.getMissing().size()
                + st.getUntracked().size() + st.getConflicting().size();
    }

    public static List<String> statusLines(Status st) {
        List<String> out = new ArrayList<>();
        addLines(out, "A 新增", st.getAdded());
        addLines(out, "M 修改", st.getModified());
        addLines(out, "C 变更", st.getChanged());
        addLines(out, "D 已删", st.getRemoved());
        addLines(out, "! 丢失", st.getMissing());
        addLines(out, "? 未跟踪", st.getUntracked());
        addLines(out, "X 冲突", st.getConflicting());
        return out;
    }

    private static void addLines(List<String> out, String prefix, java.util.Set<String> set) {
        for (String s : set) out.add(prefix + "  " + s);
    }

    private static Result fail(Log log, String op, Exception e) {
        String m = err(e);
        log.line(op + "失败：" + m);
        return new Result(false, op + "失败：" + m);
    }

    public static String err(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        if (e.getCause() != null && e.getCause() != e) {
            String c = e.getCause().getMessage();
            if (c != null && !c.isEmpty() && !c.equals(m)) m += " / " + c;
        }
        return m;
    }
}
