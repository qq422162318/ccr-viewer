package com.ccr.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 用量：按 模型+提供商 拆分统计的明细列表（原生 ListView 绘制） */
public class UsageFragment extends Fragment {

    private ListView list;
    private TextView summary, trendStats;
    private UsageTrendView trend;
    private Button refresh;
    private ModelAdapter adapter;
    private final List<JSONObject> rows = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_usage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        list = v.findViewById(R.id.usageList);
        summary = v.findViewById(R.id.usageSummary);
        trendStats = v.findViewById(R.id.trendStats);
        trend = v.findViewById(R.id.usageTrend);
        refresh = v.findViewById(R.id.refreshButton);
        adapter = new ModelAdapter();
        list.setAdapter(adapter);
        refresh.setOnClickListener(x -> load());
        load();
    }

    private void load() {
        refresh.setEnabled(false);
        CcrApi.rpc("getUsageStats", (ok, value, error) -> {
            if (!isAdded()) return;
            refresh.setEnabled(true);
            if (!ok) { summary.setText("加载失败：" + error); return; }
            JSONObject o = (JSONObject) value;
            JSONArray series = o.optJSONArray("series");
            JSONObject t = totalsFor30Days(series);
            summary.setText("最近 30 天  ·  请求 " + Ui.tok(t.optLong("requestCount", 0))
                    + "  ·  成本 " + Ui.money(t.optDouble("costUsd", 0))
                    + "  ·  成功率 " + Ui.pct(successRate(t)));
            trend.setSeries(series);
            trendStats.setText("请求 " + Ui.tok(t.optLong("requestCount", 0))
                    + "  ·  Token " + Ui.tok(t.optLong("totalTokens", 0))
                    + "  ·  成本 " + Ui.money(t.optDouble("costUsd", 0)));
            rows.clear();
            JSONArray arr = o.optJSONArray("models");
            if (arr != null) for (int i = 0; i < arr.length(); i++) rows.add(arr.optJSONObject(i));
            adapter.notifyDataSetChanged();
        });
    }

    private JSONObject totalsFor30Days(JSONArray series) {
        JSONObject out = new JSONObject();
        long requests = 0, input = 0, output = 0, cache = 0, errors = 0;
        double cost = 0;
        if (series != null) {
            int start = Math.max(0, series.length() - 30);
            for (int i = start; i < series.length(); i++) {
                JSONObject day = series.optJSONObject(i);
                if (day == null) continue;
                requests += day.optLong("requestCount", 0);
                input += day.optLong("inputTokens", 0);
                output += day.optLong("outputTokens", 0);
                cache += day.optLong("cacheTokens", 0);
                errors += day.optLong("errorCount", 0);
                cost += day.optDouble("costUsd", 0);
            }
        }
        try {
            out.put("requestCount", requests);
            out.put("inputTokens", input);
            out.put("outputTokens", output);
            out.put("cacheTokens", cache);
            out.put("totalTokens", input + output + cache);
            out.put("errorCount", errors);
            out.put("costUsd", cost);
        } catch (Exception ignore) { }
        return out;
    }

    private double successRate(JSONObject totals) {
        long requests = totals.optLong("requestCount", 0);
        if (requests == 0) return 0;
        return Math.max(0, (requests - totals.optLong("errorCount", 0)) / (double) requests);
    }

    private class ModelAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            if (convert == null) {
                convert = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_model, parent, false);
            }
            JSONObject r = rows.get(i);
            TextView name = convert.findViewById(R.id.modelName);
            TextView stats = convert.findViewById(R.id.modelStats);
            TextView sub = convert.findViewById(R.id.modelSub);
            name.setText(r.optString("model", "-") + "  ·  " + r.optString("provider", "-"));
            stats.setText(Ui.tok(r.optLong("requestCount", 0)) + " 次  ·  "
                    + Ui.tok(r.optLong("totalTokens", 0)) + " tok");
            sub.setText("输入 " + Ui.tok(r.optLong("inputTokens", 0))
                    + " · 输出 " + Ui.tok(r.optLong("outputTokens", 0))
                    + " · 成本 " + Ui.money(r.optDouble("costUsd", 0))
                    + " · 成功率 " + Ui.pct(r.optDouble("successRate", 0))
                    + " · 平均 " + Ui.ms(r.optLong("avgDurationMs", 0)));
            return convert;
        }
    }
}
