package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

@SuppressLint("SetTextI18n")
final class GuideOverlayController {
    interface Callback {
        void markCurrentStep();
        void insertTaskWithScreenshot();
        void insertStepWithScreenshot();
        void deleteCurrentStep();
        void captureCurrentStepClickPoint();
        void handleBranchAction();
    }

    private final ProbeAccessibilityService service;
    private final WindowManager windowManager;
    private final Callback callback;
    private View root;
    private WindowManager.LayoutParams params;
    private TextView progress;
    private TextView instruction;
    private ImageView referenceImage;
    private Button imageButton;
    private Button pauseButton;
    private Button finishButton;
    private LinearLayout expandedContent;
    private LinearLayout flowEditControls;
    private Button deleteStepButton;
    private Button branchButton;
    private Bitmap referenceBitmap;
    private boolean imageVisible;
    private boolean collapsed;
    private long finishArmedUntil;
    private long deleteArmedUntil;

    GuideOverlayController(ProbeAccessibilityService service, WindowManager windowManager, Callback callback) {
        this.service = service;
        this.windowManager = windowManager;
        this.callback = callback;
    }

    boolean isShowing() {
        return root != null;
    }

    void show() {
        if (!GuideSessionStore.isActive(service)) {
            Toast.makeText(service, "请先在 App 中选择任务并开始引导", Toast.LENGTH_LONG).show();
            return;
        }
        if (root != null) {
            root.setVisibility(View.VISIBLE);
            refresh();
            return;
        }
        root = buildView();
        android.util.DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        int width = Math.min(dp(310), metrics.widthPixels - dp(20));
        params = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(10);
        params.y = dp(68);
        try {
            windowManager.addView(root, params);
            refresh();
        } catch (RuntimeException error) {
            root = null;
            Toast.makeText(service, "引导浮窗显示失败", Toast.LENGTH_LONG).show();
        }
    }

    void remove() {
        if (root != null) {
            try {
                windowManager.removeView(root);
            } catch (RuntimeException ignored) {
            }
        }
        root = null;
        recycleReference();
    }

    void hideTemporarily() {
        if (root != null) root.setVisibility(View.INVISIBLE);
    }

    void restoreAfterCapture() {
        if (root != null) root.setVisibility(View.VISIBLE);
    }

    void onConfigurationChanged() {
        if (!isShowing()) return;
        remove();
        new android.os.Handler(service.getMainLooper()).postDelayed(this::show, 350);
    }

