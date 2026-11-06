package com.leon.gitsync;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

/** 仓库详情：自动模式（一键同步）/ 手动模式（各操作单独点） */
public class RepoActivity extends AppCompatActivity {

    private Repo repo;
    private RepoStore store;
    private TextView logView;
    private ScrollView logScroll;
    private View panelAuto, panelManual;
    private Button btnAutoMode, btnManualMode;
    private boolean autoMode = true;

    interface Op {
        GitEngine.Result run(Context c, Repo r, GitEngine.Log l);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repo);
        store = new RepoStore(this);
        String id = getIntent().getStringExtra("repoId");
        repo = store.find(id == null ? "" : id);
        if (repo == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.title)).setText(repo.name);
        ((TextView) findViewById(R.id.subtitle)).setText(repo.localPath);
        logView = findViewById(R.id.log);
        logScroll = findViewById(R.id.log_scroll);
        panelAuto = findViewById(R.id.panel_auto);
        panelManual = findViewById(R.id.panel_manual);
        btnAutoMode = findViewById(R.id.btn_auto_mode);
        btnManualMode = findViewById(R.id.btn_manual_mode);

        btnAutoMode.setOnClickListener(v -> setMode(true));
        btnManualMode.setOnClickListener(v -> setMode(false));
        setMode(true);

        // 自动模式
        CheckBox cbAuto = findViewById(R.id.cb_auto);
        CheckBox cbOpen = findViewById(R.id.cb_open);
        CheckBox cbClose = findViewById(R.id.cb_close);
        cbAuto.setChecked(repo.autoSync);
        cbOpen.setChecked(repo.syncOnOpen);
        cbClose.setChecked(repo.syncOnClose);
        CompoundButton.OnCheckedChangeListener save = (b, checked) -> {
            repo.autoSync = cbAuto.isChecked();
            repo.syncOnOpen = cbOpen.isChecked();
            repo.syncOnClose = cbClose.isChecked();
            store.save(repo);
        };
        cbAuto.setOnCheckedChangeListener(save);
        cbOpen.setOnCheckedChangeListener(save);
        cbClose.setOnCheckedChangeListener(save);

        findViewById(R.id.btn_sync).setOnClickListener(v ->
                run("一键同步", GitEngine::sync));
        log("仓库：" + repo.name);
        log("路径：" + repo.localPath);
        if (!repo.remoteUrl.isEmpty()) log("远程：" + repo.remoteUrl);
        log("选择上方模式后点按钮开始");

        // 手动模式
        bind(R.id.op_fetch, "获取", GitEngine::fetch);
        bind(R.id.op_pull, "拉取", GitEngine::pull);
        bind(R.id.op_stage, "暂存", GitEngine::stage);
        bind(R.id.op_push, "推送", GitEngine::push);
        bind(R.id.op_status, "状态", GitEngine::status);

        findViewById(R.id.op_commit).setOnClickListener(v -> askCommit());
        findViewById(R.id.op_history).setOnClickListener(v -> browse("history"));
        findViewById(R.id.op_branch).setOnClickListener(v -> browse("branch"));
        findViewById(R.id.op_remote).setOnClickListener(v -> browse("remote"));
        findViewById(R.id.op_files).setOnClickListener(v -> browse("files"));
        findViewById(R.id.op_conflict).setOnClickListener(v -> browse("conflicts"));
        findViewById(R.id.op_edit).setOnClickListener(v -> {
            Intent i = new Intent(this, EditRepoActivity.class);
            i.putExtra("repoId", repo.id);
            startActivity(i);
        });
    }

    private void bind(int viewId, String title, Op op) {
        findViewById(viewId).setOnClickListener(v -> run(title, op));
    }

    private void setMode(boolean auto) {
        autoMode = auto;
        panelAuto.setVisibility(auto ? View.VISIBLE : View.GONE);
        panelManual.setVisibility(auto ? View.GONE : View.VISIBLE);
        btnAutoMode.setBackgroundTintList(ColorStateList.valueOf(
                auto ? 0xFF2D7FF9 : 0xFFE5E7EB));
        btnAutoMode.setTextColor(auto ? Color.WHITE : Color.DKGRAY);
        btnManualMode.setBackgroundTintList(ColorStateList.valueOf(
                auto ? 0xFFE5E7EB : 0xFF2D7FF9));
        btnManualMode.setTextColor(auto ? Color.DKGRAY : Color.WHITE);
    }

    private void browse(String mode) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.putExtra("repoId", repo.id);
        i.putExtra("mode", mode);
        startActivity(i);
    }

    private void askCommit() {
        EditText e = new EditText(this);
        e.setHint("提交消息");
        e.setText(GitEngine.message(this, repo));
        new AlertDialog.Builder(this)
                .setTitle("提交到本地仓库")
                .setView(e)
                .setPositiveButton("提交", (d, w) -> {
                    String msg = e.getText().toString().trim();
                    if (msg.isEmpty()) msg = "手动提交";
                    final String m = msg;
                    run("提交", (c, r, l) -> GitEngine.commit(c, r, m, l));
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void run(String title, Op op) {
        log("");
        log("---- " + title + " ----");
        setButtonsEnabled(false);
        new Thread(() -> {
            GitEngine.Result res;
            try {
                res = op.run(this, repo, this::log);
            } catch (Throwable t) {
                res = new GitEngine.Result(false, GitEngine.err(t));
                log("异常：" + res.message);
            }
            repo.lastSync = System.currentTimeMillis();
            repo.lastResult = (res.ok ? "成功 · " : "失败 · ") + res.message;
            store.save(repo);
            final GitEngine.Result r = res;
            runOnUiThread(() -> {
                setButtonsEnabled(true);
                log("结果：" + r.message);
                if (!r.conflicts.isEmpty()) {
                    log("冲突文件：" + String.join(", ", r.conflicts));
                }
                Toast.makeText(this, r.message, Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void setButtonsEnabled(boolean on) {
        findViewById(R.id.btn_sync).setEnabled(on);
        int[] ids = {R.id.op_fetch, R.id.op_pull, R.id.op_stage, R.id.op_commit,
                R.id.op_push, R.id.op_status};
        for (int id : ids) findViewById(id).setEnabled(on);
    }

    private void log(String s) {
        runOnUiThread(() -> {
            logView.append(s + "\n");
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Repo fresh = store.find(repo.id);
        if (fresh != null) repo = fresh;
    }
}
