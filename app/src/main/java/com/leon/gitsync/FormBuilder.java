package com.leon.gitsync;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/** 简单表单构造器：避免为每个设置页写一堆 XML */
public class FormBuilder {
    private final Context ctx;
    private final LinearLayout root;
    private final Map<String, EditText> fields = new LinkedHashMap<>();
    private final Map<String, CheckBox> checks = new LinkedHashMap<>();

    public FormBuilder(Context ctx, LinearLayout root) {
        this.ctx = ctx;
        this.root = root;
    }

    public void section(String title) {
        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextSize(15);
        t.setPadding(0, 18, 0, 4);
        t.setTextColor(0xFF1A1A1A);
        root.addView(t);
    }

    public EditText text(String key, String label, String value) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(0xFF6B7280);
        root.addView(t);
        EditText e = new EditText(ctx);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(e, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        fields.put(key, e);
        return e;
    }

    public EditText password(String key, String label, String value) {
        EditText e = text(key, label, value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    public EditText number(String key, String label, int value) {
        EditText e = text(key, label, String.valueOf(value));
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    public EditText multiline(String key, String label, String value, int lines) {
        TextView t = new TextView(ctx);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(0xFF6B7280);
        root.addView(t);
        EditText e = new EditText(ctx);
        e.setText(value == null ? "" : value);
        e.setSingleLine(false);
        e.setMinLines(lines);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setTextSize(13);
        root.addView(e, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        fields.put(key, e);
        return e;
    }

    public CheckBox check(String key, String label, boolean value) {
        CheckBox c = new CheckBox(ctx);
        c.setText(label);
        c.setChecked(value);
        c.setTextSize(13);
        root.addView(c);
        checks.put(key, c);
        return c;
    }

    public void button(String label, View.OnClickListener listener) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setTextSize(13);
        b.setOnClickListener(listener);
        root.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    public String get(String key) {
        EditText e = fields.get(key);
        return e == null ? "" : e.getText().toString().trim();
    }

    public void set(String key, String value) {
        EditText e = fields.get(key);
        if (e != null) e.setText(value == null ? "" : value);
    }

    public boolean checked(String key) {
        CheckBox c = checks.get(key);
        return c != null && c.isChecked();
    }

    public int number(String key, int def) {
        try {
            return Integer.parseInt(get(key));
        } catch (Exception e) {
            return def;
        }
    }
}
