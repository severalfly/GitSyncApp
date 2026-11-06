package com.leon.gitsync;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 首页：多个 git 仓库的列表 */
public class MainActivity extends AppCompatActivity {

    private RepoStore store;
    private final List<Repo> data = new ArrayList<>();
    private ArrayAdapter<Repo> adapter;
    private static long lastAutoSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = new RepoStore(this);

        ListView list = findViewById(R.id.list);
        View empty = findViewById(R.id.empty);
        list.setEmptyView(empty);
        adapter = new ArrayAdapter<Repo>(this, R.layout.item_repo, R.id.name, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    v = LayoutInflater.from(getContext()).inflate(R.layout.item_repo, parent, false);
                }
                final Repo r = getItem(position);
                ((TextView) v.findViewById(R.id.name)).setText(r.name);
                ((TextView) v.findViewById(R.id.path)).setText(r.localPath);
                TextView res = v.findViewById(R.id.result);
                String s = r.lastSync == 0 ? "还没有同步过"
                        : new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                .format(new Date(r.lastSync)) + "  " + r.lastResult;
                res.setText(s);
                res.setTextColor(r.lastResult.startsWith("失败") || r.lastResult.startsWith("冲突")
                        ? 0xFFDC2626 : 0xFF16A34A);
                Button b = v.findViewById(R.id.btn_sync);
                b.setText("同步");
                b.setOnClickListener(x -> syncOne(r));
                // 整行可点：列表项里有 Button 时，ListView 的 onItemClick 会被抢焦点，
                // 直接给行根布局绑点击最稳。
                v.setOnClickListener(x -> openRepo(r));
                return v;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            final Repo r = data.get(pos);
            new AlertDialog.Builder(this)
                    .setTitle(r.name)
                    .setMessage("删除这个仓库的配置？\n本地文件不会被删除。")
                    .setPositiveButton("删除", (d, w) -> {
                        store.remove(r.id);
                        refresh();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        findViewById(R.id.btn_add).setOnClickListener(v ->
                startActivity(new Intent(this, EditRepoActivity.class)));
        findViewById(R.id.btn_sync_all).setOnClickListener(v -> {
            if (data.isEmpty()) {
                Toast.makeText(this, "先添加一个仓库", Toast.LENGTH_SHORT).show();
            } else {
                SyncUtil.startSync(this);
                Toast.makeText(this, "开始同步全部仓库", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (new Prefs(this).syncOnOpen()) autoSync();
    }

    @Override
    protected void onStop() {
        if (new Prefs(this).syncOnClose()) autoSync();
        super.onStop();
    }

    private void autoSync() {
        if (System.currentTimeMillis() - lastAutoSync < 30000) return;
        lastAutoSync = System.currentTimeMillis();
        if (!data.isEmpty()) SyncUtil.startSync(this);
    }

    private void refresh() {
        data.clear();
        data.addAll(store.list());
        adapter.notifyDataSetChanged();
    }

    private void openRepo(Repo r) {
        Intent i = new Intent(this, RepoActivity.class);
        i.putExtra("repoId", r.id);
        startActivity(i);
    }

    private void syncOne(Repo r) {
        Toast.makeText(this, "正在同步 " + r.name, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            GitEngine.Result res = GitEngine.sync(this, r, s -> {
            });
            r.lastSync = System.currentTimeMillis();
            r.lastResult = (res.ok ? "成功 · " : "失败 · ") + res.message;
            store.save(r);
            runOnUiThread(() -> {
                refresh();
                Toast.makeText(this, r.name + "：" + r.lastResult, Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
