package com.leon.gitsync;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** 全局设置 */
public class SettingsActivity extends AppCompatActivity {

    private FormBuilder form;
    private TextView output;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("设置");
        Prefs p = new Prefs(this);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 16, 24, 24);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        setContentView(sv);

        form = new FormBuilder(this, box);
        form.section("提交身份");
        form.text("authorName", "作者名", p.authorName());
        form.text("authorEmail", "作者邮箱", p.authorEmail());
        form.text("messageTemplate", "默认自动提交消息（%date = 当前时间）", p.messageTemplate());

        form.section("自动同步");
        form.number("interval", "定时同步间隔（分钟，0 = 关闭）", p.intervalMinutes());
        form.check("syncOnOpen", "打开应用时同步全部仓库", p.syncOnOpen());
        form.check("syncOnClose", "退出应用时同步全部仓库", p.syncOnClose());
        form.check("retryOnNetwork", "网络恢复后自动重试失败的同步", p.retryOnNetwork());
        form.check("notifyResult", "同步完成后显示通知", p.notifyResult());

        form.section("操作");
        form.button("保存设置", v -> save());
        form.button("运行自检（验证 git 引擎是否可用）", v -> selfTest());
        form.button("存储权限（同步 /sdcard 下的目录需要）", v -> openStorageSettings());

        output = new TextView(this);
        output.setTextSize(12);
        output.setPadding(0, 16, 0, 8);
        box.addView(output);
        out("提示：默认仓库放在应用私有目录，无需任何权限；\n"
                + "要同步手机里已有的文件夹，请开启「所有文件访问权限」后手填路径。");
    }

    private void save() {
        Prefs p = new Prefs(this);
        p.put("authorName", form.get("authorName"));
        p.put("authorEmail", form.get("authorEmail"));
        p.put("messageTemplate", form.get("messageTemplate"));
        int mins = form.number("interval", 0);
        p.put("interval", mins);
        p.put("syncOnOpen", form.checked("syncOnOpen"));
        p.put("syncOnClose", form.checked("syncOnClose"));
        p.put("retryOnNetwork", form.checked("retryOnNetwork"));
        p.put("notifyResult", form.checked("notifyResult"));
        SyncUtil.schedule(this, mins);
        Toast.makeText(this, mins > 0 ? "已保存，每 " + mins + " 分钟同步一次" : "已保存",
                Toast.LENGTH_SHORT).show();
    }

    private void selfTest() {
        out("开始自检…");
        form.get("authorName"); // 保持表单值，不参与自检
        new Thread(() -> {
            String result = SyncUtil.selfTest(this, this::out);
            runOnUiThread(() -> out(result));
        }).start();
    }

    private void openStorageSettings() {
        if (Build.VERSION.SDK_INT >= 30) {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 13);
        }
    }

    private void out(String s) {
        runOnUiThread(() -> {
            output.append(s + "\n");
            if (output.getLineCount() > 60) output.setText(s);
        });
    }
}
