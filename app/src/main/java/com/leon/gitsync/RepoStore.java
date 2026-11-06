package com.leon.gitsync;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** 多仓库配置持久化（SharedPreferences + JSON） */
public class RepoStore {
    private static final String FILE = "repos";
    private static final String KEY = "list";

    private final SharedPreferences sp;
    private List<Repo> cache;

    public RepoStore(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    /** 每次都重新读，保证多个页面之间的配置即时同步 */
    public List<Repo> list() {
        load();
        return cache;
    }

    public Repo find(String id) {
        load();
        for (Repo r : cache) {
            if (r.id.equals(id)) return r;
        }
        return null;
    }

    public void save(Repo r) {
        load();
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id.equals(r.id)) {
                cache.set(i, r);
                flush();
                return;
            }
        }
        cache.add(r);
        flush();
    }

    public void remove(String id) {
        load();
        for (int i = cache.size() - 1; i >= 0; i--) {
            if (cache.get(i).id.equals(id)) cache.remove(i);
        }
        flush();
    }

    private void load() {
        cache = new ArrayList<>();
        String s = sp.getString(KEY, "[]");
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                cache.add(Repo.fromJson(a.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
        }
    }

    private void flush() {
        JSONArray a = new JSONArray();
        try {
            for (Repo r : cache) a.put(r.toJson());
            sp.edit().putString(KEY, a.toString()).apply();
        } catch (JSONException ignored) {
        }
    }
}
