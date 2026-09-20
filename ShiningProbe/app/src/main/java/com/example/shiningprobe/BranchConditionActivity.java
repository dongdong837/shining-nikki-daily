package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressLint("SetTextI18n")
public class BranchConditionActivity extends Activity {
    static final String EXTRA_RAW_PATH = "raw_path";
    static final String EXTRA_OUTPUT_PATH = "output_path";
    static final String EXTRA_BRANCH_ID = "branch_id";

    private String rawPath;
    private String outputPath;
    private String branchId;
    private RegionView regionView;
    private TextView regionText;
    private OnBackInvokedCallback backCallback;
    private boolean completed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rawPath = getIntent().getStringExtra(EXTRA_RAW_PATH);
        outputPath = getIntent().getStringExtra(EXTRA_OUTPUT_PATH);
        branchId = getIntent().getStringExtra(EXTRA_BRANCH_ID);
        if (rawPath == null || outputPath == null || branchId == null
                || !new File(rawPath).exists() || BranchStore.find(this, branchId) == null) {
            Toast.makeText(this, "分支判断截图不存在", Toast.LENGTH_LONG).show();
            cancelAndReturn();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = this::cancelAndReturn;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        buildContent();
    }

    private void buildContent() {
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 18));
        root.setBackgroundColor(Color.rgb(28, 25, 32));
        TextView title = Ui.text(this, "框选分支2判断条件", 23, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView help = Ui.text(this,
                "在画面上拖动矩形，框住分支2特有且相对稳定的按钮、标题或图标。不要框动态人物和特效。",
                14, Color.rgb(225, 218, 232));
        help.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 8));
        root.addView(help);
        regionText = Ui.text(this, "尚未框选判断区域", 14, Color.rgb(255, 190, 195));
        regionText.setPadding(0, 0, 0, Ui.dp(this, 8));
        root.addView(regionText);

        regionView = new RegionView(this, rawPath);
        regionView.setListener((left, top, right, bottom, width, height) -> regionText.setText(
                "区域：( " + left + ", " + top + " ) - ( " + right + ", " + bottom
                        + " ) / " + width + "×" + height));
        root.addView(regionView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = Ui.button(this, "取消创建", Color.rgb(95, 91, 101), v -> cancelAndReturn());
        Button save = Ui.button(this, "保存判断区域并进入分支2", Ui.ERROR, v -> saveRegion());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f);
        left.rightMargin = Ui.dp(this, 6);
        buttons.addView(cancel, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f);
        right.leftMargin = Ui.dp(this, 6);
        buttons.addView(save, right);
        root.addView(buttons);
        setContentView(root);
    }

    private void saveRegion() {
        if (regionView == null || !regionView.hasRegion()) {
            Toast.makeText(this, "请先拖动框选一个判断区域", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            regionView.saveAnnotated(new File(outputPath));
            boolean saved = BranchStore.attachCondition(this, branchId, rawPath, outputPath,
                    regionView.left(), regionView.top(), regionView.right(), regionView.bottom(),
                    regionView.imageWidth(), regionView.imageHeight());
            if (!saved) throw new IOException("分支记录已经不存在");
            BranchStore.Definition definition = BranchStore.find(this, branchId);
            if (definition != null) {
                String note = "分支2判断区域：( " + regionView.left() + ", " + regionView.top()
                        + " ) - ( " + regionView.right() + ", " + regionView.bottom()
                        + " ) / " + regionView.imageWidth() + "×" + regionView.imageHeight();
                CorrectionStore.Record record = CorrectionStore.recordAction(this,
                        CorrectionStore.ACTION_CREATE_BRANCH, definition.taskNumber,
                        definition.taskTitle, definition.decisionStepIndex,
                        definition.decisionStepText, note);
                CorrectionStore.attachScreenshot(this, record.id, outputPath);
                CorrectionStore.attachRawScreenshot(this, record.id, rawPath);
            }
            completed = true;
            Toast.makeText(this, "分支2已创建，请开始记录分支2步骤", Toast.LENGTH_LONG).show();
            returnToGame();
        } catch (IOException error) {
            Toast.makeText(this, "判断区域保存失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void cancelAndReturn() {
        if (!completed && branchId != null) BranchStore.cancelDraft(this, branchId);
        returnToGame();
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        cancelAndReturn();
    }

    private void returnToGame() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.papegames.nn4.vivo");
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);
        }
        ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
        if (service != null) service.resumeGuideAfterAnnotation();
        finish();
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (regionView != null) regionView.release();
        super.onDestroy();
    }

    private interface RegionListener {
        void onRegion(int left, int top, int right, int bottom, int width, int height);
    }

    private static final class RegionView extends View {
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint regionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        private float drawScale = 1f;
        private float drawLeft;
        private float drawTop;
        private int startX = -1;
        private int startY = -1;
        private int endX = -1;
        private int endY = -1;
        private RegionListener listener;

        RegionView(Activity activity, String path) {
            super(activity);
            setBackgroundColor(Color.BLACK);
            bitmap = BitmapFactory.decodeFile(path);
            outerPaint.setColor(Color.WHITE);
            outerPaint.setStyle(Paint.Style.STROKE);
            regionPaint.setColor(Color.rgb(245, 28, 52));
            regionPaint.setStyle(Paint.Style.STROKE);
        }

        void setListener(RegionListener listener) { this.listener = listener; }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            updateTransform();
            canvas.save();
            canvas.translate(drawLeft, drawTop);
            canvas.scale(drawScale, drawScale);
            canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
            if (startX >= 0 && endX >= 0) drawRegion(canvas, left(), top(), right(), bottom());
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null) return false;
            int x = imageX(event.getX());
            int y = imageY(event.getY());
            if (x < 0 || y < 0) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startX = endX = x;
                startY = endY = y;
                invalidate();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                    || event.getActionMasked() == MotionEvent.ACTION_UP) {
                endX = x;
                endY = y;
                invalidate();
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    if (listener != null && hasRegion()) {
                        listener.onRegion(left(), top(), right(), bottom(),
                                bitmap.getWidth(), bitmap.getHeight());
                    }
                    performClick();
                }
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        void saveAnnotated(File output) throws IOException {
            if (bitmap == null || !hasRegion()) throw new IOException("没有有效的判断区域");
            Bitmap annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (annotated == null) throw new IOException("无法创建标注图片");
            try {
                drawRegion(new Canvas(annotated), left(), top(), right(), bottom());
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!annotated.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IOException("PNG 编码失败");
                    }
                }
            } finally {
                annotated.recycle();
            }
        }

        private void drawRegion(Canvas canvas, int left, int top, int right, int bottom) {
            float stroke = Math.max(7f, Math.min(bitmap.getWidth(), bitmap.getHeight()) * 0.006f);
            outerPaint.setStrokeWidth(stroke + Math.max(3f, stroke * 0.55f));
            regionPaint.setStrokeWidth(stroke);
            canvas.drawRect(left, top, right, bottom, outerPaint);
            canvas.drawRect(left, top, right, bottom, regionPaint);
        }

        private int imageX(float viewX) {
            updateTransform();
            int value = Math.round((viewX - drawLeft) / drawScale);
            return bitmap == null || value < 0 || value >= bitmap.getWidth() ? -1 : value;
        }

        private int imageY(float viewY) {
            updateTransform();
            int value = Math.round((viewY - drawTop) / drawScale);
            return bitmap == null || value < 0 || value >= bitmap.getHeight() ? -1 : value;
        }

        private void updateTransform() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
            drawScale = Math.min(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            drawLeft = (getWidth() - bitmap.getWidth() * drawScale) * 0.5f;
            drawTop = (getHeight() - bitmap.getHeight() * drawScale) * 0.5f;
        }

        boolean hasRegion() { return startX >= 0 && endX >= 0 && right() - left() >= 20
                && bottom() - top() >= 20; }
        int left() { return Math.min(startX, endX); }
        int top() { return Math.min(startY, endY); }
        int right() { return Math.max(startX, endX); }
        int bottom() { return Math.max(startY, endY); }
        int imageWidth() { return bitmap == null ? 0 : bitmap.getWidth(); }
        int imageHeight() { return bitmap == null ? 0 : bitmap.getHeight(); }

        void release() {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
        }
    }
}
