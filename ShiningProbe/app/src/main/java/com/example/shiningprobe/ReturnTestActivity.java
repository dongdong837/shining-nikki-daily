package com.example.shiningprobe;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("SetTextI18n")
public class ReturnTestActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(Color.rgb(248, 247, 252));

        TextView title = new TextView(this);
        title.setText("返回动作测试页");
        title.setTextSize(25);
        title.setTextColor(Color.rgb(42, 35, 54));
        root.addView(title);

        TextView note = new TextView(this);
        note.setText("点击下方按钮后，无障碍服务只会在本测试 App 内执行一次系统返回。成功时将回到首页。");
        note.setTextSize(16);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, dp(18), 0, dp(24));
        root.addView(note);

        Button button = new Button(this);
        button.setText("由无障碍服务执行返回");
        button.setAllCaps(false);
        button.setOnClickListener(v -> {
            ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
            if (service == null) {
                Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show();
            } else {
                service.performOwnAppBackTest();
            }
        });
        root.addView(button);
        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
