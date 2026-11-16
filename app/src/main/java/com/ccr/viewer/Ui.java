package com.ccr.viewer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 数值 / 时间的显示格式化（API 24 兼容，不引入 java.time 以免脱糖） */
public class Ui {

    /** 1234567 -> "1.23M" */
    public static String tok(long n) {
        if (n >= 1_000_000_000L) return fmt(n / 1e9) + "B";
        if (n >= 1_000_000L) return fmt(n / 1e6) + "M";
        if (n >= 1_000L) return fmt(n / 1e3) + "K";
        return String.valueOf(n);
    }

    public static String fmt(double d) {
        if (Math.abs(d) >= 100) return String.format(Locale.US, "%.0f", d);
        if (Math.abs(d) >= 10) return String.format(Locale.US, "%.1f", d);
        return String.format(Locale.US, "%.2f", d);
    }

    public static String money(double usd) { return "$" + String.format(Locale.US, "%.2f", usd); }

    /** 毫秒 -> "1.5s" / "320ms" */
    public static String ms(long m) {
        if (m >= 1000) return String.format(Locale.US, "%.1fs", m / 1000.0);
        return m + "ms";
    }

    /** 0.9815 -> "98.2%" */
    public static String pct(double r) { return String.format(Locale.US, "%.1f%%", r * 100); }

    /** ISO-8601 UTC -> 本地 "MM-dd HH:mm:ss" */
    public static String time(String iso) { return format(iso, "MM-dd HH:mm:ss"); }

    /** ISO-8601 UTC -> 本地 "HH:mm:ss" */
    public static String hms(String iso) { return format(iso, "HH:mm:ss"); }

    private static String format(String iso, String pattern) {
        if (iso == null || iso.isEmpty()) return "";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = in.parse(iso);
            SimpleDateFormat out = new SimpleDateFormat(pattern, Locale.US);
            return out.format(d);
        } catch (Exception e) {
            // 无毫秒版本兜底
            try {
                SimpleDateFormat in2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                in2.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = in2.parse(iso);
                SimpleDateFormat out = new SimpleDateFormat(pattern, Locale.US);
                return out.format(d);
            } catch (Exception e2) { return iso; }
        }
    }
}
