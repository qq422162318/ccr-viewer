package com.ccr.viewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** 令牌登录页：输入 ccr_web_token，换取会话 Cookie 后进入主界面 */
public class LoginFragment extends Fragment {

    private static final String ARG_ERR = "err";

    public static LoginFragment newInstance(String error) {
        LoginFragment f = new LoginFragment();
        Bundle b = new Bundle();
        if (error != null) b.putString(ARG_ERR, error);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        EditText input = v.findViewById(R.id.tokenInput);
        Button go = v.findViewById(R.id.loginButton);
        TextView err = v.findViewById(R.id.loginError);

        if (getArguments() != null) {
            String e = getArguments().getString(ARG_ERR);
            if (e != null && !e.isEmpty()) {
                err.setText("登录失败：" + e);
                err.setVisibility(View.VISIBLE);
            }
        }

        View.OnClickListener submit = view -> {
            String token = input.getText().toString().trim();
            if (token.isEmpty()) return;
            err.setVisibility(View.GONE);
            go.setEnabled(false);
            go.setText("登录中…");
            CcrApi.auth(token, (ok, value, error) -> {
                go.setEnabled(true);
                go.setText("登录");
                if (ok && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).onLoginSuccess(token);
                } else {
                    err.setText("登录失败：" + (error == null ? "未知错误" : error));
                    err.setVisibility(View.VISIBLE);
                }
            });
        };
        go.setOnClickListener(submit);
        input.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { submit.onClick(tv); return true; }
            return false;
        });
    }
}