    private View buildView() {
        LinearLayout card = new LinearLayout(service);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(242, 37, 31, 47));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.argb(220, 211, 193, 235));
        card.setBackground(background);

        LinearLayout header = new LinearLayout(service);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        progress = label("准备引导", 13, Color.WHITE);
        progress.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(progress, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button collapse = smallButton("收起", v -> toggleCollapsed());
        header.addView(collapse);
        card.addView(header);
        installDrag(header);

        expandedContent = new LinearLayout(service);
        expandedContent.setOrientation(LinearLayout.VERTICAL);
        instruction = label("", 14, Color.rgb(247, 243, 250));
        instruction.setLineSpacing(0, 1.15f);
        instruction.setPadding(0, dp(6), 0, dp(6));
        expandedContent.addView(instruction);

        referenceImage = new ImageView(service);
        referenceImage.setAdjustViewBounds(true);
        referenceImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        referenceImage.setMaxHeight(dp(190));
        referenceImage.setVisibility(View.GONE);
        expandedContent.addView(referenceImage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = row();
        actions.addView(weightedButton("上一步", v -> previous()));
        imageButton = weightedButton("参考图", v -> toggleImage());
        actions.addView(imageButton);
        actions.addView(weightedButton("标记有误", v -> callback.markCurrentStep(), Color.rgb(190, 67, 83)));
        actions.addView(weightedButton("下一步", v -> next()));
        expandedContent.addView(actions);

        LinearLayout controls = row();
        controls.addView(weightedButton("步骤校准", v -> toggleFlowEditControls(),
                Color.rgb(126, 83, 145)));
        pauseButton = weightedButton("暂停", v -> togglePause(), Color.rgb(113, 102, 70));
        controls.addView(pauseButton);
        controls.addView(weightedButton("隐藏", v -> remove(), Color.rgb(78, 74, 82)));
        finishButton = weightedButton("结束", v -> finishSession(), Color.rgb(145, 56, 67));
        controls.addView(finishButton);
        expandedContent.addView(controls);

        flowEditControls = row();
        flowEditControls.setVisibility(View.GONE);
        flowEditControls.addView(weightedButton("点选位置",
                v -> callback.captureCurrentStepClickPoint(), Color.rgb(46, 126, 145)));
        flowEditControls.addView(weightedButton("插入大流程",
                v -> callback.insertTaskWithScreenshot(), Color.rgb(89, 110, 168)));
        flowEditControls.addView(weightedButton("插入小步骤",
                v -> callback.insertStepWithScreenshot(), Color.rgb(126, 83, 145)));
        deleteStepButton = weightedButton("删除当前步骤",
                v -> confirmDeleteCurrentStep(), Color.rgb(190, 67, 83));
        flowEditControls.addView(deleteStepButton);
        expandedContent.addView(flowEditControls);
        LinearLayout branchControls = row();
        branchButton = weightedButton("在当前步骤后创建分支",
                v -> callback.handleBranchAction(), Color.rgb(63, 119, 103));
        branchControls.addView(branchButton);
        expandedContent.addView(branchControls);
        card.addView(expandedContent);
        return card;
    }

    private void toggleFlowEditControls() {
        if (flowEditControls == null) return;
        flowEditControls.setVisibility(flowEditControls.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE);
    }

    private void confirmDeleteCurrentStep() {
        long now = System.currentTimeMillis();
        if (now > deleteArmedUntil) {
            deleteArmedUntil = now + 3500;
            deleteStepButton.setText("再点确认删除");
            Toast.makeText(service, "再次点击才会删除当前步骤", Toast.LENGTH_SHORT).show();
            new android.os.Handler(service.getMainLooper()).postDelayed(() -> {
                if (deleteStepButton != null) deleteStepButton.setText("删除当前步骤");
            }, 3600);
            return;
        }
        deleteArmedUntil = 0;
        deleteStepButton.setText("删除当前步骤");
        callback.deleteCurrentStep();
    }

    void refresh() {
        BranchStore.BranchStepPosition branchPosition = BranchStore.currentBranch2(service);
        BranchStore.Definition activeBranch = BranchStore.active(service);
        if (branchPosition != null) {
            progress.setText("任务 " + branchPosition.definition.taskNumber + " · 分支2 · "
                    + (branchPosition.position + 1) + "/" + branchPosition.count);
            String clickStatus = branchPosition.step.hasClickPoint()
                    ? "\n已记录点击位置：" + branchPosition.step.clickX + ", "
                    + branchPosition.step.clickY : "";
            instruction.setText(branchPosition.step.text + clickStatus);
            pauseButton.setText(GuideSessionStore.isPaused(service) ? "继续" : "暂停");
            imageButton.setEnabled(!branchPosition.step.imageAsset.isEmpty());
            branchButton.setText("分支2结束");
            branchButton.setEnabled(true);
            if (imageVisible) loadReference(branchPosition.step.imageAsset);
            return;
        }
        GuideCatalog catalog = EditableGuideStore.load(service);
        GuideSessionStore.Position position = GuideSessionStore.current(service, catalog);
        if (position == null) {
            remove();
            return;
        }
        progress.setText("任务 " + (position.selectedTaskPosition + 1) + "/"
                + position.selectedTaskCount + " · " + position.task.title + " · "
                + position.step.index + "/" + position.task.steps.size());
        String clickStatus = position.step.hasClickPoint()
                ? "\n已记录点击位置：" + position.step.clickX + ", " + position.step.clickY : "";
        instruction.setText(position.step.text + clickStatus);
        pauseButton.setText(GuideSessionStore.isPaused(service) ? "继续" : "暂停");
        imageButton.setEnabled(!position.step.imageAsset.isEmpty());
        if (activeBranch != null && BranchStore.PHASE_BRANCH1.equals(activeBranch.phase)) {
            branchButton.setText("分支1结束并汇合");
            branchButton.setEnabled(true);
        } else if (activeBranch != null && BranchStore.PHASE_CONDITION.equals(activeBranch.phase)) {
            branchButton.setText("取消未完成分支");
            branchButton.setEnabled(true);
        } else {
            branchButton.setText("在当前步骤后创建分支");
            branchButton.setEnabled(true);
        }
        if (imageVisible) loadReference(position.step.imageAsset);
    }

    private void previous() {
        if (GuideSessionStore.isPaused(service)) {
            Toast.makeText(service, "引导已暂停", Toast.LENGTH_SHORT).show();
            return;
        }
        if (BranchStore.currentBranch2(service) != null) {
            if (!BranchStore.previousBranch2Step(service)) {
                Toast.makeText(service, "已经是分支2的第一步", Toast.LENGTH_SHORT).show();
            }
        } else {
            GuideSessionStore.previous(service, EditableGuideStore.load(service));
        }
        imageVisible = false;
        referenceImage.setVisibility(View.GONE);
        refresh();
    }

    private void next() {
        if (GuideSessionStore.isPaused(service)) {
            Toast.makeText(service, "引导已暂停", Toast.LENGTH_SHORT).show();
            return;
        }
        if (BranchStore.currentBranch2(service) != null) {
            if (!BranchStore.nextBranch2Step(service)) {
                Toast.makeText(service,
                        "已到分支2最后一步；可插入下一步或点击“分支2结束”",
                        Toast.LENGTH_LONG).show();
            }
            imageVisible = false;
            referenceImage.setVisibility(View.GONE);
            refresh();
            return;
        }
        boolean completed = GuideSessionStore.next(service, EditableGuideStore.load(service));
        if (completed) {
            remove();
            Toast.makeText(service, "全部所选任务已走完，请回到 App 整理校准记录", Toast.LENGTH_LONG).show();
            return;
        }
        imageVisible = false;
        referenceImage.setVisibility(View.GONE);
        refresh();
    }

    private void togglePause() {
        boolean paused = !GuideSessionStore.isPaused(service);
        GuideSessionStore.setPaused(service, paused);
        refresh();
        Toast.makeText(service, paused ? "引导已暂停" : "引导已继续", Toast.LENGTH_SHORT).show();
    }

    private void finishSession() {
        long now = System.currentTimeMillis();
        if (now > finishArmedUntil) {
            finishArmedUntil = now + 3500;
            finishButton.setText("再点结束");
            Toast.makeText(service, "再次点击才会结束本次引导", Toast.LENGTH_SHORT).show();
            new android.os.Handler(service.getMainLooper()).postDelayed(() -> {
                if (finishButton != null) finishButton.setText("结束");
            }, 3600);
            return;
        }
        GuideSessionStore.finish(service);
        remove();
        Toast.makeText(service, "本次引导已结束，标记记录已保留", Toast.LENGTH_LONG).show();
    }

    private void toggleImage() {
        BranchStore.BranchStepPosition branchPosition = BranchStore.currentBranch2(service);
        if (branchPosition != null) {
            if (branchPosition.step.imageAsset.isEmpty()) return;
            imageVisible = !imageVisible;
            if (imageVisible) {
                loadReference(branchPosition.step.imageAsset);
                referenceImage.setVisibility(View.VISIBLE);
                imageButton.setText("隐藏图");
            } else {
                referenceImage.setVisibility(View.GONE);
                imageButton.setText("参考图");
            }
            return;
        }
        GuideSessionStore.Position position = GuideSessionStore.current(service, EditableGuideStore.load(service));
        if (position == null || position.step.imageAsset.isEmpty()) return;
        imageVisible = !imageVisible;
        if (imageVisible) {
            loadReference(position.step.imageAsset);
            referenceImage.setVisibility(View.VISIBLE);
            imageButton.setText("隐藏图");
        } else {
            referenceImage.setVisibility(View.GONE);
            imageButton.setText("参考图");
        }
    }

    private void loadReference(String assetPath) {
        recycleReference();
        if (assetPath == null || assetPath.isEmpty()) {
            referenceImage.setVisibility(View.GONE);
            return;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        try {
            if (assetPath.startsWith("file:")) {
                referenceBitmap = BitmapFactory.decodeFile(
                        assetPath.substring("file:".length()), options);
            } else {
                try (InputStream stream = service.getAssets().open(assetPath)) {
                    referenceBitmap = BitmapFactory.decodeStream(stream, null, options);
                }
            }
            referenceImage.setImageBitmap(referenceBitmap);
            referenceImage.setVisibility(referenceBitmap == null ? View.GONE : View.VISIBLE);
        } catch (IOException | RuntimeException error) {
            referenceImage.setVisibility(View.GONE);
            Toast.makeText(service, "参考图读取失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void recycleReference() {
        referenceImageSafeClear();
        if (referenceBitmap != null && !referenceBitmap.isRecycled()) referenceBitmap.recycle();
        referenceBitmap = null;
    }

    private void referenceImageSafeClear() {
        if (referenceImage != null) referenceImage.setImageDrawable(null);
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        expandedContent.setVisibility(collapsed ? View.GONE : View.VISIBLE);
    }

    private void installDrag(View handle) {
        final float[] startTouch = new float[2];
        final int[] startPosition = new int[2];
        handle.setOnTouchListener((view, event) -> {
            if (params == null) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                startTouch[0] = event.getRawX();
                startTouch[1] = event.getRawY();
                startPosition[0] = params.x;
                startPosition[1] = params.y;
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                params.x = startPosition[0] + Math.round(event.getRawX() - startTouch[0]);
                params.y = startPosition[1] + Math.round(event.getRawY() - startTouch[1]);
                try {
                    windowManager.updateViewLayout(root, params);
                } catch (RuntimeException ignored) {
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                view.performClick();
                return true;
            }
            return false;
        });
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(service);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView label(String value, float size, int color) {
        TextView text = new TextView(service);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private Button smallButton(String value, View.OnClickListener listener) {
        Button button = new Button(service);
        button.setText(value);
        button.setTextSize(11);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(3), dp(8), dp(3));
        button.setOnClickListener(listener);
        return button;
    }

    private Button weightedButton(String value, View.OnClickListener listener) {
        return weightedButton(value, listener, Color.rgb(103, 80, 164));
    }

    private Button weightedButton(String value, View.OnClickListener listener, int color) {
        Button button = smallButton(value, listener);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(8));
        button.setBackground(background);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        layout.setMargins(dp(2), dp(3), dp(2), dp(3));
        button.setLayoutParams(layout);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }
}
