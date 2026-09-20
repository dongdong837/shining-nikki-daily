package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

@SuppressLint("SetTextI18n")
public class FlowEditorActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        GuideCatalog catalog = EditableGuideStore.load(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 40));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, "编辑流程结构", 26, Ui.DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        String source = EditableGuideStore.hasCustomFlow(this) ? "手机端编辑版" : "安装包内置版";
        TextView summary = Ui.text(this, source + " · " + catalog.tasks.size() + " 个大流程 · "
                + catalog.totalStepCount() + " 个小步骤", 14, Ui.MUTED);
        summary.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        root.addView(summary);
        if (GuideSessionStore.isActive(this)) {
            TextView active = Ui.text(this,
                    "当前引导仍在进行：插入内容后会自动调整后续编号，并尽量保持当前步骤不跳动。",
                    13, Color.rgb(142, 92, 28));
            active.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
            active.setBackground(Ui.roundRect(this, Color.rgb(255, 244, 220), 12, Color.TRANSPARENT));
            root.addView(active, Ui.matchWrapBottom(this, 10));
        }

        root.addView(Ui.button(this, "在末尾新增大流程", Ui.PURPLE,
                v -> showInsertTaskDialog(catalog.tasks.size())));
        root.addView(Ui.button(this, "导出当前流程结构", Color.rgb(75, 114, 153),
                v -> exportFlow()));
        root.addView(Ui.button(this, "恢复安装包内置流程", Ui.ERROR,
                v -> confirmReset()));

        root.addView(Ui.section(this, "大流程列表"));
        for (GuideCatalog.GuideTask task : catalog.tasks) {
            LinearLayout card = Ui.column(this);
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 10));
            card.setBackground(Ui.roundRect(this, Color.WHITE, 14, Color.rgb(226, 220, 233)));

            TextView heading = Ui.text(this, task.number + ". " + task.title
                    + "  ·  " + task.steps.size() + " 步", 17, Ui.DARK);
            heading.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(heading);

            LinearLayout taskActions = new LinearLayout(this);
            taskActions.setOrientation(LinearLayout.HORIZONTAL);
            taskActions.addView(smallAction("插入前", v -> showInsertTaskDialog(task.number - 1)));
            taskActions.addView(smallAction("改标题", v -> showEditTitleDialog(task)));
            taskActions.addView(smallAction("插入后", v -> showInsertTaskDialog(task.number)));
            card.addView(taskActions);

            card.addView(Ui.button(this, "编辑或插入小步骤", Color.rgb(126, 83, 145),
                    v -> openStepEditor(task.number)));
            root.addView(card, Ui.matchWrapBottom(this, 10));
        }

        root.addView(Ui.button(this, "返回首页", Color.rgb(100, 96, 105), v -> finish()));
        setContentView(scroll);
    }

    private Button smallAction(String label, View.OnClickListener listener) {
        Button button = Ui.button(this, label, Color.rgb(103, 80, 164), listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 5));
        button.setLayoutParams(params);
        return button;
    }

    private void showInsertTaskDialog(int insertionIndex) {
        EditText title = input("新大流程名称");
        EditText firstStep = input("第一个小步骤的操作说明");
        firstStep.setMinLines(2);
        LinearLayout form = dialogForm(title, firstStep);
        new AlertDialog.Builder(this)
                .setTitle("插入新大流程")
                .setMessage("插入后，当前位置之后的大流程会自动顺延编号。")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("插入", (dialog, which) -> {
                    String taskTitle = title.getText().toString().trim();
                    String stepText = firstStep.getText().toString().trim();
                    if (taskTitle.isEmpty() || stepText.isEmpty()) {
                        Toast.makeText(this, "大流程名称和第一个小步骤都不能为空", Toast.LENGTH_LONG).show();
                        return;
                    }
                    int newTaskNumber = EditableGuideStore.insertTask(
                            this, insertionIndex, taskTitle, stepText);
                    refreshGuideOverlay();
                    render();
                    openStepEditor(newTaskNumber);
                })
                .show();
    }

    private void showEditTitleDialog(GuideCatalog.GuideTask task) {
        EditText input = input("大流程名称");
        input.setText(task.title);
        LinearLayout form = dialogForm(input);
        new AlertDialog.Builder(this)
                .setTitle("修改第 " + task.number + " 个大流程标题")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    EditableGuideStore.editTaskTitle(this, task.number, value);
                    refreshGuideOverlay();
                    render();
                })
                .show();
    }

    private void openStepEditor(int taskNumber) {
        Intent intent = new Intent(this, TaskStepEditorActivity.class);
        intent.putExtra(TaskStepEditorActivity.EXTRA_TASK_NUMBER, taskNumber);
        startActivity(intent);
    }

    private void exportFlow() {
        try {
            File file = EditableGuideStore.export(this);
            Toast.makeText(this, "已导出：" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "导出失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("恢复内置流程？")
                .setMessage("手机端插入和文字修改会被清除。已有校准标记和截图不会删除；当前引导将结束。")
                .setNegativeButton("取消", null)
                .setPositiveButton("恢复", (dialog, which) -> {
                    GuideSessionStore.finish(this);
                    ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
                    if (service != null) service.removeGuideOverlay();
                    EditableGuideStore.resetToBuiltIn(this);
                    render();
                })
                .show();
    }

    private void refreshGuideOverlay() {
        ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
        if (service != null && GuideSessionStore.isActive(this)) service.showGuideOverlay();
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(16);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return editText;
    }

    private LinearLayout dialogForm(EditText... inputs) {
        LinearLayout form = Ui.column(this);
        int padding = Ui.dp(this, 18);
        form.setPadding(padding, 0, padding, 0);
        for (EditText input : inputs) form.addView(input, Ui.matchWrapBottom(this, 6));
        return form;
    }
}
