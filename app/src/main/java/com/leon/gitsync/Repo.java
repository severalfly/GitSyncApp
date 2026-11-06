package com.leon.gitsync;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/** 一个 git 仓库的配置 */
public class Repo {
    public String id;
    public String name = "";
    public String localPath = "";
    public String remoteUrl = "";
    /** none / https / ssh */
    public String authType = "none";
    public String username = "";
    public String token = "";
    public String sshKey = "";
    public String sshPassphrase = "";
    public String branch = "";
    public String messageTemplate = "";
    public boolean autoSync = true;
    public boolean syncOnOpen = false;
    public boolean syncOnClose = false;
    public boolean sslVerify = true;
    public int cloneDepth = 0;
    public long lastSync;
    public String lastResult = "";

    public Repo() {
        this.id = UUID.randomUUID().toString();
    }

    public static Repo fromJson(JSONObject o) {
        Repo r = new Repo();
        r.id = o.optString("id", r.id);
        r.name = o.optString("name", "");
        r.localPath = o.optString("localPath", "");
        r.remoteUrl = o.optString("remoteUrl", "");
        r.authType = o.optString("authType", "none");
        r.username = o.optString("username", "");
        r.token = o.optString("token", "");
        r.sshKey = o.optString("sshKey", "");
        r.sshPassphrase = o.optString("sshPassphrase", "");
        r.branch = o.optString("branch", "");
        r.messageTemplate = o.optString("messageTemplate", "");
        r.autoSync = o.optBoolean("autoSync", true);
        r.syncOnOpen = o.optBoolean("syncOnOpen", false);
        r.syncOnClose = o.optBoolean("syncOnClose", false);
        r.sslVerify = o.optBoolean("sslVerify", true);
        r.cloneDepth = o.optInt("cloneDepth", 0);
        r.lastSync = o.optLong("lastSync", 0);
        r.lastResult = o.optString("lastResult", "");
        return r;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("localPath", localPath);
        o.put("remoteUrl", remoteUrl);
        o.put("authType", authType);
        o.put("username", username);
        o.put("token", token);
        o.put("sshKey", sshKey);
        o.put("sshPassphrase", sshPassphrase);
        o.put("branch", branch);
        o.put("messageTemplate", messageTemplate);
        o.put("autoSync", autoSync);
        o.put("syncOnOpen", syncOnOpen);
        o.put("syncOnClose", syncOnClose);
        o.put("sslVerify", sslVerify);
        o.put("cloneDepth", cloneDepth);
        o.put("lastSync", lastSync);
        o.put("lastResult", lastResult);
        return o;
    }
}
