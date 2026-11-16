package com.ccr.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** 最近 30 天请求量趋势，使用原生 Canvas 避免引入图表依赖。 */
public class UsageTrendView extends View {

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<JSONObject> points = new ArrayList<>();
    private String metric = "requestCount";

    public UsageTrendView(Context context, AttributeSet attrs) {
        super(context, attrs);
        bar.setColor(0xFF2DD4BF);
        axis.setColor(0xFF334155);
        axis.setStrokeWidth(dp(1));
        text.setColor(0xFF94A3B8);
        text.setTextSize(dp(10));
        setMinimumHeight((int) dp(126));
    }

    public void setMetric(String metric) {
        this.metric = metric == null ? "requestCount" : metric;
        invalidate();
    }

    public void setSeries(JSONArray series) {
        points.clear();
        Map<String, JSONObject> byDay = new HashMap<>();
        if (series != null) {
            for (int i = 0; i < series.length(); i++) {
                JSONObject point = series.optJSONObject(i);
                if (point != null) byDay.put(point.optString("bucket", ""), point);
            }
        }
        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        for (int i = 29; i >= 0; i--) {
            Calendar cursor = (Calendar) day.clone();
            cursor.add(Calendar.DAY_OF_YEAR, -i);
            String bucket = format.format(cursor.getTime());
            JSONObject point = byDay.get(bucket);
            if (point == null) point = new JSONObject();
            try {
                point.put("bucket", bucket);
                point.put("label", new SimpleDateFormat("MM/dd", Locale.US).format(cursor.getTime()));
            } catch (Exception ignore) { }
            points.add(point);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float left = dp(8), right = getWidth() - dp(8);
        float top = dp(14), bottom = getHeight() - dp(24);
        canvas.drawLine(left, bottom, right, bottom, axis);
        if (points.isEmpty()) {
            canvas.drawText("暂无日用量数据", left, (top + bottom) / 2, text);
            return;
        }
        long max = 1;
        for (JSONObject point : points) max = Math.max(max, point.optLong(metric, 0));
        float slot = (right - left) / points.size();
        float barWidth = Math.max(dp(3), slot * 0.62f);
        for (int i = 0; i < points.size(); i++) {
            JSONObject point = points.get(i);
            float value = point.optLong(metric, 0) / (float) max;
            float height = Math.max(dp(2), (bottom - top) * value);
            float x = left + i * slot + (slot - barWidth) / 2;
            canvas.drawRoundRect(new RectF(x, bottom - height, x + barWidth, bottom),
                    dp(2), dp(2), bar);
        }
        JSONObject first = points.get(0);
        JSONObject last = points.get(points.size() - 1);
        canvas.drawText(first.optString("label", first.optString("bucket", "")),
                left, getHeight() - dp(7), text);
        String end = last.optString("label", last.optString("bucket", ""));
        float endWidth = text.measureText(end);
        canvas.drawText(end, right - endWidth, getHeight() - dp(7), text);
        String maxLabel = Ui.tok(max);
        float maxWidth = text.measureText(maxLabel);
        canvas.drawText(maxLabel, right - maxWidth, top, text);
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
