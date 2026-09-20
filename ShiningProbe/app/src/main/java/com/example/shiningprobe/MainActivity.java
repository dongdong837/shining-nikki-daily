package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

@SuppressLint("SetTextI18n")
public class MainActivity extends Activity {
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private TextView serviceState;
    private TextView foregroundState;
    private TextView screenState;
    private TextView resultState;
    private TextView sessionState;
    private TextView correctionState;
    private TextView clickCounter;
    private int clickCount;

    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            refreshState();
            handler.postDelayed(this, 700);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("闪暖日活校准");
        setContentView(buildContent());
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setId(R.id.probe_scroll_view);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);

        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 48));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, "闪暖日活校准", 28, Ui.DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        GuideCatalog catalog = EditableGuideStore.load(this);
        TextView subtitle = Ui.text(this,
                "第一版为人工逐步引导，不自动点击游戏。已载入 " + catalog.tasks.size()
                        + " 个任务、" + catalog.totalStepCount() + " 个步骤。",
                15, Ui.MUTED);
        subtitle.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 18));
        root.addView(subtitle);
        if (!catalog.error.isEmpty()) {
            TextView error = Ui.text(this, catalog.error, 14, Ui.ERROR);
            root.addView(error, Ui.matchWrapBottom(this, 12));
        }

        LinearLayout stateCard = Ui.column(this);
        stateCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        stateCard.setBackground(Ui.roundRect(this, Color.WHITE, 18, Color.rgb(225, 219, 232)));
        serviceState = Ui.text(this, "服务：检查中", 16, Color.DKGRAY);
        sessionState = Ui.text(this, "引导：未开始", 14, Color.DKGRAY);
        correctionState = Ui.text(this, "校准标记：0", 14, Color.DKGRAY);
        foregroundState = Ui.text(this, "前台应用：尚未读取", 13, Color.DKGRAY);
        screenState = Ui.text(this, "屏幕：读取中", 13, Color.DKGRAY);
        resultState = Ui.text(this, "最近结果：尚未执行", 13, Color.DKGRAY);
        stateCard.addView(serviceState);
        stateCard.addView(spaced(sessionState));
        stateCard.addView(spaced(correctionState));
        stateCard.addView(spaced(foregroundState));
        stateCard.addView(spaced(screenState));
        stateCard.addView(spaced(resultState));
        root.addView(stateCard, Ui.matchWrapBottom(this, 18));

        root.addView(Ui.section(this, "① 启用并检查服务"));
        root.addView(Ui.button(this, "打开无障碍设置", Ui.PURPLE,
                v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));

        root.addView(Ui.section(this, "② 开始全流程校准"));
        TextView guideHelp = Ui.text(this,
                "选择任务后会打开游戏并显示可拖动悬浮窗。发现缺少内容时使用“步骤校准”；遇到两种页面路径时，可在当前步骤后创建分支、框选判断条件并分别标记两个分支的逻辑结束点。",
                14, Ui.MUTED);
        guideHelp.setLineSpacing(0, 1.15f);
        guideHelp.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));
        guideHelp.setBackground(Ui.roundRect(this, Color.rgb(241, 236, 247), 14, Color.TRANSPARENT));
        root.addView(guideHelp, Ui.matchWrapBottom(this, 12));
        root.addView(Ui.button(this, "选择任务并开始引导", Ui.PURPLE,
                v -> startActivity(new Intent(this, TaskSelectionActivity.class))));
        root.addView(Ui.button(this, "继续显示当前引导浮窗", Color.rgb(75, 114, 153),
                v -> withService(ProbeAccessibilityService::showGuideOverlay)));
        root.addView(Ui.button(this, "打开闪耀暖暖", Color.rgb(75, 114, 153), v -> launchGame()));
        root.addView(Ui.button(this, "结束当前引导", Ui.ERROR, v -> confirmFinishSession()));

        root.addView(Ui.section(this, "③ 统一整理错误标记"));
        root.addView(Ui.button(this, "查看、编辑和导出校准记录", Ui.PURPLE,
                v -> startActivity(new Intent(this, GuideReviewActivity.class))));
        File reviewDir = CorrectionStore.reviewDir(this);
        TextView reviewPath = Ui.text(this, "导出目录：\n" + reviewDir.getAbsolutePath(), 12, Color.GRAY);
        reviewPath.setTextIsSelectable(true);
        root.addView(reviewPath, Ui.matchWrapBottom(this, 14));

        root.addView(Ui.section(this, "④ 保留的能力测试工具"));
        root.addView(Ui.button(this, "显示原游戏测试浮窗", Color.rgb(100, 96, 105),
                v -> withService(ProbeAccessibilityService::showProbeOverlay)));
        root.addView(Ui.button(this, "读取本页控件树", Color.rgb(100, 96, 105),
                v -> withService(ProbeAccessibilityService::dumpNodeTree)));
        root.addView(Ui.button(this, "截取本页一帧", Color.rgb(100, 96, 105),
                v -> withService(ProbeAccessibilityService::captureScreenshot)));
        root.addView(Ui.button(this, "由无障碍点击下方测试按钮", Color.rgb(100, 96, 105),
                v -> withService(ProbeAccessibilityService::performOwnAppClickTest)));

        Button target = Ui.button(this, "点击测试目标", Color.rgb(100, 96, 105), v -> {
            clickCount++;
            clickCounter.setText("点击测试计数：" + clickCount + "（计数增加表示点击生效）");
            ProbeStore.setResult(this, "点击测试成功，计数=" + clickCount);
        });
        target.setId(R.id.probe_click_target);
        root.addView(target);
        clickCounter = Ui.text(this, "点击测试计数：0", 14, Color.rgb(70, 63, 80));
        clickCounter.setPadding(Ui.dp(this, 4), 0, 0, Ui.dp(this, 10));
        root.addView(clickCounter);
        root.addView(Ui.button(this, "执行安全滑动测试", Color.rgb(100, 96, 105),
                v -> withService(ProbeAccessibilityService::performOwnAppSwipeTest)));
        root.addView(Ui.button(this, "打开返回动作测试页", Color.rgb(100, 96, 105),
                v -> startActivity(new Intent(this, ReturnTestActivity.class))));

        File externalDocuments = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File probeDir = new File(externalDocuments == null ? getFilesDir() : externalDocuments, "probe");
        TextView probePath = Ui.text(this, "能力测试导出目录：\n" + probeDir.getAbsolutePath(), 12, Color.GRAY);
        probePath.setTextIsSelectable(true);
        root.addView(probePath);
        return scroll;
    }

    private void refreshState() {
        GuideCatalog catalog = EditableGuideStore.load(this);
        ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
        boolean connected = service != null;
        serviceState.setText("服务：" + (connected ? "已连接" : "未连接，请先在系统中启用"));
        serviceState.setTextColor(connected ? Color.rgb(26, 122, 65) : Ui.ERROR);
        sessionState.setText("引导：" + GuideSessionStore.statusText(this, catalog));
        correctionState.setText("校准标记：" + CorrectionStore.load(this).size());
        foregroundState.setText("前台应用：" + ProbeStore.foregroundPackage(this));
        int orientation = getResources().getConfiguration().orientation;
        String direction = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏";
        screenState.setText("屏幕：" + getResources().getDisplayMetrics().widthPixels + " × "
                + getResources().getDisplayMetrics().heightPixels + "（" + direction + "）");
        resultState.setText("最近结果：" + ProbeStore.lastResult(this));
    }

    private void launchGame() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.papegames.nn4.vivo");
        if (launch == null) {
            Toast.makeText(this, "没有找到 vivo 版闪耀暖暖启动入口", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(launch);
    }

    private void confirmFinishSession() {
        if (!GuideSessionStore.isActive(this)) {
            Toast.makeText(this, "当前没有进行中的引导", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("结束当前引导？")
                .setMessage("已保存的错误标记不会删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("结束", (dialog, which) -> {
                    GuideSessionStore.finish(this);
                    ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
                    if (service != null) service.removeGuideOverlay();
                    refreshState();
                })
                .show();
    }

    private void withService(ServiceAction action) {
        ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show();
            return;
        }
        action.run(service);
    }

    interface ServiceAction { void run(ProbeAccessibilityService service); }

    private TextView spaced(TextView view) {
        view.setPadding(0, Ui.dp(this, 7), 0, 0);
        return view;
    }
}
