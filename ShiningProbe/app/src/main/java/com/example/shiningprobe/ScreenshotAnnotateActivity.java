package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Build;
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

@SuppressLint("SetTextI18n")
public class ScreenshotAnnotateActivity extends Activity {
    static final String EXTRA_RAW_PATH = "raw_path";
    static final String EXTRA_OUTPUT_PATH = "output_path";
    static final String EXTRA_RECORD_ID = "record_id";
    static final String EXTRA_TASK_NUMBER = "task_number";
    static final String EXTRA_STEP_NUMBER = "step_number";
    static final String EXTRA_BRANCH_ID = "branch_id";
    static final String EXTRA_BRANCH_STEP_ID = "branch_step_id";
    static final String EXTRA_RETURN_TO_GAME = "return_to_game";

    private CircleAnnotationView imageView;
    private TextView coordinateText;
    private String rawPath;
    private String outputPath;
    private String recordId;
    private int taskNumber;
    private int stepNumber;
    private String branchId;
    private String branchStepId;
    private boolean returnToGame;
    private boolean completed;
    private OnBackInvokedCallback backCallback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rawPath = getIntent().getStringExtra(EXTRA_RAW_PATH);
        outputPath = getIntent().getStringExtra(EXTRA_OUTPUT_PATH);
        recordId = getIntent().getStringExtra(EXTRA_RECORD_ID);
        taskNumber = getIntent().getIntExtra(EXTRA_TASK_NUMBER, 0);
        stepNumber = getIntent().getIntExtra(EXTRA_STEP_NUMBER, 0);
        branchId = getIntent().getStringExtra(EXTRA_BRANCH_ID);
        branchStepId = getIntent().getStringExtra(EXTRA_BRANCH_STEP_ID);
        returnToGame = getIntent().getBooleanExtra(EXTRA_RETURN_TO_GAME, false);
        if (rawPath == null || outputPath == null || !new File(rawPath).exists()) {
            Toast.makeText(this, "截图文件不存在，无法标注", Toast.LENGTH_LONG).show();
            finishAndReturn();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = this::handleBackRequest;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        buildContent();
    }

