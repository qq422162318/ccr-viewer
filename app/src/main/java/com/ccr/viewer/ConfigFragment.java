package com.ccr.viewer;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置：默认提供商、内置路由规则、以及提供商的增 / 改 / 启停 / 测试 / 删除。
 *
 * 服务端没有细粒度的提供商接口，网页端同样是把整份配置改好后
 * 走 saveConfig(config, options) 回传，所以这里沿用同样的往返方式：
 * 先 getConfig 拿全量 -> 在本地改 Providers 数组 -> saveConfig 整份提交。
 */
public class ConfigFragment extends Fragment {

    /** 网页端下拉里可选的协议类型 */
    private static final String[] PROTOCOLS = {
            "openai_chat_completions", "openai_responses", "anthropic_messages",
            "gemini_generate_content", "gemini_interactions"
    };
    private static final String[] PROTOCOL_LABELS = {
            "OpenAI Chat", "OpenAI Responses", "Anthropic Messages",
            "Gemini Generate", "Gemini Interactions"
    };

    private Spinner preferredSp;
    private CheckBox claudeCb, codexCb;
    private LinearLayout providerList;
    private TextView providersEmpty, providersTitle;
    private Button refresh, save, addProvider;
    private JSONObject config;
    private boolean loading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_config, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        preferredSp = v.findViewById(R.id.preferredProvider);
        claudeCb = v.findViewById(R.id.claudeCodeCb);
        codexCb = v.findViewById(R.id.codexCb);
        providerList = v.findViewById(R.id.providerList);
        providersEmpty = v.findViewById(R.id.providersEmpty);
        providersTitle = v.findViewById(R.id.providersTitle);
        refresh = v.findViewById(R.id.refreshButton);
        save = v.findViewById(R.id.saveButton);
        addProvider = v.findViewById(R.id.addProviderButton);

        preferredSp.setPopupBackgroundResource(R.drawable.bg_popup);

