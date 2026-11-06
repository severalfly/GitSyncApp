package com.leon.gitsync;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/** 添加 / 编辑一个仓库 */
public class EditRepoActivity extends AppCompatActivity {

    private static final int REQ_FOLDER = 21;

    private Repo repo;
    private RepoStore store;
    private boolean isNew;
    private FormBuilder form;
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new RepoStore(this);
        String id = getIntent().getStringExtra("repoId");
        repo = id == null ? null : store.find(id);
        isNew = repo == null;
        if (isNew) repo = new Repo();
        setTitle(isNew ? "添加仓库" : "仓库设置：" + repo.name);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 16, 24, 24);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);

        form = new FormBuilder(this, box);
        form.section("基本");
        form.text("name", "仓库名称", repo.name);
        File ext = getExternalFilesDir("repos");
        File defaultRoot = new File(ext == null ? getFilesDir() : ext, "repo1");
        form.text("localPath", "本地文件夹路径（可点「选择文件夹」）",
                repo.localPath.isEmpty() ? defaultRoot.getAbsolutePath() : repo.localPath);
        form.text("remoteUrl", "远程地址（https://… / git@… / file://…）", repo.remoteUrl);
        form.text("branch", "分支（留空跟随远程默认）", repo.branch);

        form.section("认证");
        form.text("authType", "认证方式：none / https / ssh", repo.authType);
        form.text("username", "用户名（HTTPS）", repo.username);
        form.password("token", "密码或 Personal Access Token", repo.token);
        form.multiline("sshKey", "SSH 私钥（粘贴 PEM 全文）", repo.sshKey, 3);
        form.password("sshPassphrase", "私钥口令（没有就留空）", repo.sshPassphrase);

        form.section("同步");
        form.text("messageTemplate", "自动提交消息（%date = 时间）", repo.messageTemplate);
        form.number("cloneDepth", "克隆深度（0=完整，当前仅支持完整克隆）", repo.cloneDepth);
        form.check("sslVerify", "校验 HTTPS 证书", repo.sslVerify);
        form.check("autoSync", "加入定时自动同步", repo.autoSync);
        form.check("syncOnOpen", "打开应用时同步", repo.syncOnOpen);
        form.check("syncOnClose", "退出应用时同步", repo.syncOnClose);

        form.section("操作");
        form.button("保存", v -> save(true));
        form.button("克隆远程仓库到这个目录", v -> {
            save(false);
            runGit(true);
        });
        form.button("初始化本地空仓库", v -> {
            save(false);
            runGit(false);
        });
        form.button("选择文件夹（自动填路径）", v -> pickFolder());
        form.button("编辑 .gitignore", v -> editText(GitEngine.ignoreFile(repo)));
        form.button("编辑 .git/info/exclude", v -> editText(GitEngine.excludeFile(repo)));
        form.button("存储权限（同步 /sdcard 下的目录需要）", v -> openStorageSettings());
        if (!isNew) {
            form.button("删除这个仓库的配置", v ->
                    new AlertDialog.Builder(this)
                            .setMessage("删除「" + repo.name + "」的配置？本地文件保留。")
                            .setPositiveButton("删除", (d, w) -> {
                                store.remove(repo.id);
                                finish();
                            })
                            .setNegativeButton("取消", null).show());
        }

        output = new TextView(this);
        output.setTextSize(12);
        output.setPadding(0, 16, 0, 8);
        box.addView(output);
    }

    private void save(boolean finish) {
        repo.name = form.get("name");
        if (repo.name.isEmpty()) {
            repo.name = new File(form.get("localPath")).getName();
        }
        repo.localPath = form.get("localPath");
        repo.remoteUrl = form.get("remoteUrl");
        repo.branch = form.get("branch");
        repo.authType = form.get("authType").isEmpty() ? "none" : form.get("authType");
        repo.username = form.get("username");
        repo.token = form.get("token");
        repo.sshKey = form.get("sshKey");
        repo.sshPassphrase = form.get("sshPassphrase");
        repo.messageTemplate = form.get("messageTemplate");
        repo.cloneDepth = form.number("cloneDepth", 0);
        repo.sslVerify = form.checked("sslVerify");
        repo.autoSync = form.checked("autoSync");
        repo.syncOnOpen = form.checked("syncOnOpen");
        repo.syncOnClose = form.checked("syncOnClose");
        store.save(repo);
        isNew = false;
        if (finish) {
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void runGit(boolean clone) {
        if (repo.remoteUrl.isEmpty() && clone) {
            out("先填远程地址再克隆");
            return;
        }
        out((clone ? "克隆" : "初始化") + "中…");
        new Thread(() -> {
            GitEngine.Result r = clone
                    ? GitEngine.cloneRepo(this, repo, this::out)
                    : GitEngine.initRepo(this, repo, this::out);
            runOnUiThread(() -> out((r.ok ? "完成：" : "失败：") + r.message));
        }).start();
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(i, REQ_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FOLDER || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        String path = pathFromTree(uri);
        if (path != null) {
            form.set("localPath", path);
            out("已填入路径：" + path);
        } else {
            out("这个目录无法自动转成路径，请手动填写（一般是 /storage/emulated/0/…）");
        }
    }

    private String pathFromTree(Uri uri) {
        try {
            String docId = DocumentsContract.getDocumentId(uri);
            if (docId.startsWith("raw:")) return docId.substring(4);
            if (docId.startsWith("primary:")) {
                return "/storage/emulated/0/" + docId.substring("primary:".length());
            }
            String[] parts = docId.split(":");
            if (parts.length >= 2) {
                return "/storage/" + parts[0] + "/" + parts[1];
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void editText(File f) {
        Intent i = new Intent(this, TextEditActivity.class);
        i.putExtra("path", f.getAbsolutePath());
        startActivity(i);
    }

    private void openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 12);
        }
    }

    private void out(String s) {
        runOnUiThread(() -> {
            output.append(s + "\n");
            if (output.getLineCount() > 30) {
                output.setText(s);
            }
        });
    }
}
