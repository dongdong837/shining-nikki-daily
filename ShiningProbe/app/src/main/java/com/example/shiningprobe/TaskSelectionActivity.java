package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressLint("SetTextI18n")
public class TaskSelectionActivity extends Activity {
    private final List<TaskCheck> taskChecks = new ArrayList<>();
    private GuideCatalog catalog;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        catalog = EditableGuideStore.load(this);
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 44));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, "选择本次校准任务", 26, Ui.DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = Ui.text(this,
                "默认选择全部任务。开始后由你手动操作游戏，悬浮引导只负责显示步骤、切换进度和记录错误。",
                15, Ui.MUTED);
        subtitle.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        root.addView(subtitle);

        if (!catalog.error.isEmpty()) {
            TextView error = Ui.text(this, catalog.error, 14, Ui.ERROR);
            root.addView(error, Ui.matchWrapBottom(this, 12));
        }

        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        Button all = Ui.button(this, "全选", Ui.PURPLE, v -> setAll(true));
        Button none = Ui.button(this, "清空", Color.rgb(100, 96, 105), v -> setAll(false));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = Ui.dp(this, 6);
        quick.addView(all, half);
        LinearLayout.LayoutParams halfRight = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        halfRight.leftMargin = Ui.dp(this, 6);
        quick.addView(none, halfRight);
        root.addView(quick, Ui.matchWrapBottom(this, 12));

        Set<Integer> previouslySelected = new HashSet<>(GuideSessionStore.selectedTaskNumbers(this));
        boolean usePrevious = GuideSessionStore.isActive(this) && !previouslySelected.isEmpty();
        for (GuideCatalog.GuideTask task : catalog.tasks) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(task.number + ". " + task.title + "  ·  " + task.steps.size() + " 步");
            checkBox.setTextSize(16);
            checkBox.setTextColor(Ui.DARK);
            checkBox.setChecked(!usePrevious || previouslySelected.contains(task.number));
            checkBox.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
            checkBox.setBackground(Ui.roundRect(this, Color.WHITE, 12, Color.rgb(228, 222, 235)));
            root.addView(checkBox, Ui.matchWrapBottom(this, 8));
            taskChecks.add(new TaskCheck(task.number, checkBox));
        }

        root.addView(Ui.button(this, "开始引导并打开闪耀暖暖", Ui.PURPLE, v -> startGuide()),
                Ui.matchWrapBottom(this, 8));
        root.addView(Ui.button(this, "取消", Color.rgb(100, 96, 105), v -> finish()));
        return scroll;
    }

    private void setAll(boolean checked) {
        for (TaskCheck item : taskChecks) item.checkBox.setChecked(checked);
    }

    private void startGuide() {
        List<Integer> selected = new ArrayList<>();
        for (TaskCheck item : taskChecks) if (item.checkBox.isChecked()) selected.add(item.taskNumber);
        if (selected.isEmpty()) {
            Toast.makeText(this, "请至少选择一个任务", Toast.LENGTH_SHORT).show();
            return;
        }
        ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "请先返回首页并启用无障碍服务", Toast.LENGTH_LONG).show();
            return;
        }
        GuideSessionStore.start(this, selected);
        service.showGuideOverlay();
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.papegames.nn4.vivo");
        if (launch == null) {
            Toast.makeText(this, "引导已开始，但没有找到 vivo 版闪耀暖暖启动入口", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(launch);
    }

    private static final class TaskCheck {
        final int taskNumber;
        final CheckBox checkBox;

        TaskCheck(int taskNumber, CheckBox checkBox) {
            this.taskNumber = taskNumber;
            this.checkBox = checkBox;
        }
    }
}
