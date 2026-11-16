package com.ccr.viewer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.os.Handler;
import android.os.Looper;

/**
 * CCR 网关的极简 HTTP 客户端。
 *
 * 真实数据接口只有两个（由页面 web-client-bridge.js 反推）：
 *   1. POST /api/ccr/auth  {token}            -> {"ok":true}，并下发 ccr_web_session Cookie
 *   2. POST /api/ccr/rpc   {method,args:[..]} -> {"ok":true,"value":<任意>}
 *      UI 里的 window.ccr.getConfig()/getUsageStats()/getRequestLogs() 等全部落到这一个端点。
 *
 * 线程模型：所有请求在内部线程池执行，回调统一回主线程。
 */
public class CcrApi {

    public interface Callback {
        /** value 为响应里的 "value" 字段（JSONObject/JSONArray/String/Number/Boolean 或 null）；失败时 error 非空 */
        void onResult(boolean ok, Object value, String error);
    }

    private static final String BASE = "http://116.205.124.216:3458";
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile String cookie = "";   // ccr_web_session=...（auth 后拿到）
    private static volatile String token = "";    // 保存的访问令牌，供 401 后重登

    public static String getToken() { return token; }
    public static void setToken(String t) { token = t == null ? "" : t; }

    /* ---------- 对外 ---------- */

    /** 登录：用访问令牌换会话 Cookie */
    public static void auth(final String tk, final Callback cb) {
        setToken(tk);
        EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("token", tk);
                JSONObject resp = rawPost("/api/ccr/auth", body);
                CrashLog.log("auth -> " + resp.optBoolean("ok") + " " + (resp.optBoolean("ok") ? "" : errMsg(resp)));
                deliver(cb, resp.optBoolean("ok"), null, resp, resp.optBoolean("ok") ? null : errMsg(resp));
            } catch (Exception e) { CrashLog.log("auth EX " + e); deliver(cb, false, null, null, msg(e)); }
        });
    }

    /** 调用 RPC 方法，回调收到 value（任意类型） */
    public static void rpc(final String method, final JSONArray args, final Callback cb) {
        EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("method", method)
                        .put("args", args == null ? new JSONArray() : args);
                JSONObject resp = rawPost("/api/ccr/rpc", body);
                Object v = resp.opt("value");
                CrashLog.log("rpc " + method + " -> " + (resp.optBoolean("ok") ? "ok value=" + typeOf(v) : "fail " + errMsg(resp)));
                deliver(cb, resp.optBoolean("ok"), v, resp,
                        resp.optBoolean("ok") ? null : errMsg(resp));
            } catch (Exception e) { CrashLog.log("rpc " + method + " EX " + e); deliver(cb, false, null, null, msg(e)); }
        });
    }

    /** 便捷：无参 RPC */
    public static void rpc(final String method, final Callback cb) {
        rpc(method, null, cb);
    }

    /* ---------- 内部 ---------- */

    private static JSONObject rawPost(String path, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(15000);
            c.setReadTimeout(45000);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (!cookie.isEmpty()) c.setRequestProperty("Cookie", cookie);
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            // 捕获会话 Cookie（auth 响应头下发）
            String sc = c.getHeaderField("Set-Cookie");
            if (sc != null) cookie = sc.split(";")[0];

            int code = c.getResponseCode();
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = is == null ? "" : readAll(is);
            if (code == 401) throw new Exception("登录已过期（HTTP 401），请重新登录");
            if (code >= 400) throw new Exception("HTTP " + code + (text.isEmpty() ? "" : "：" + text));
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String errMsg(JSONObject resp) {
        JSONObject e = resp.optJSONObject("error");
        if (e != null) {
            String m = e.optString("message", "");
            if (!m.isEmpty()) return m;
        }
        return "请求失败";
    }

    private static String msg(Exception e) {
        return e == null ? "网络错误" : (e.getMessage() == null ? e.toString() : e.getMessage());
    }

    private static String typeOf(Object v) {
        return v == null ? "null" : (v == JSONObject.NULL ? "JSONObject.NULL" : v.getClass().getSimpleName());
    }

    private static void deliver(Callback cb, boolean ok, Object value, JSONObject resp, String err) {
        MAIN.post(() -> {
            if (cb == null) return;
            try {
                cb.onResult(ok, value, err);
            } catch (Throwable t) {
                // 回调异常不再闪退，落盘记录以便诊断
                CrashLog.write("CALLBACK", t);
            }
        });
    }
}
