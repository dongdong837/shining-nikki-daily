package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressLint("SetTextI18n")
public class GuideReviewActivity extends Activity {
    private static final int PAGE_SIZE = 6;
    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2);
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final List<Bitmap> activeThumbnails = new ArrayList<>();
    private int pageIndex;
    private int renderGeneration;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        int generation = ++renderGeneration;
        List<Bitmap> oldThumbnails = new ArrayList<>(activeThumbnails);
        activeThumbnails.clear();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 44));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, "流程校准记录", 26, Ui.DARK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        CorrectionStore.importUnloggedInsertedSteps(this, EditableGuideStore.load(this));
        List<CorrectionStore.Record> records = CorrectionStore.load(this);
        int pageCount = Math.max(1, (records.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pageIndex = Math.max(0, Math.min(pageIndex, pageCount - 1));
        TextView summary = Ui.text(this, "共 " + records.size() + " 个待统一处理的校准操作"
                + (records.isEmpty() ? "" : " · 第 " + (pageIndex + 1) + "/" + pageCount + " 页"),
                15, Ui.MUTED);
        summary.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 14));
        root.addView(summary);

        root.addView(Ui.button(this, "导出 Markdown 和 JSON 汇总", Ui.PURPLE, v -> exportRecords()));

        if (records.isEmpty()) {
            TextView empty = Ui.text(this, "还没有校准记录。流程中使用“标记有误”“点选位置”或增删步骤后，记录会显示在这里。",
                    16, Ui.MUTED);
            empty.setPadding(Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 24));
            root.addView(empty);
        } else {
            root.addView(pageControls(pageCount), Ui.matchWrapBottom(this, 10));
            int start = pageIndex * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, records.size());
            for (int index = start; index < end; index++) {
                root.addView(recordCard(records.get(index), generation), Ui.matchWrapBottom(this, 12));
            }
            root.addView(pageControls(pageCount), Ui.matchWrapBottom(this, 10));
        }
        root.addView(Ui.button(this, "返回首页", Color.rgb(100, 96, 105), v -> finish()));
        setContentView(scroll);
        recycleBitmaps(oldThumbnails);
    }

    private View pageControls(int pageCount) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button previous = Ui.button(this, "上一页", Color.rgb(100, 96, 105), v -> {
            if (pageIndex > 0) {
                pageIndex--;
                render();
            }
        });
        previous.setEnabled(pageIndex > 0);
        TextView page = Ui.text(this, (pageIndex + 1) + " / " + pageCount, 15, Ui.DARK);
        page.setGravity(android.view.Gravity.CENTER);
        Button next = Ui.button(this, "下一页", Color.rgb(75, 114, 153), v -> {
            if (pageIndex + 1 < pageCount) {
                pageIndex++;
                render();
            }
        });
        next.setEnabled(pageIndex + 1 < pageCount);
        row.addView(previous, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(page, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.65f));
        row.addView(next, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View recordCard(CorrectionStore.Record record, int generation) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(Ui.roundRect(this, Color.WHITE, 16, Color.rgb(228, 222, 235)));

        TextView operation = Ui.text(this, record.actionLabel(), 14, Color.WHITE);
        operation.setGravity(android.view.Gravity.CENTER);
        operation.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        operation.setBackground(Ui.roundRect(this, Ui.PURPLE, 10, Color.TRANSPARENT));
        card.addView(operation, Ui.matchWrapBottom(this, 8));
        TextView heading = Ui.text(this, "任务 " + record.taskNumber + " · " + record.taskTitle
                + " · 步骤 " + record.stepIndex, 17, Ui.DARK);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(heading);
        TextView time = Ui.text(this, CorrectionStore.displayTime(record.createdAt), 12, Color.GRAY);
        time.setPadding(0, Ui.dp(this, 3), 0, Ui.dp(this, 8));
        card.addView(time);
        TextView step = Ui.text(this, "涉及步骤：" + record.stepText, 14, Ui.MUTED);
        step.setLineSpacing(0, 1.12f);
        card.addView(step);
        TextView note = Ui.text(this, "修改说明：" + record.note, 14, Ui.ERROR);
        note.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        card.addView(note);
        TextView clickPoint = Ui.text(this, record.clickPointText(), 13,
                record.hasClickPoint() ? Color.rgb(24, 112, 73) : Color.GRAY);
        clickPoint.setPadding(0, 0, 0, Ui.dp(this, 8));
        card.addView(clickPoint);

        if (!record.screenshotPath.isEmpty() && new File(record.screenshotPath).exists()) {
            TextView previewHint = Ui.text(this, "缩略图正在后台加载…", 12, Color.GRAY);
            previewHint.setPadding(0, 0, 0, Ui.dp(this, 5));
            card.addView(previewHint);
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(Color.rgb(236, 232, 240));
            image.setContentDescription("标记时的现场截图");
            image.setOnClickListener(v -> openScreenshot(record.screenshotPath));
            LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 180));
            previewParams.bottomMargin = Ui.dp(this, 8);
            card.addView(image, previewParams);
            loadThumbnail(record.screenshotPath, image, previewHint, generation);
            card.addView(Ui.button(this, "查看清晰原图", Color.rgb(75, 114, 153),
                    v -> openScreenshot(record.screenshotPath)));
            boolean supportsPointSelection = !CorrectionStore.ACTION_CREATE_BRANCH.equals(record.actionType)
                    && !CorrectionStore.ACTION_BRANCH2_END.equals(record.actionType)
                    && !CorrectionStore.ACTION_BRANCH1_END.equals(record.actionType);
            if (supportsPointSelection) {
                card.addView(Ui.button(this, "重新点选红圈和坐标", Color.rgb(46, 126, 145),
                        v -> openAnnotator(record)));
            }
        } else {
            card.addView(Ui.text(this, "现场截图尚未保存或已被移动", 12, Color.GRAY));
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = Ui.button(this, "编辑备注", Ui.PURPLE, v -> editNote(record));
        Button delete = Ui.button(this, "删除标记", Ui.ERROR, v -> confirmDelete(record));
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = Ui.dp(this, 6);
        buttons.addView(edit, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = Ui.dp(this, 6);
        buttons.addView(delete, right);
        card.addView(buttons);
        return card;
    }

    private void loadThumbnail(String path, ImageView image, TextView hint, int generation) {
        thumbnailExecutor.execute(() -> {
            Bitmap thumbnail = decodeThumbnail(path);
            mainHandler.post(() -> {
                if (generation != renderGeneration || isFinishing() || isDestroyed()) {
                    if (thumbnail != null && !thumbnail.isRecycled()) thumbnail.recycle();
                    return;
                }
                if (thumbnail == null) {
                    hint.setText("缩略图加载失败，可点击“查看清晰原图”重试");
                    return;
                }
                image.setImageBitmap(thumbnail);
                hint.setText("现场截图预览（点击图片查看清晰原图）");
                activeThumbnails.add(thumbnail);
            });
        });
    }

    private Bitmap decodeThumbnail(String path) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= 600
                    && bounds.outHeight / (sample * 2) >= 600) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, options);
        } catch (RuntimeException | OutOfMemoryError error) {
            return null;
        }
    }

    private void recycleBitmaps(List<Bitmap> bitmaps) {
        for (Bitmap bitmap : bitmaps) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    @Override protected void onDestroy() {
        renderGeneration++;
        thumbnailExecutor.shutdownNow();
        recycleBitmaps(new ArrayList<>(activeThumbnails));
        activeThumbnails.clear();
        super.onDestroy();
    }

    private void openScreenshot(String path) {
        Intent intent = new Intent(this, ScreenshotViewerActivity.class);
        intent.putExtra(ScreenshotViewerActivity.EXTRA_PATH, path);
        startActivity(intent);
    }

    private void openAnnotator(CorrectionStore.Record record) {
        String rawPath = !record.rawScreenshotPath.isEmpty()
                && new File(record.rawScreenshotPath).exists()
                ? record.rawScreenshotPath : record.screenshotPath;
        Intent intent = new Intent(this, ScreenshotAnnotateActivity.class);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_RAW_PATH, rawPath);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_OUTPUT_PATH, record.screenshotPath);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_RECORD_ID, record.id);
        boolean bindsToStep = CorrectionStore.ACTION_INSERT_TASK.equals(record.actionType)
                || CorrectionStore.ACTION_INSERT_STEP.equals(record.actionType)
                || CorrectionStore.ACTION_CLICK_POINT.equals(record.actionType);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_TASK_NUMBER,
                bindsToStep && record.branchId.isEmpty() ? record.taskNumber : 0);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_STEP_NUMBER,
                bindsToStep && record.branchId.isEmpty() ? record.stepIndex : 0);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_BRANCH_ID, record.branchId);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_BRANCH_STEP_ID, record.branchStepId);
        intent.putExtra(ScreenshotAnnotateActivity.EXTRA_RETURN_TO_GAME, false);
        startActivity(intent);
    }

    private void editNote(CorrectionStore.Record record) {
        EditText input = new EditText(this);
        input.setText("待补充说明".equals(record.note) ? "" : record.note);
        input.setHint("例如：这里应先点击普通模式，再点击独自登场");
        input.setMinLines(3);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int padding = Ui.dp(this, 18);
        input.setPadding(padding, 0, padding, 0);
        new AlertDialog.Builder(this)
                .setTitle("编辑修改说明")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    CorrectionStore.updateNote(this, record.id, note.isEmpty() ? "待补充说明" : note);
                    render();
                })
                .show();
    }

    private void confirmDelete(CorrectionStore.Record record) {
        new AlertDialog.Builder(this)
                .setTitle("删除这条标记？")
                .setMessage("只删除校准记录，不会修改流程文件。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    CorrectionStore.delete(this, record.id);
                    render();
                })
                .show();
    }

    private void exportRecords() {
        try {
            File report = CorrectionStore.exportMarkdown(this);
            Toast.makeText(this, "已导出：" + report.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            Toast.makeText(this, "导出失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
