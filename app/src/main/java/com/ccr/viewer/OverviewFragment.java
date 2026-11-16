package com.ccr.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

/** 概览：网关/代理状态 + 用量合计 + 最近请求（全部原生控件绘制） */
public class OverviewFragment extends Fragment {

    private TextView statusText, proxyText, totalsText, recentText, tokenTrendStats;
    private UsageTrendView tokenTrend;
    private Button refresh, logout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        statusText = v.findViewById(R.id.statusText);
        proxyText = v.findViewById(R.id.proxyText);
        totalsText = v.findViewById(R.id.totalsText);
        recentText = v.findViewById(R.id.recentText);
        tokenTrendStats = v.findViewById(R.id.tokenTrendStats);
        tokenTrend = v.findViewById(R.id.tokenTrend);
        tokenTrend.setMetric("totalTokens");
        refresh = v.findViewById(R.id.refreshButton);
        logout = v.findViewById(R.id.logoutButton);
        refresh.setOnClickListener(x -> load());
        logout.setOnClickListener(x -> {
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).logout();
        });
        load();
    }

    private void load() {
        refresh.setEnabled(false);

        CcrApi.rpc("getGatewayStatus", (ok, value, error) -> {
            if (!isAdded()) return;
            if (ok) statusText.setText(renderStatus((JSONObject) value));
            else statusText.setText("网关状态加载失败：" + error);
        });
        CcrApi.rpc("getProxyStatus", (ok, value, error) -> {
            if (!isAdded()) return;
            if (ok) proxyText.setText(renderProxy((JSONObject) value));
            else proxyText.setText("代理状态加载失败：" + error);
        });
        CcrApi.rpc("getUsageStats", (ok, value, error) -> {
            if (!isAdded()) return;
            refresh.setEnabled(true);
            if (!ok) { totalsText.setText("用量加载失败：" + error); return; }
            JSONObject o = (JSONObject) value;
            JSONArray series = o.optJSONArray("series");
            tokenTrend.setSeries(series);
            JSONObject trendTotals = totalsFor30Days(series);
            tokenTrendStats.setText("总 Token " + Ui.tok(trendTotals.optLong("totalTokens", 0))
                    + "  ·  输入 " + Ui.tok(trendTotals.optLong("inputTokens", 0))
                    + "  ·  输出 " + Ui.tok(trendTotals.optLong("outputTokens", 0)));
            totalsText.setText(renderTotals(o));
            recentText.setText(renderRecent(o));
        });
    }

    private JSONObject totalsFor30Days(JSONArray series) {
        JSONObject out = new JSONObject();
        long input = 0, output = 0, cache = 0;
        if (series != null) {
            int start = Math.max(0, series.length() - 30);
            for (int i = start; i < series.length(); i++) {
                JSONObject day = series.optJSONObject(i);
                if (day == null) continue;
                input += day.optLong("inputTokens", 0);
                output += day.optLong("outputTokens", 0);
                cache += day.optLong("cacheTokens", 0);
            }
        }
        try {
            out.put("inputTokens", input);
            out.put("outputTokens", output);
            out.put("cacheTokens", cache);
            out.put("totalTokens", input + output + cache);
        } catch (Exception ignore) { }
        return out;
    }

    private String renderStatus(JSONObject g) {
        StringBuilder s = new StringBuilder();
        s.append("状态   ").append(g.optString("state", "-")).append('\n');
        s.append("端点   ").append(g.optString("endpoint", "-")).append('\n');
        s.append("核心   ").append(g.optString("coreEndpoint", "-")).append('\n');
        s.append("PID    ").append(g.optLong("pid", 0)).append('\n');
        s.append("启动   ").append(Ui.time(g.optString("lastStartedAt", ""))).append('\n');
        JSONArray nets = g.optJSONArray("networkEndpoints");
        if (nets != null && nets.length() > 0) {
            s.append("外网   ");
            for (int i = 0; i < nets.length(); i++) {
                JSONObject n = nets.optJSONObject(i);
                if (n == null) continue;
                if (i > 0) s.append(", ");
                s.append(n.optString("endpoint", "")).append(" (").append(n.optString("interfaceName", "")).append(')');
            }
            s.append('\n');
        }
        return s.toString();
    }

    private String renderProxy(JSONObject p) {
        StringBuilder s = new StringBuilder();
        s.append("状态   ").append(p.optString("state", "-")).append('\n');
        s.append("模式   ").append(p.optString("mode", "-")).append('\n');
        s.append("端口   ").append(p.optLong("port", 0)).append('\n');
        s.append("端点   ").append(p.optString("endpoint", "-")).append('\n');
        JSONArray hosts = p.optJSONArray("targetHosts");
        s.append("目标   ").append(hosts == null ? "-" : hosts.length() + " 个主机").append('\n');
        JSONObject sys = p.optJSONObject("systemProxy");
        if (sys != null) s.append("系统代理 ").append(sys.optString("state", "-")).append('\n');
        return s.toString();
    }

    private String renderTotals(JSONObject o) {
        JSONObject t = o.optJSONObject("totals");
        if (t == null) return "（无数据）";
        StringBuilder s = new StringBuilder();
        s.append("统计范围  ").append(o.optString("range", "-")).append('\n');
        s.append("请求数    ").append(Ui.tok(t.optLong("requestCount", 0))).append('\n');
        s.append("成功率    ").append(Ui.pct(t.optDouble("successRate", 0))).append('\n');
        s.append("错误      ").append(t.optLong("errorCount", 0)).append('\n');
        s.append("输入      ").append(Ui.tok(t.optLong("inputTokens", 0))).append(" tok").append('\n');
        s.append("输出      ").append(Ui.tok(t.optLong("outputTokens", 0))).append(" tok").append('\n');
        s.append("缓存      ").append(Ui.tok(t.optLong("cacheTokens", 0))).append(" tok").append('\n');
        s.append("成本      ").append(Ui.money(t.optDouble("costUsd", 0))).append('\n');
        s.append("平均耗时  ").append(Ui.ms(t.optLong("avgDurationMs", 0)));
        return s.toString();
    }

    private String renderRecent(JSONObject o) {
        JSONArray arr = o.optJSONArray("recentRequests");
        if (arr == null || arr.length() == 0) return "（暂无请求）";
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject r = arr.optJSONObject(i);
            if (r == null) continue;
            boolean ok = r.optBoolean("ok", false);
            s.append(ok ? "✓" : "✗").append(' ')
             .append(Ui.hms(r.optString("createdAt", ""))).append(' ')
             .append(r.optString("model", "")).append(" @")
             .append(r.optString("provider", "")).append("  ")
             .append(Ui.tok(r.optLong("totalTokens", 0))).append("tok ")
             .append(Ui.ms(r.optLong("durationMs", 0))).append('\n');
        }
        return s.toString();
    }
}
