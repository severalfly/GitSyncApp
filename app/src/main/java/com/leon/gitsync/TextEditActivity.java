package com.leon.gitsync;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** 仓库内文本文件 / .gitignore / exclude 的编辑，退出时自动保存 */
public class TextEditActivity extends AppCompatActivity {

    private File file;
    private EditText edit;
    private String original = "";
    private boolean editable = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        setTitle(file.getName());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        TextView info = new TextView(this);
        info.setText(file.getAbsolutePath());
        info.setTextSize(11);
        root.addView(info);

        Button save = new Button(this);
        save.setText("保存");
        save.setOnClickListener(v -> doSave());
        root.addView(save);

        edit = new EditText(this);
        edit.setTextSize(13);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setPadding(8, 8, 8, 8);
        root.addView(edit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        try {
            if (file.exists()) {
                if (file.length() > 1024 * 1024) {
                    editable = false;
                    edit.setText("文件超过 1MB，不在应用里编辑");
                } else {
                    byte[] bytes = Files.readAllBytes(file.toPath());
                    String s = new String(bytes, StandardCharsets.UTF_8);
                    if (s.indexOf('\0') >= 0) {
                        editable = false;
                        edit.setText("这是二进制文件，不能当文本编辑");
                    } else {
                        original = s;
                        edit.setText(s);
                    }
                }
            } else {
                edit.setText("");
            }
            edit.setEnabled(editable);
        } catch (Exception e) {
            editable = false;
            edit.setText("读取失败：" + GitEngine.err(e));
            edit.setEnabled(false);
        }
    }

    @Override
    protected void onPause() {
        if (editable) doSave();
        super.onPause();
    }

    private void doSave() {
        if (!editable) return;
        String now = edit.getText().toString();
        if (now.equals(original)) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.write(file.toPath(), now.getBytes(StandardCharsets.UTF_8));
            original = now;
            Toast.makeText(this, "已保存，回到仓库页同步即可提交", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + GitEngine.err(e), Toast.LENGTH_LONG).show();
        }
    }
}