    private void buildContent() {
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 18));
        root.setBackgroundColor(Color.rgb(28, 25, 32));

        TextView title = Ui.text(this, "标注实际点击位置", 23, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView help = Ui.text(this,
                "在截图上点击实际操作的位置。可反复点击调整；保存后会写入红圈、像素坐标和相对位置。",
                14, Color.rgb(225, 218, 232));
        help.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 8));
        root.addView(help);
        coordinateText = Ui.text(this, "尚未选择位置", 14, Color.rgb(255, 190, 195));
        coordinateText.setPadding(0, 0, 0, Ui.dp(this, 8));
        root.addView(coordinateText);

        imageView = new CircleAnnotationView(this, rawPath);
        imageView.setPointListener((x, y, width, height) -> coordinateText.setText(
                "坐标：(" + x + ", " + y + ") / " + width + "×" + height
                        + String.format(java.util.Locale.CHINA, "　相对：%.1f%%, %.1f%%",
                        x * 100f / width, y * 100f / height)));
        root.addView(imageView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button skip = Ui.button(this, "无需红圈", Color.rgb(95, 91, 101), v -> saveWithoutCircle());
        Button save = Ui.button(this, "保存红圈和坐标", Ui.ERROR, v -> saveAnnotation());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = Ui.dp(this, 6);
        buttons.addView(skip, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.35f);
        right.leftMargin = Ui.dp(this, 6);
        buttons.addView(save, right);
        root.addView(buttons);
        setContentView(root);
    }

    private void saveAnnotation() {
        if (imageView == null || !imageView.hasPoint()) {
            Toast.makeText(this, "请先在图片上点一下实际点击位置", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            imageView.saveAnnotated(new File(outputPath));
            CorrectionStore.updateClickPoint(this, recordId, imageView.pointX(), imageView.pointY(),
                    imageView.imageWidth(), imageView.imageHeight());
            if (taskNumber > 0 && stepNumber > 0) {
                EditableGuideStore.attachStepClickPoint(this, taskNumber, stepNumber, outputPath,
                        imageView.pointX(), imageView.pointY(),
                        imageView.imageWidth(), imageView.imageHeight());
            }
            if (branchId != null && branchStepId != null) {
                BranchStore.attachBranch2ClickPoint(this, branchId, branchStepId, outputPath,
                        imageView.pointX(), imageView.pointY(),
                        imageView.imageWidth(), imageView.imageHeight());
            }
            completed = true;
            Toast.makeText(this, "红圈和精确坐标已保存", Toast.LENGTH_SHORT).show();
            finishAndReturn();
        } catch (IOException error) {
            Toast.makeText(this, "标注保存失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveWithoutCircle() {
        try {
            copyFile(new File(rawPath), new File(outputPath));
            CorrectionStore.updateClickPoint(this, recordId, -1, -1, 0, 0);
            completed = true;
            finishAndReturn();
        } catch (IOException error) {
            Toast.makeText(this, "截图保存失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        handleBackRequest();
    }

    private void handleBackRequest() {
        if (!completed) saveWithoutCircle();
        else finish();
    }

    private void finishAndReturn() {
        if (returnToGame) {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.papegames.nn4.vivo");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(launch);
            }
            ProbeAccessibilityService service = ProbeAccessibilityService.getInstance();
            if (service != null) service.resumeGuideAfterAnnotation();
        }
        finish();
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        if (imageView != null) imageView.release();
        super.onDestroy();
    }

    private static void copyFile(File source, File target) throws IOException {
        if (source.getCanonicalPath().equals(target.getCanonicalPath())) return;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
        }
    }

    private interface PointListener {
        void onPoint(int x, int y, int width, int height);
    }

    private static final class CircleAnnotationView extends View {
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        private float drawScale = 1f;
        private float drawLeft;
        private float drawTop;
        private int pointX = -1;
        private int pointY = -1;
        private PointListener pointListener;

        CircleAnnotationView(Activity activity, String path) {
            super(activity);
            setBackgroundColor(Color.BLACK);
            bitmap = BitmapFactory.decodeFile(path);
            outerPaint.setColor(Color.WHITE);
            outerPaint.setStyle(Paint.Style.STROKE);
            circlePaint.setColor(Color.rgb(245, 28, 52));
            circlePaint.setStyle(Paint.Style.STROKE);
        }

        void setPointListener(PointListener listener) {
            pointListener = listener;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null) return;
            updateTransform();
            canvas.save();
            canvas.translate(drawLeft, drawTop);
            canvas.scale(drawScale, drawScale);
            canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
            if (hasPoint()) drawMarker(canvas, pointX, pointY, bitmap.getWidth(), bitmap.getHeight());
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null) return false;
            if (event.getActionMasked() != MotionEvent.ACTION_UP) return true;
            updateTransform();
            float imageX = (event.getX() - drawLeft) / drawScale;
            float imageY = (event.getY() - drawTop) / drawScale;
            if (imageX < 0 || imageY < 0
                    || imageX >= bitmap.getWidth() || imageY >= bitmap.getHeight()) return true;
            pointX = Math.round(imageX);
            pointY = Math.round(imageY);
            if (pointListener != null) {
                pointListener.onPoint(pointX, pointY, bitmap.getWidth(), bitmap.getHeight());
            }
            invalidate();
            performClick();
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        void saveAnnotated(File output) throws IOException {
            if (bitmap == null || !hasPoint()) throw new IOException("没有可保存的标注点");
            Bitmap annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (annotated == null) throw new IOException("无法创建标注图片");
            try {
                Canvas canvas = new Canvas(annotated);
                drawMarker(canvas, pointX, pointY, annotated.getWidth(), annotated.getHeight());
                File parent = output.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!annotated.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        throw new IOException("PNG 编码失败");
                    }
                }
            } finally {
                annotated.recycle();
            }
        }

        private void drawMarker(Canvas canvas, int x, int y, int width, int height) {
            float radius = Math.max(26f, Math.min(width, height) * 0.035f);
            float stroke = Math.max(7f, Math.min(width, height) * 0.006f);
            outerPaint.setStrokeWidth(stroke + Math.max(3f, stroke * 0.55f));
            circlePaint.setStrokeWidth(stroke);
            canvas.drawCircle(x, y, radius, outerPaint);
            canvas.drawCircle(x, y, radius, circlePaint);
            circlePaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(x, y, Math.max(5f, stroke * 0.7f), circlePaint);
            circlePaint.setStyle(Paint.Style.STROKE);
        }

        private void updateTransform() {
            if (bitmap == null || getWidth() == 0 || getHeight() == 0) return;
            drawScale = Math.min(getWidth() / (float) bitmap.getWidth(),
                    getHeight() / (float) bitmap.getHeight());
            drawLeft = (getWidth() - bitmap.getWidth() * drawScale) * 0.5f;
            drawTop = (getHeight() - bitmap.getHeight() * drawScale) * 0.5f;
        }

        boolean hasPoint() { return pointX >= 0 && pointY >= 0; }
        int pointX() { return pointX; }
        int pointY() { return pointY; }
        int imageWidth() { return bitmap == null ? 0 : bitmap.getWidth(); }
        int imageHeight() { return bitmap == null ? 0 : bitmap.getHeight(); }

        void release() {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
        }
    }
}
