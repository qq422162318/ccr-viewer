package com.ccr.viewer;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 请求日志：分页 + 按 提供商/模型 过滤 + 点击查看详情（原生控件） */
public class RequestsFragment extends Fragment {

    private ListView list;
    private Spinner providerSp, modelSp;
    private Button prevBtn, nextBtn, refresh;
    private TextView pageInfo;
    private ReqAdapter adapter;
    private final List<JSONObject> rows = new ArrayList<>();

    private int page = 1;
    private int totalPages = 1;
    private static final int PAGE_SIZE = 25;
    private boolean optionsLoaded = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_requests, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        list = v.findViewById(R.id.reqList);
        providerSp = v.findViewById(R.id.providerSpinner);
        modelSp = v.findViewById(R.id.modelSpinner);
        prevBtn = v.findViewById(R.id.prevButton);
        nextBtn = v.findViewById(R.id.nextButton);
        refresh = v.findViewById(R.id.refreshButton);
        pageInfo = v.findViewById(R.id.pageInfo);
        adapter = new ReqAdapter();
        list.setAdapter(adapter);
        providerSp.setPopupBackgroundResource(R.drawable.bg_popup);
        modelSp.setPopupBackgroundResource(R.drawable.bg_popup);

        refresh.setOnClickListener(x -> load());
        prevBtn.setOnClickListener(x -> { if (page > 1) { page--; load(); } });
        nextBtn.setOnClickListener(x -> { if (page < totalPages) { page++; load(); } });
        providerSp.setOnItemSelectedListener(new SimpleSelect(0));
        modelSp.setOnItemSelectedListener(new SimpleSelect(1));
        list.setOnItemClickListener((parent, view, pos, id) -> showDetail(rows.get(pos)));

        load();
    }

    /** Spinner 选择时触发，忽略首次初始化的自动回调 */
    private class SimpleSelect implements AdapterView.OnItemSelectedListener {
        private final int which;
        SimpleSelect(int which) { this.which = which; }
        @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
            if (optionsLoaded) { page = 1; load(); }
        }
        @Override public void onNothingSelected(AdapterView<?> p) { }
    }

    private void load() {
        refresh.setEnabled(false);
        JSONObject filter = new JSONObject();
        try {
            filter.put("page", page);
            filter.put("pageSize", PAGE_SIZE);
            String prov = selectedText(providerSp);
            String mod = selectedText(modelSp);
            if (prov != null) filter.put("provider", prov);
            if (mod != null) filter.put("model", mod);
        } catch (Exception ignore) { }

        CcrApi.rpc("getRequestLogs", new JSONArray().put(filter), (ok, value, error) -> {
            if (!isAdded()) return;
            refresh.setEnabled(true);
            if (!ok) { pageInfo.setText("加载失败：" + error); return; }
            JSONObject o = (JSONObject) value;
            totalPages = Math.max(1, o.optInt("totalPages", 1));
            page = o.optInt("page", page);
            rows.clear();
            JSONArray items = o.optJSONArray("items");
            if (items != null) for (int i = 0; i < items.length(); i++) rows.add(items.optJSONObject(i));
            adapter.notifyDataSetChanged();
            pageInfo.setText("第 " + page + " / " + totalPages + " 页（共 " + o.optInt("total", 0) + " 条）");
            prevBtn.setEnabled(page > 1);
            nextBtn.setEnabled(page < totalPages);
            if (!optionsLoaded) populateOptions(o.optJSONObject("options"));
        });
    }

    private void populateOptions(JSONObject options) {
        if (options == null) return;
        JSONArray provs = options.optJSONArray("providers");
        JSONArray mods = options.optJSONArray("models");
        List<String> p = new ArrayList<>(); p.add("全部");
        List<String> m = new ArrayList<>(); m.add("全部");
        if (provs != null) for (int i = 0; i < provs.length(); i++) p.add(provs.optString(i));
        if (mods != null) for (int i = 0; i < mods.length(); i++) m.add(mods.optString(i));
        providerSp.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.item_spinner, p));
        modelSp.setAdapter(new ArrayAdapter<>(requireContext(),
                R.layout.item_spinner, m));
        ((ArrayAdapter<?>) providerSp.getAdapter()).setDropDownViewResource(R.layout.item_spinner_dropdown);
        ((ArrayAdapter<?>) modelSp.getAdapter()).setDropDownViewResource(R.layout.item_spinner_dropdown);
        optionsLoaded = true;
    }

    private String selectedText(Spinner sp) {
        Object o = sp.getSelectedItem();
        if (o == null) return null;
        String s = o.toString();
        return "全部".equals(s) || s.isEmpty() ? null : s;
    }

    private void showDetail(JSONObject r) {
        long id = r.optLong("id", 0);
        // 网关的 getRequestLogDetail 期望 {"id": ...}（对象），传裸 id 会返回 null
        JSONObject idArg = new JSONObject();
        try { idArg.put("id", id); } catch (Exception ignore) { }
        CcrApi.rpc("getRequestLogDetail", new JSONArray().put(idArg), (ok, value, error) -> {
            if (!isAdded()) return;
            String body;
            if (ok && value instanceof JSONObject) {
                JSONObject v = (JSONObject) value;
                StringBuilder sb = new StringBuilder();
                sb.append("状态 ").append(v.optInt("statusCode", 0))
                        .append(" · ").append(v.optBoolean("ok", false) ? "成功" : "失败")
                        .append(" · ").append(v.optLong("durationMs", 0)).append("ms\n\n");
                for (String key : new String[]{"requestBody", "responseBody"}) {
                    JSONObject b = v.optJSONObject(key);
                    if (b == null) continue;
                    String text = b.optString("text", "");
                    if (text.length() > 20000) text = text.substring(0, 20000) + "…（截断）";
                    sb.append("── ").append(key).append(" ").append(b.optLong("sizeBytes", 0))
                            .append(" bytes ──\n").append(text).append("\n\n");
                }
                if (sb.length() == 0) { try { sb.append(v.toString(2)); } catch (Exception ignore) { } }
                body = sb.toString();
            } else {
                body = "加载失败：" + error;
            }
            new AlertDialog.Builder(requireContext(), R.style.Theme_CCRViewer_Dialog)
                    .setTitle("请求 #" + id)
                    .setMessage(body)
                    .setPositiveButton("关闭", null)
                    .show();
        });
    }

    private class ReqAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int i) { return rows.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            if (convert == null) {
                convert = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_request, parent, false);
            }
            JSONObject r = rows.get(i);
            boolean ok = r.optBoolean("ok", false);
            TextView badge = convert.findViewById(R.id.reqBadge);
            badge.setText(ok ? "成功" : "失败");
            badge.setTextColor(ok ? 0xFF2DD4BF : 0xFFF87171);
            ((TextView) convert.findViewById(R.id.reqTime)).setText(Ui.time(r.optString("createdAt", "")));
            ((TextView) convert.findViewById(R.id.reqDur)).setText(Ui.ms(r.optLong("durationMs", 0)));
            ((TextView) convert.findViewById(R.id.reqLine1)).setText(
                    r.optString("model", "") + "  @" + r.optString("provider", ""));
            ((TextView) convert.findViewById(R.id.reqLine2)).setText(
                    "入 " + Ui.tok(r.optLong("inputTokens", 0))
                            + " · 出 " + Ui.tok(r.optLong("outputTokens", 0))
                            + " · 缓存 " + Ui.tok(r.optLong("cacheReadTokens", 0))
                            + " · 状态 " + r.optInt("statusCode", 0)
                            + " · " + r.optString("method", "")
                            + " " + r.optString("path", ""));
            return convert;
        }
    }
}
