package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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

@SuppressLint("SetTextI18n")
public class TaskStepEditorActivity extends Activity {
    static final String EXTRA_TASK_NUMBER = "task_number";
    private int taskNumber;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        taskNumber = getIntent().getIntExtra(EXTRA_TASK_NUMBER, -1);
        render();
    }

    private void render() {
        GuideCatalog catalog = EditableGuideStore.load(this);
        GuideCatalog.GuideTask task = catalog.findTask(taskNumber);
        if (task == null) {
            Toast.makeText(this, "这个大流程已经不存在", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 40));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, task.number + ". " + task.title, 24, Ui.DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView summary = Ui.text(this, task.steps.size()
                + " 个小步骤；插入后编号自动顺延。现场缺步骤时，也可在游戏悬浮窗点击“插入一步”。",
                14, Ui.MUTED);
        summary.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 10));
        root.addView(summary);

        root.addView(Ui.button(this, "在第 1 步前插入", Ui.PURPLE,
                v -> showInsertStepDialog(0)));

        for (GuideCatalog.GuideStep step : task.steps) {
            LinearLayout card = Ui.column(this);
            card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 10));
            card.setBackground(Ui.roundRect(this, Color.WHITE, 14, Color.rgb(226, 220, 233)));
            TextView heading = Ui.text(this, "步骤 " + step.index, 16, Ui.DARK);
            heading.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(heading);
            TextView text = Ui.text(this, step.text, 15, Ui.MUTED);
            text.setLineSpacing(0, 1.12f);
            text.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 6));
            card.addView(text);
            String imageState;
            if (step.imageAsset.startsWith("file:")) imageState = "参考图：手机现场截图";
            else if (!step.imageAsset.isEmpty()) imageState = "参考图：录屏流程图";
            else imageState = "参考图：暂未关联";
            card.addView(Ui.text(this, imageState, 12, Color.GRAY));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(smallAction("前面插入", v -> showInsertStepDialog(step.index - 1)));
            actions.addView(smallAction("修改文字", v -> showEditStepDialog(step)));
            actions.addView(smallAction("后面插入", v -> showInsertStepDialog(step.index)));
            card.addView(actions);
            root.addView(card, Ui.matchWrapBottom(this, 9));
        }

        root.addView(Ui.button(this, "添加到末尾", Ui.PURPLE,
                v -> showInsertStepDialog(task.steps.size())));
        root.addView(Ui.button(this, "返回大流程列表", Color.rgb(100, 96, 105), v -> finish()));
        setContentView(scroll);
    }

    private Button smallAction(String label, View.OnClickListener listener) {
        Button button = Ui.button(this, label, Color.rgb(103, 80, 164), listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 4));
        button.setLayoutParams(params);
        return button;
    }

    private void showInsertStepDialog(int insertionIndex) {
        EditText input = stepInput();
        new AlertDialog.Builder(this)
                .setTitle("插入小步骤")
                .setMessage("插入位置之后的小步骤会自动顺延编号。")
                .setView(wrapInput(input))
                .setNegativeButton("取消", null)
                .setPositiveButton("插入", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "步骤说明不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    EditableGuideStore.insertStep(this, taskNumber, insertionIndex, value);
                    refreshGuideOverlay();
                    render();
                })
                .show();
    }

    private void showEditStepDialog(GuideCatalog.GuideStep step) {
        EditText input = stepInput();
        input.setText(step.text);
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("修改步骤 " + step.index)
                .setView(wrapInput(input))
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    EditableGuideStore.editStep(this, taskNumber, step.index, value);
                    refreshGuideOverlay();
                    render();
                })
                .show();
    }

    private EditText stepInput() {
        EditText input = new EditText(this);
        input.setHint("填写这个小步骤需要进行的操作");
        input.setMinLines(3);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        return input;
    }

    private LinearLayout wrapInput(EditText input) {
        LinearLayout form = Ui.column(this);
        int padding = Ui.dp(this, 18);
        form.setPadding(padding, 0, padding, 0);
        form.addView(input, Ui.matchWrapBottom(this, 4));
        return form;
    }

    private void refreshGuideOverlay() {
        ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
        if (service != null && GuideSessionStore.isActive(this)) service.showGuideOverlay();
    }
}