        refresh.setOnClickListener(x -> load());
        save.setOnClickListener(x -> saveConfig());
        addProvider.setOnClickListener(x -> editProvider(null));
        load();
    }

    /* ---------------- 加载与渲染 ---------------- */

    private void load() {
        loading = true;
        refresh.setEnabled(false);
        CcrApi.rpc("getConfig", (ok, value, error) -> {
            if (!isAdded()) return;
            refresh.setEnabled(true);
            loading = false;
            if (!ok || !(value instanceof JSONObject)) {
                toast("加载失败：" + error);
                return;
            }
            config = (JSONObject) value;
            render();
        });
    }

    private void render() {
        JSONArray providers = providers();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.optJSONObject(i);
            if (p != null) ids.add(p.optString("id", ""));
        }

        ArrayAdapter<String> pa = new ArrayAdapter<>(requireContext(),
                R.layout.item_spinner, ids);
        pa.setDropDownViewResource(R.layout.item_spinner_dropdown);
        preferredSp.setAdapter(pa);
        String pref = config.optString("preferredProvider", "");
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(pref)) { preferredSp.setSelection(i); break; }
        }

        JSONObject rules = config.optJSONObject("Router");
        JSONObject builtIn = rules == null ? null : rules.optJSONObject("builtInRules");
        claudeCb.setChecked(builtInRulesEnabled(builtIn, "claude-code"));
        codexCb.setChecked(builtInRulesEnabled(builtIn, "codex"));

        providersTitle.setText("提供商 · " + providers.length());
        renderProviders(providers);
    }

    private void renderProviders(JSONArray providers) {
        providerList.removeAllViews();
        providersEmpty.setVisibility(providers.length() == 0 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < providers.length(); i++) {
            final JSONObject p = providers.optJSONObject(i);
            if (p == null) continue;
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_provider, providerList, false);

            boolean enabled = !p.has("enabled") || p.optBoolean("enabled", true);
            String id = p.optString("id", "-");
            String name = p.optString("name", "");
            JSONArray models = p.optJSONArray("models");

            ((TextView) row.findViewById(R.id.pvName)).setText(
                    name.isEmpty() ? id : name + "  ·  " + id);
            TextView state = row.findViewById(R.id.pvState);
            state.setText(enabled ? "已启用" : "已停用");
            state.setTextColor(enabled ? 0xFF5EEAD4 : 0xFF8A94A6);
            ((TextView) row.findViewById(R.id.pvBase)).setText(p.optString("api_base_url", "-"));

            String key = p.optString("api_key", "");
            String keyMask = key.isEmpty() ? "未设置 Key"
                    : (key.length() <= 8 ? key : key.substring(0, 4) + "…" + key.substring(key.length() - 4));
            ((TextView) row.findViewById(R.id.pvMeta)).setText(
                    p.optString("type", "-") + "  ·  "
                            + (models == null ? 0 : models.length()) + " 模型  ·  " + keyMask);

            Button toggle = row.findViewById(R.id.pvToggle);
            toggle.setText(enabled ? "停用" : "启用");
            toggle.setOnClickListener(x -> setProviderEnabled(p.optString("id", ""), !enabled));
            row.findViewById(R.id.pvEdit).setOnClickListener(x -> editProvider(p));
            row.findViewById(R.id.pvTest).setOnClickListener(x -> testProvider(p));
            row.findViewById(R.id.pvDelete).setOnClickListener(x -> confirmDelete(p));
            providerList.addView(row);
        }
    }

    /* ---------------- 提供商增改 ---------------- */

    /**
     * @param existing null 表示新增
     */
    private void editProvider(@Nullable JSONObject existing) {
        if (config == null) return;
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_provider, null, false);
        EditText nameEt = form.findViewById(R.id.dlgName);
        EditText idEt = form.findViewById(R.id.dlgId);
        EditText baseEt = form.findViewById(R.id.dlgBaseUrl);
        EditText keyEt = form.findViewById(R.id.dlgApiKey);
        EditText modelsEt = form.findViewById(R.id.dlgModels);
        Spinner protoSp = form.findViewById(R.id.dlgProtocol);
        CheckBox enabledCb = form.findViewById(R.id.dlgEnabled);

        ArrayAdapter<String> protoAdapter = new ArrayAdapter<>(requireContext(),
                R.layout.item_spinner, PROTOCOL_LABELS);
        protoAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        protoSp.setAdapter(protoAdapter);
        protoSp.setPopupBackgroundResource(R.drawable.bg_popup);

        boolean isNew = existing == null;
        if (!isNew) {
            nameEt.setText(existing.optString("name", ""));
            idEt.setText(existing.optString("id", ""));
            idEt.setEnabled(false);
            baseEt.setText(existing.optString("api_base_url", ""));
            keyEt.setText(existing.optString("api_key", ""));
            enabledCb.setChecked(!existing.has("enabled") || existing.optBoolean("enabled", true));
            String type = existing.optString("type", PROTOCOLS[0]);
            for (int i = 0; i < PROTOCOLS.length; i++) {
                if (PROTOCOLS[i].equals(type)) { protoSp.setSelection(i); break; }
            }
            JSONArray models = existing.optJSONArray("models");
            if (models != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < models.length(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(models.optString(i));
                }
                modelsEt.setText(sb.toString());
            }
        } else {
            idEt.setText(nextProviderId(""));
        }
        final String autoId = idEt.getText().toString();

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.Theme_CCRViewer_Dialog)
                .setTitle(isNew ? "添加提供商" : "编辑提供商")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFF5EEAD4);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFF94A3B8);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
                String base = baseEt.getText().toString().trim();
                if (base.isEmpty()) { toast("请填写 Base URL"); return; }
                String proto = PROTOCOLS[Math.max(0, protoSp.getSelectedItemPosition())];
                String name = nameEt.getText().toString().trim();
                String id = idEt.getText().toString().trim();
                if (id.isEmpty()) id = nextProviderId(name);
                // 用户没动过自动生成的 id，就按名称重算一个更可读的
                else if (isNew && id.equals(autoId) && !name.isEmpty()) id = nextProviderId(name);

                try {
                    JSONObject p = isNew ? new JSONObject() : new JSONObject(existing.toString());
                    p.put("id", id);
                    p.put("name", name.isEmpty() ? id : name);
                    p.put("type", proto);
                    p.put("api_base_url", base);
                    p.put("api_key", keyEt.getText().toString().trim());
                    p.put("models", parseModels(modelsEt.getText().toString()));
                    if (enabledCb.isChecked()) p.remove("enabled");
                    else p.put("enabled", false);
                    if (isNew) {
                        // 与网页端一致：首次添加时按 Base URL + 协议生成 capabilities
                        p.put("capabilities", defaultCapabilities(base, proto));
                    }
                    upsertProvider(p, isNew);
                } catch (Exception e) {
                    toast("保存失败：" + e.getMessage());
                    return;
                }
                dialog.dismiss();
                submit("提供商" + (isNew ? "已添加" : "已更新"));
            });
        });
        dialog.show();
    }

    private JSONArray defaultCapabilities(String baseUrl, String proto) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String endpoint;
        switch (proto) {
            case "openai_responses": endpoint = base + "/responses"; break;
            case "anthropic_messages": endpoint = base + "/messages"; break;
            case "gemini_generate_content":
            case "gemini_interactions": endpoint = base + "/models"; break;
            default: endpoint = base + "/chat/completions";
        }
        JSONArray arr = new JSONArray();
        try {
            arr.put(new JSONObject()
                    .put("baseUrl", base)
                    .put("endpoint", endpoint)
                    .put("source", "manual")
                    .put("type", proto));
        } catch (Exception ignore) { }
        return arr;
    }

    private JSONArray parseModels(String text) {
        JSONArray arr = new JSONArray();
        for (String line : text.split("[\\n,，]")) {
            String m = line.trim();
            if (!m.isEmpty()) arr.put(m);
        }
        return arr;
    }

    /** 生成不与现有提供商冲突的 id */
    private String nextProviderId(String preferredName) {
        JSONArray providers = providers();
        String base = (preferredName == null || preferredName.trim().isEmpty())
                ? "provider" : preferredName.trim();
        String id = base;
        int n = 2;
        while (idTaken(providers, id)) {
            id = base + "-" + n;
            n++;
        }
        return id;
    }

    private boolean idTaken(JSONArray providers, String id) {
        for (int i = 0; i < providers.length(); i++) {
            JSONObject p = providers.optJSONObject(i);
            if (p != null && id.equals(p.optString("id", ""))) return true;
        }
        return false;
    }

    private void upsertProvider(JSONObject provider, boolean isNew) throws Exception {
        JSONArray old = providers();
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject p = old.optJSONObject(i);
            if (p != null && provider.optString("id").equals(p.optString("id", ""))) {
                next.put(provider);
                replaced = true;
            } else if (p != null) {
                next.put(p);
            }
        }
        if (!replaced && isNew) next.put(provider);
        config.put("Providers", next);
    }

    private void setProviderEnabled(String id, boolean enabled) {
        if (config == null) return;
        try {
            JSONArray arr = providers();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null && id.equals(p.optString("id", ""))) {
                    if (enabled) p.remove("enabled");
                    else p.put("enabled", false);
                }
            }
            config.put("Providers", arr);
        } catch (Exception e) {
            toast("修改失败：" + e.getMessage());
            return;
        }
        submit(enabled ? "已启用 " + id : "已停用 " + id);
    }

    private void confirmDelete(JSONObject p) {
        String id = p.optString("id", "");
        new AlertDialog.Builder(requireContext(), R.style.Theme_CCRViewer_Dialog)
                .setTitle("删除提供商")
                .setMessage("确定删除 “" + p.optString("name", id) + "”（" + id + "）？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    try {
                        JSONArray old = providers();
                        JSONArray next = new JSONArray();
                        for (int i = 0; i < old.length(); i++) {
                            JSONObject o = old.optJSONObject(i);
                            if (o != null && !id.equals(o.optString("id", ""))) next.put(o);
                        }
                        config.put("Providers", next);
                        // 若删的是默认提供商，顺带回退到第一个
                        if (id.equals(config.optString("preferredProvider", ""))) {
                            config.put("preferredProvider",
                                    next.length() > 0 ? next.optJSONObject(0).optString("id", "") : "");
                        }
                    } catch (Exception e) {
                        toast("删除失败：" + e.getMessage());
                        return;
                    }
                    submit("已删除 " + id);
                })
                .show();
    }

    /** 测试连通性：probeProvider 会用给定 Key 拉一次模型/协议探测 */
    private void testProvider(JSONObject p) {
        String base = p.optString("api_base_url", "");
        String key = p.optString("api_key", "");
        if (base.isEmpty()) { toast("缺少 Base URL"); return; }
        toast("正在测试 " + (p.optString("name", p.optString("id"))) + " …");
        JSONObject arg = new JSONObject();
        try {
            if (!key.isEmpty()) arg.put("apiKey", key);
            arg.put("baseUrl", base);
            arg.put("mode", key.isEmpty() ? "protocols" : "models");
            arg.put("models", new JSONArray());
            JSONArray protos = new JSONArray();
            String type = p.optString("type", "");
            if (!key.isEmpty()) {
                protos.put(type.isEmpty() ? PROTOCOLS[0] : type);
            } else {
                for (String s : PROTOCOLS) protos.put(s);
            }
            arg.put("protocols", protos);
        } catch (Exception ignore) { }

        CcrApi.rpc("probeProvider", new JSONArray().put(arg), (ok, value, error) -> {
            if (!isAdded()) return;
            if (!ok || !(value instanceof JSONObject)) {
                alert("测试失败", "无法连接：\n" + error);
                return;
            }
            JSONObject o = (JSONObject) value;
            JSONArray models = o.optJSONArray("models");
            JSONArray caps = o.optJSONArray("capabilities");
            StringBuilder sb = new StringBuilder();
            sb.append("Base URL：").append(o.optString("normalizedBaseUrl", base)).append('\n');
            sb.append("协议：").append(o.optString("detectedProtocol", "-")).append('\n');
            sb.append("可用能力：").append(caps == null ? 0 : caps.length()).append(" 项\n");
            sb.append("返回模型：").append(models == null ? 0 : models.length()).append(" 个\n\n");
            if (models != null) {
                int n = Math.min(models.length(), 12);
                for (int i = 0; i < n; i++) sb.append("· ").append(models.optString(i)).append('\n');
                if (models.length() > n) sb.append("… 其余 ").append(models.length() - n).append(" 个");
            }
            alert("测试成功", sb.toString());
        });
    }

    private void alert(String title, String body) {
        new AlertDialog.Builder(requireContext(), R.style.Theme_CCRViewer_Dialog)
                .setTitle(title)
                .setMessage(body)
                .setPositiveButton("关闭", null)
                .show();
    }

    /* ---------------- 保存 ---------------- */

    private JSONArray providers() {
        JSONArray arr = config == null ? null : config.optJSONArray("Providers");
        return arr == null ? new JSONArray() : arr;
    }

    private boolean builtInRulesEnabled(JSONObject builtIn, String key) {
        JSONObject o = builtIn == null ? null : builtIn.optJSONObject(key);
        return o != null && o.optBoolean("enabled", false);
    }

    /** 顶部“保存”按钮：只提交默认提供商与规则开关 */
    private void saveConfig() {
        if (config == null || loading) return;
        try {
            Object sel = preferredSp.getSelectedItem();
            if (sel != null && !sel.toString().isEmpty()) config.put("preferredProvider", sel.toString());
            JSONObject rules = config.optJSONObject("Router");
            JSONObject builtIn = rules == null ? null : rules.optJSONObject("builtInRules");
            if (builtIn != null) {
                JSONObject cc = builtIn.optJSONObject("claude-code");
                if (cc != null) cc.put("enabled", claudeCb.isChecked());
                JSONObject cx = builtIn.optJSONObject("codex");
                if (cx != null) cx.put("enabled", codexCb.isChecked());
            }
        } catch (Exception e) {
            toast("保存失败：" + e.getMessage());
            return;
        }
        submit("已保存配置");
    }

    /** 把当前 config 整份回传，成功后重新拉取以刷新服务端归一化后的结果 */
    private void submit(String successMsg) {
        if (config == null) return;
        save.setEnabled(false);
        CcrApi.rpc("saveConfig", new JSONArray().put(config), (ok, value, error) -> {
            if (!isAdded()) return;
            save.setEnabled(true);
            if (!ok) {
                String e = error == null ? "" : error;
                if (e.contains("Configuration changed")) {
                    // 别处（网页/另一台设备）改过配置：重新拉取，让用户在新配置上重做
                    toast("配置已被他人修改，已重新载入，请重新提交");
                    load();
                } else {
                    toast("保存失败：" + e);
                }
                return;
            }
            toast(successMsg);
            if (value instanceof JSONObject) {
                config = (JSONObject) value;
                render();
            } else {
                load();
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(requireContext(), s, Toast.LENGTH_SHORT).show();
    }
}
