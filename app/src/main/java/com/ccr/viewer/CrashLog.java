package com.ccr.viewer;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 崩溃/诊断日志。本设备无 logcat、pm install/run-as 均被 SELinux 拦。
 * 本 ROM 允许 app 直写公共 Download（见同目录 yeemusic-diag.log），
 * 因此把日志直写到 Termux 可读的 /sdcard/Download/ccr-crash.log（追加）。
 * 兜底：MediaStore 写入、app 专属 external/files 目录。
 */
public class CrashLog {

    private static Context appCtx;
    private static Uri sessionUri;

    public static void init(Context c) {
        appCtx = c.getApplicationContext();
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            write("UNCAUGHT", e);
            try {
                // 完整异常信息（含行号），用户可直接读给开发
                Toast.makeText(appCtx, "崩溃: " + e + "\n详见 /sdcard/Download/ccr-crash.log",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) { }
            if (prev != null) prev.uncaughtException(t, e);
            else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });
    }

    public static void log(String line) {
        appendSession(new Date() + "  " + line + "\n");
    }

    public static void write(String tag, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        appendSession(tag + " @" + new Date() + "\n" + sw + "\n=====\n");
    }

    /* ---------- 落盘（多路兜底） ---------- */

    private static synchronized void appendSession(String s) {
        if (appCtx == null) return;
        boolean wrote = false;

        // 1) 直写公共 Download（本 ROM 允许；Termux 可读）
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir.exists() || dir.mkdirs()) {
                File f = new File(dir, "ccr-crash.log");
                try (FileOutputStream out = new FileOutputStream(f, true)) {
                    out.write(s.getBytes(StandardCharsets.UTF_8));
                    wrote = true;
                }
            }
        } catch (Throwable ignore) { }

        // 2) MediaStore 兜底（API 29+）
        if (!wrote && Build.VERSION.SDK_INT >= 29) {
            try {
                ensureSessionUri();
                if (sessionUri != null) {
                    OutputStream os = appCtx.getContentResolver().openOutputStream(sessionUri, "wa");
                    if (os != null) { os.write(s.getBytes(StandardCharsets.UTF_8)); os.close(); }
                }
            } catch (Throwable ignore) { }
        }

        // 3) app 专属目录兜底
        try {
            File dir = appCtx.getExternalFilesDir(null);
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                try (FileOutputStream out = new FileOutputStream(new File(dir, "crash.log"), true)) {
                    out.write(s.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignore) { }
        try {
            File dir = appCtx.getFilesDir();
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                try (FileOutputStream out = new FileOutputStream(new File(dir, "crash.log"), true)) {
                    out.write(s.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignore) { }
    }

    private static void ensureSessionUri() throws Exception {
        if (sessionUri != null) return;
        ContentValues cv = new ContentValues();
        String name = "ccr-session-" +
                new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".log";
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = appCtx.getContentResolver()
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) return;
        ContentValues fin = new ContentValues();
        fin.put(MediaStore.MediaColumns.IS_PENDING, 0);
        appCtx.getContentResolver().update(uri, fin, null, null);
        sessionUri = uri;
    }
}
