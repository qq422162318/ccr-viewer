package com.ccr.viewer;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * 主界面：登录门 + 底部标签导航。
 *
 * 原生实现（无 WebView）：登录成功后直接通过 CcrApi 的 RPC 接口拉数据、
 * 用原生控件绘制 概览/用量/请求/配置 四个页面。
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "ccr";
    private static final String KEY_TOKEN = "token";

    private SharedPreferences prefs;
    private View bottomBar;
    private TextView tabOverview, tabUsage, tabRequests, tabConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.init(this);   // 必须先于一切，捕获崩溃写入 crash.log
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bottomBar = findViewById(R.id.bottomBar);
        tabOverview = findViewById(R.id.tab_overview);
        tabUsage = findViewById(R.id.tab_usage);
        tabRequests = findViewById(R.id.tab_requests);
        tabConfig = findViewById(R.id.tab_config);

        tabOverview.setOnClickListener(v -> selectTab(0));
        tabUsage.setOnClickListener(v -> selectTab(1));
        tabRequests.setOnClickListener(v -> selectTab(2));
        tabConfig.setOnClickListener(v -> selectTab(3));

        String saved = prefs.getString(KEY_TOKEN, "");
        if (saved.isEmpty()) {
            showLogin(null);
        } else {
            CcrApi.setToken(saved);
            tryAuthAndEnter();
        }
    }

    /** 用已保存的令牌自动登录，失败则落到登录页 */
    private void tryAuthAndEnter() {
        CcrApi.auth(CcrApi.getToken(), (ok, value, error) -> {
            if (ok) showTabs();
            else showLogin(error);
        });
    }

    /** 显示登录页（隐藏底部栏） */
    public void showLogin(String error) {
        bottomBar.setVisibility(View.GONE);
        replace(LoginFragment.newInstance(error));
    }

    /** 登录成功回调（LoginFragment 调用） */
    public void onLoginSuccess(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
        CcrApi.setToken(token);
        showTabs();
    }

    /** 退出登录：清除令牌，回到登录页 */
    public void logout() {
        prefs.edit().remove(KEY_TOKEN).apply();
        CcrApi.setToken("");
        showLogin(null);
    }

    private void showTabs() {
        bottomBar.setVisibility(View.VISIBLE);
        selectTab(0);
    }

    private void selectTab(int index) {
        Fragment f;
        switch (index) {
            case 0: f = new OverviewFragment(); break;
            case 1: f = new UsageFragment(); break;
            case 2: f = new RequestsFragment(); break;
            default: f = new ConfigFragment(); break;
        }
        replace(f);
        styleTab(tabOverview, index == 0);
        styleTab(tabUsage, index == 1);
        styleTab(tabRequests, index == 2);
        styleTab(tabConfig, index == 3);
    }

    private void styleTab(TextView tv, boolean selected) {
        tv.setTextColor(selected ? 0xFF5EEAD4 : 0xFF71839A);
        tv.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tv.setSelected(selected);
    }

    private void replace(Fragment f) {
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction().replace(R.id.container, f).commit();
    }
}
