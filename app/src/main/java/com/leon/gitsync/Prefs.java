package com.leon.gitsync;

import android.content.Context;
import android.content.SharedPreferences;

/** 全局设置 */
public class Prefs {
    private static final String FILE = "settings";
    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String str(String key, String def) {
        return sp.getString(key, def);
    }

    public boolean bool(String key, boolean def) {
        return sp.getBoolean(key, def);
    }

    public int integer(String key, int def) {
        return sp.getInt(key, def);
    }

    public void put(String key, String v) {
        sp.edit().putString(key, v).apply();
    }

    public void put(String key, boolean v) {
        sp.edit().putBoolean(key, v).apply();
    }

    public void put(String key, int v) {
        sp.edit().putInt(key, v).apply();
    }

    // ---- 常用项 ----
    public String authorName() { return str("authorName", "GitSync"); }
    public String authorEmail() { return str("authorEmail", "gitsync@localhost"); }
    public String messageTemplate() { return str("messageTemplate", "自动同步 %date"); }
    public int intervalMinutes() { return integer("interval", 0); }
    public boolean syncOnOpen() { return bool("syncOnOpen", false); }
    public boolean syncOnClose() { return bool("syncOnClose", false); }
    public boolean retryOnNetwork() { return bool("retryOnNetwork", true); }
    public boolean notifyResult() { return bool("notifyResult", true); }
}
