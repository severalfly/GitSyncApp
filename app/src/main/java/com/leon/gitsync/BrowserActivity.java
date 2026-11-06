package com.leon.gitsync;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 通用列表页：提交历史 / 状态 / 分支 / 远程 / 文件 / 冲突 / 差异 */
public class BrowserActivity extends AppCompatActivity {

    private String mode;
    private Repo repo;
    private RepoStore store;
    private final List<String[]> rows = new ArrayList<>();
    private ArrayAdapter<String[]> adapter;
    private View inputRow;
    private EditText input;
    private TextView hintText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        store = new RepoStore(this);
        mode = getIntent().getStringExtra("mode");
        String id = getIntent().getStringExtra("repoId");
        repo = store.find(id == null ? "" : id);
        if (repo == null || mode == null) {
            finish();
            return;
        }

        TextView title = findViewById(R.id.title);
        hintText = findViewById(R.id.hint);
        inputRow = findViewById(R.id.input_row);
        input = findViewById(R.id.input);
        Button action = findViewById(R.id.action);
        ListView list = findViewById(R.id.list);
        TextView empty = findViewById(R.id.empty);
        list.setEmptyView(empty);

        adapter = new ArrayAdapter<String[]>(this, R.layout.item_two_line, R.id.line1, rows) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = LayoutInflater.from(getContext())
                            .inflate(R.layout.item_two_line, parent, false);
                }
                String[] row = getItem(position);
                ((TextView) v.findViewById(R.id.line1)).setText(row[0]);
                ((TextView) v.findViewById(R.id.line2)).setText(row[1]);
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id2) -> onRowClick(rows.get(pos)));

        switch (mode) {
            case "history":
                title.setText("提交历史");
                hintText.setText("点某条提交查看改了哪些文件（diff）");
                break;
            case "status":
                title.setText("工作区状态");
                hintText.setText("点任意一行查看未提交的内容差异");
                break;
            case "branch":
                title.setText("分支管理");
                hintText.setText("输入新分支名后点「新建」；点已有分支可切换 / 重命名 / 删除");
                input.setHint("新分支名");
                action.setText("新建");
                action.setOnClickListener(v -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) {
                        toast("先填分支名");
                        return;
                    }
                    act(GitEngine.createBranch(this, repo, n));
                });
                break;
            case "remote":
                title.setText("远程管理");
                hintText.setText("点新建可添加远程；点已有远程可改地址 / 删除");
                input.setHint("新远程名称");
                action.setText("添加");
                action.setOnClickListener(v -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) n = "origin";
                    askTwoInputs("添加远程", "名称", n, "地址", "", (a, b) ->
                            act(GitEngine.remoteAdd(this, repo, a, b)));
                });
                break;
            case "files":
                title.setText("文件浏览");
                hintText.setText("点文本文件可以直接编辑保存（.git 目录已隐藏）");
                break;
            case "conflicts":
                title.setText("合并冲突");
                hintText.setText("点冲突文件选择使用本地版或远程版，处理后记得提交");
                break;
            case "diff":
                title.setText("提交内容差异");
                hintText.setText(repo.name);
                break;
            case "workdiff":
                title.setText("未提交的内容差异");
                hintText.setText(repo.name);
                break;
            default:
                break;
        }
        boolean needInput = "branch".equals(mode) || "remote".equals(mode);
        inputRow.setVisibility(needInput ? View.VISIBLE : View.GONE);
        load();
    }

    private void load() {
        rows.clear();
        adapter.notifyDataSetChanged();
        new Thread(() -> {
            List<String[]> result = new ArrayList<>();
            String err = null;
            try {
                switch (mode) {
                    case "history":
                        for (GitEngine.CommitInfo c : GitEngine.history(this, repo, 100)) {
                            String d = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                    .format(new Date(c.time));
                            result.add(new String[]{c.shortMessage,
                                    c.hash.substring(0, 7) + "  " + c.author + "  " + d,
                                    c.hash});
                        }
                        break;
                    case "status":
                        try (org.eclipse.jgit.api.Git git =
                                     GitEngine.open(new File(repo.localPath))) {
                            for (String s : GitEngine.statusLines(git.status().call())) {
                                result.add(new String[]{s, "点这行看内容差异", "workdiff"});
                            }
                        }
                        break;
                    case "branch":
                        for (String[] b : GitEngine.branches(this, repo)) {
                            result.add(new String[]{b[0], b[1], b[0]});
                        }
                        break;
                    case "remote":
                        for (String[] r : GitEngine.remotes(this, repo)) {
                            result.add(new String[]{r[0], r[1], r[0]});
                        }
                        break;
                    case "files":
                        File root = new File(repo.localPath);
                        for (File f : GitEngine.listFiles(root, 500)) {
                            result.add(new String[]{GitEngine.relative(root, f),
                                    f.length() + " B", f.getAbsolutePath()});
                        }
                        break;
                    case "conflicts":
                        for (String p : GitEngine.conflicts(this, repo)) {
                            result.add(new String[]{p, "点这里处理", p});
                        }
                        break;
                    case "diff": {
                        String hash = getIntent().getStringExtra("hash");
                        String d = hash == null ? "（没有指定提交）"
                                : GitEngine.commitDiff(this, repo, hash);
                        for (String line : d.split("\n")) {
                            result.add(new String[]{line, "", ""});
                        }
                        break;
                    }
                    case "workdiff": {
                        String d = GitEngine.workingDiff(this, repo);
                        for (String line : d.split("\n")) {
                            result.add(new String[]{line, "", ""});
                        }
                        break;
                    }
                    default:
                        break;
                }
            } catch (Exception e) {
                err = GitEngine.err(e);
            }
            final String error = err;
            runOnUiThread(() -> {
                rows.clear();
                rows.addAll(result);
                adapter.notifyDataSetChanged();
                if (error != null) {
                    toast("读取失败：" + error);
                } else if (rows.isEmpty()) {
                    String s = "conflicts".equals(mode) ? "没有冲突，很干净"
                            : "status".equals(mode) ? "工作区没有改动" : "这里还没有内容";
                    findViewById(R.id.empty).setAlpha(1f);
                    ((TextView) findViewById(R.id.empty)).setText(s);
                }
            });
        }).start();
    }

    private void onRowClick(String[] row) {
        String payload = row.length > 2 ? row[2] : "";
        switch (mode) {
            case "history":
                Intent i = new Intent(this, BrowserActivity.class);
                i.putExtra("repoId", repo.id);
                i.putExtra("mode", "diff");
                i.putExtra("hash", payload);
                startActivity(i);
                break;
            case "status":
                Intent w = new Intent(this, BrowserActivity.class);
                w.putExtra("repoId", repo.id);
                w.putExtra("mode", "workdiff");
                startActivity(w);
                break;
            case "branch":
                branchMenu(row[0]);
                break;
            case "remote":
                remoteMenu(row[0], row[1]);
                break;
            case "files":
                Intent f = new Intent(this, TextEditActivity.class);
                f.putExtra("path", payload);
                startActivity(f);
                break;
            case "conflicts":
                conflictMenu(row[0]);
                break;
            default:
                break;
        }
    }

    private void branchMenu(String name) {
        new AlertDialog.Builder(this)
                .setTitle("分支 " + name)
                .setItems(new String[]{"切换到这个分支", "重命名", "删除"}, (d, which) -> {
                    if (which == 0) {
                        act(GitEngine.checkout(this, repo, name));
                    } else if (which == 1) {
                        askOneInput("重命名分支", name, s ->
                                act(GitEngine.renameBranch(this, repo, name, s)));
                    } else {
                        confirm("删除分支 " + name + "？", () ->
                                act(GitEngine.deleteBranch(this, repo, name)));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void remoteMenu(String name, String url) {
        new AlertDialog.Builder(this)
                .setTitle("远程 " + name + "\n" + url)
                .setItems(new String[]{"修改地址", "删除"}, (d, which) -> {
                    if (which == 0) {
                        askTwoInputs("修改 " + name + " 的地址", "名称", name, "地址", url,
                                (a, b) -> act(GitEngine.remoteSetUrl(this, repo, a, b)));
                    } else {
                        confirm("删除远程 " + name + "？", () ->
                                act(GitEngine.remoteRemove(this, repo, name)));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void conflictMenu(String path) {
        new AlertDialog.Builder(this)
                .setTitle("冲突：" + path)
                .setItems(new String[]{"用本地版本", "用远程版本", "打开文件手动改"}, (d, which) -> {
                    if (which == 0) {
                        act(GitEngine.resolveConflict(this, repo, path, true));
                    } else if (which == 1) {
                        act(GitEngine.resolveConflict(this, repo, path, false));
                    } else {
                        Intent i = new Intent(this, TextEditActivity.class);
                        i.putExtra("path", new File(repo.localPath, path).getAbsolutePath());
                        startActivity(i);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void act(GitEngine.Result r) {
        toast(r.message);
        load();
    }

    private void askOneInput(String title, String def, java.util.function.Consumer<String> onOk) {
        EditText e = new EditText(this);
        e.setText(def);
        new AlertDialog.Builder(this).setTitle(title).setView(e)
                .setPositiveButton("确定", (d, w) -> {
                    String s = e.getText().toString().trim();
                    if (!s.isEmpty()) onOk.accept(s);
                })
                .setNegativeButton("取消", null).show();
    }

    private void askTwoInputs(String title, String l1, String v1, String l2, String v2,
                              BiConsumer onOk) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 12, 24, 12);
        EditText e1 = new EditText(this);
        e1.setHint(l1);
        e1.setText(v1);
        EditText e2 = new EditText(this);
        e2.setHint(l2);
        e2.setText(v2);
        box.addView(e1);
        box.addView(e2);
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    String a = e1.getText().toString().trim();
                    String b = e2.getText().toString().trim();
                    if (!a.isEmpty() && !b.isEmpty()) onOk.accept(a, b);
                })
                .setNegativeButton("取消", null).show();
    }

    interface BiConsumer {
        void accept(String a, String b);
    }

    private void confirm(String msg, Runnable onOk) {
        new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton("确定", (d, w) -> onOk.run())
                .setNegativeButton("取消", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
