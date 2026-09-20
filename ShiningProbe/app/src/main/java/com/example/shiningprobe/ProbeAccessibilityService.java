package com.example.shiningprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.AccessibilityService.ScreenshotResult;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

public class ProbeAccessibilityService extends AccessibilityService {
    private static volatile WeakReference<ProbeAccessibilityService> instanceRef = new WeakReference<>(null);
    private final Executor mainExecutor = command -> new android.os.Handler(getMainLooper()).post(command);
    private WindowManager windowManager;
    private View overlay;
    private GuideOverlayController guideOverlay;

    static ProbeAccessibilityService getInstance() {
        return instanceRef.get();
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instanceRef = new WeakReference<>(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        guideOverlay = createGuideOverlayController();
        ProbeStore.setResult(this, "无障碍服务已连接");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            ProbeStore.setForeground(this, event.getPackageName(), event.getClassName());
        }
    }

    @Override public void onInterrupt() {
        ProbeStore.setResult(this, "无障碍服务被系统中断");
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        removeOverlay();
        removeGuideOverlay();
        instanceRef.clear();
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        removeOverlay();
        removeGuideOverlay();
        instanceRef.clear();
        super.onDestroy();
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (guideOverlay != null) guideOverlay.onConfigurationChanged();
    }

    void showProbeOverlay() {
        if (overlay != null) {
            Toast.makeText(this, "测试浮窗已经显示", Toast.LENGTH_SHORT).show();
            return;
        }
        removeGuideOverlay();
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(5), dp(6), dp(5));
        bar.setBackgroundColor(Color.argb(230, 35, 31, 42));
        bar.addView(overlayButton("状态", v -> collectStatus()));
        bar.addView(overlayButton("节点", v -> dumpNodeTree()));
        bar.addView(overlayButton("截屏", v -> captureScreenshot()));
        bar.addView(overlayButton("关闭", v -> removeOverlay()));

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(48);
        try {
            windowManager.addView(bar, params);
            overlay = bar;
            ProbeStore.setResult(this, "游戏测试浮窗已显示");
        } catch (RuntimeException error) {
            ProbeStore.setResult(this, "浮窗显示失败：" + error.getClass().getSimpleName());
            Toast.makeText(this, "浮窗显示失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Button overlayButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        button.setOnClickListener(listener);
        return button;
    }

    private void removeOverlay() {
        if (overlay == null || windowManager == null) return;
        try {
            windowManager.removeView(overlay);
        } catch (RuntimeException ignored) {
        }
        overlay = null;
        ProbeStore.setResult(this, "游戏测试浮窗已关闭");
    }

    void showGuideOverlay() {
        removeOverlay();
        if (guideOverlay == null) {
            guideOverlay = createGuideOverlayController();
        }
        guideOverlay.show();
        ProbeStore.setResult(this, "逐步引导浮窗已显示");
    }

    void removeGuideOverlay() {
        if (guideOverlay != null) guideOverlay.remove();
    }

    void collectStatus() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root == null || root.getPackageName() == null
                ? ProbeStore.foregroundPackage(this)
                : root.getPackageName().toString();
        int orientation = getResources().getConfiguration().orientation;
        String direction = orientation == Configuration.ORIENTATION_LANDSCAPE ? "横屏" : "竖屏";
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int windows = getWindows() == null ? 0 : getWindows().size();
        String result = "前台=" + pkg + "，窗口=" + windows + "，"
                + direction + "，" + metrics.widthPixels + "×" + metrics.heightPixels;
        ProbeStore.setResult(this, result);
        Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        if (root != null) root.recycle();
    }

    void dumpNodeTree() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            fail("控件树读取失败：当前窗口没有可读取的根节点");
            return;
        }
        int count = 0;
        StringBuilder output = new StringBuilder(8192);
        output.append("time=").append(timestamp()).append('\n');
        output.append("foreground=").append(ProbeStore.foregroundPackage(this)).append('\n');

        Deque<NodeDepth> queue = new ArrayDeque<>();
        queue.add(new NodeDepth(root, 0));
        while (!queue.isEmpty() && count < 500) {
            NodeDepth current = queue.removeFirst();
            AccessibilityNodeInfo node = current.node;
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);
            indent(output, current.depth)
                    .append(node.getClassName()).append(" text=").append(quoted(node.getText()))
                    .append(" desc=").append(quoted(node.getContentDescription()))
                    .append(" id=").append(quoted(node.getViewIdResourceName()))
                    .append(" clickable=").append(node.isClickable())
                    .append(" scrollable=").append(node.isScrollable())
                    .append(" bounds=").append(bounds)
                    .append('\n');
            count++;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.addLast(new NodeDepth(child, current.depth + 1));
            }
            node.recycle();
        }

        File file = new File(outputDir(), "nodes-" + fileStamp() + ".txt");
        try {
            writeText(file, output.toString());
            String result = "控件树成功：" + count + " 个节点，已保存 " + file.getName();
            ProbeStore.setResult(this, result);
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
        } catch (IOException error) {
            fail("控件树保存失败：" + error.getMessage());
        }
    }

    void captureScreenshot() {
        requestScreenshot(null, 0, 0, false);
    }

    private GuideOverlayController createGuideOverlayController() {
        return new GuideOverlayController(this, windowManager, new GuideOverlayController.Callback() {
            @Override public void markCurrentStep() {
                markCurrentGuideStep();
            }

            @Override public void insertStepWithScreenshot() {
                insertCurrentGuideStepWithScreenshot();
            }

            @Override public void insertTaskWithScreenshot() {
                insertCurrentGuideTaskWithScreenshot();
            }

            @Override public void deleteCurrentStep() {
                deleteCurrentGuideStep();
            }

            @Override public void captureCurrentStepClickPoint() {
                captureGuideStepClickPoint();
            }

            @Override public void handleBranchAction() {
                handleGuideBranchAction();
            }
        });
    }

    private void markCurrentGuideStep() {
        BranchStore.BranchStepPosition branchPosition = BranchStore.currentBranch2(this);
        if (branchPosition != null) {
            CorrectionStore.Record record = CorrectionStore.recordAction(this,
                    CorrectionStore.ACTION_ERROR, branchPosition.definition.taskNumber,
                    branchPosition.definition.taskTitle + " / 分支2",
                    branchPosition.position + 1, branchPosition.step.text, "待补充说明");
            Toast.makeText(this, "已标记分支2当前步骤，正在保存现场截图", Toast.LENGTH_SHORT).show();
            requestScreenshot(record.id, 0, 0, false);
            return;
        }
        GuideSessionStore.Position position = GuideSessionStore.current(this, EditableGuideStore.load(this));
        if (position == null) {
            fail("标记失败：当前没有进行中的引导步骤");
            return;
        }
        CorrectionStore.Record record = CorrectionStore.mark(this, position);
        ProbeStore.setResult(this, "已标记任务 " + position.task.number + " 的步骤 " + position.step.index);
        Toast.makeText(this, "已标记有误，正在保存现场截图", Toast.LENGTH_SHORT).show();
        requestScreenshot(record.id, 0, 0, false);
    }

    private void insertCurrentGuideTaskWithScreenshot() {
        if (BranchStore.currentBranch2(this) != null) {
            fail("分支2校准期间不能插入新的大流程");
            return;
        }
        GuideSessionStore.Position position = GuideSessionStore.current(
                this, EditableGuideStore.load(this));
        if (position == null) {
            fail("插入失败：当前没有进行中的引导步骤");
            return;
        }
        int previousTaskNumber = position.task.number;
        int insertedTask = EditableGuideStore.insertTask(this, previousTaskNumber,
                "新插入大流程（待统一补充名称）",
                "新插入步骤：请在校准记录中补充正式操作说明。");
        GuideCatalog.GuideTask newTask = EditableGuideStore.load(this).findTask(insertedTask);
        if (newTask == null || newTask.steps.isEmpty()) {
            fail("插入大流程失败");
            return;
        }
        CorrectionStore.Record record = CorrectionStore.recordAction(this,
                CorrectionStore.ACTION_INSERT_TASK, insertedTask, newTask.title, 1,
                newTask.steps.get(0).text,
                "插入位置：原任务 " + previousTaskNumber + " 之后；请统一补充大流程名称和步骤说明。");
        if (guideOverlay != null) guideOverlay.refresh();
        Toast.makeText(this, "已插入大流程，正在截屏并点选位置", Toast.LENGTH_SHORT).show();
        requestScreenshot(record.id, insertedTask, 1, true);
    }

    private void insertCurrentGuideStepWithScreenshot() {
        BranchStore.BranchStepPosition branchPosition = BranchStore.currentBranch2(this);
        if (branchPosition != null) {
            int insertedStep = BranchStore.insertBranch2Step(this,
                    "分支2新步骤：请在校准记录中补充正式操作说明。");
            BranchStore.BranchStepPosition inserted = BranchStore.currentBranch2(this);
            if (insertedStep < 1 || inserted == null) {
                fail("插入分支2步骤失败");
                return;
            }
            CorrectionStore.Record record = CorrectionStore.recordAction(this,
                    CorrectionStore.ACTION_INSERT_STEP, inserted.definition.taskNumber,
                    inserted.definition.taskTitle + " / 分支2", insertedStep,
                    inserted.step.text, "在分支2中插入第 " + insertedStep + " 个步骤。");
            CorrectionStore.attachBranchLink(this, record.id,
                    inserted.definition.id, inserted.step.id);
            if (guideOverlay != null) guideOverlay.refresh();
            Toast.makeText(this, "已插入分支2步骤，正在截屏并点选位置", Toast.LENGTH_SHORT).show();
            requestScreenshot(record.id, 0, 0, true,
                    inserted.definition.id, inserted.step.id);
            return;
        }
        GuideCatalog catalog = EditableGuideStore.load(this);
        GuideSessionStore.Position position = GuideSessionStore.current(this, catalog);
        if (position == null) {
            fail("插入失败：当前没有进行中的引导步骤");
            return;
        }
        int taskNumber = position.task.number;
        int insertedStep = EditableGuideStore.insertStep(this, taskNumber, position.step.index,
                "新插入步骤：请在校准记录中补充正式操作说明。");
        if (insertedStep < 1) {
            fail("插入步骤失败");
            return;
        }
        GuideSessionStore.next(this, EditableGuideStore.load(this));
        GuideCatalog.GuideTask task = EditableGuideStore.load(this).findTask(taskNumber);
        GuideCatalog.GuideStep step = task == null || insertedStep > task.steps.size()
                ? null : task.steps.get(insertedStep - 1);
        CorrectionStore.Record record = CorrectionStore.recordAction(this,
                CorrectionStore.ACTION_INSERT_STEP, taskNumber,
                task == null ? position.task.title : task.title, insertedStep,
                step == null ? "新插入步骤" : step.text,
                "插入位置：原步骤 " + position.step.index + " 之后；请统一补充正式步骤说明。");
        if (guideOverlay != null) guideOverlay.refresh();
        ProbeStore.setResult(this, "已插入任务 " + taskNumber + " 的步骤 " + insertedStep + "，正在保存参考图");
        Toast.makeText(this, "已插入小步骤，正在截屏并点选位置", Toast.LENGTH_SHORT).show();
        requestScreenshot(record.id, taskNumber, insertedStep, true);
    }

    private void captureGuideStepClickPoint() {
        BranchStore.BranchStepPosition branchPosition = BranchStore.currentBranch2(this);
        if (branchPosition != null) {
            CorrectionStore.Record record = CorrectionStore.recordAction(this,
                    CorrectionStore.ACTION_CLICK_POINT, branchPosition.definition.taskNumber,
                    branchPosition.definition.taskTitle + " / 分支2",
                    branchPosition.position + 1, branchPosition.step.text,
                    "为分支2当前步骤补充点击坐标。");
            CorrectionStore.attachBranchLink(this, record.id,
                    branchPosition.definition.id, branchPosition.step.id);
            Toast.makeText(this, "正在截取分支2当前画面", Toast.LENGTH_SHORT).show();
            requestScreenshot(record.id, 0, 0, true,
                    branchPosition.definition.id, branchPosition.step.id);
            return;
        }
        GuideSessionStore.Position position = GuideSessionStore.current(
                this, EditableGuideStore.load(this));
        if (position == null) {
            fail("点选失败：当前没有进行中的引导步骤");
            return;
        }
        CorrectionStore.Record record = CorrectionStore.recordAction(this,
                CorrectionStore.ACTION_CLICK_POINT, position.task.number, position.task.title,
                position.step.index, position.step.text,
                "为现有步骤补充可用于后续自动操作的点击坐标。");
        Toast.makeText(this, "正在截取当前画面，随后请点选目标位置", Toast.LENGTH_SHORT).show();
        requestScreenshot(record.id, position.task.number, position.step.index, true);
    }

    private void deleteCurrentGuideStep() {
        BranchStore.BranchStepPosition branchPosition = BranchStore.currentBranch2(this);
        if (branchPosition != null) {
            CorrectionStore.Record record = CorrectionStore.recordAction(this,
                    CorrectionStore.ACTION_DELETE_STEP, branchPosition.definition.taskNumber,
                    branchPosition.definition.taskTitle + " / 分支2",
                    branchPosition.position + 1, branchPosition.step.text,
                    "删除分支2当前步骤。");
            if (!BranchStore.deleteCurrentBranch2Step(this)) {
                CorrectionStore.updateNote(this, record.id, "删除失败：分支2至少保留一个步骤。");
                fail("分支2至少需要保留一个步骤");
                return;
            }
            if (guideOverlay != null) guideOverlay.refresh();
            Toast.makeText(this, "分支2当前步骤已删除", Toast.LENGTH_SHORT).show();
            requestScreenshot(record.id, 0, 0, false);
            return;
        }
        GuideSessionStore.Position position = GuideSessionStore.current(
                this, EditableGuideStore.load(this));
        if (position == null) {
            fail("删除失败：当前没有进行中的引导步骤");
            return;
        }
        CorrectionStore.Record record = CorrectionStore.recordAction(this,
                CorrectionStore.ACTION_DELETE_STEP, position.task.number, position.task.title,
                position.step.index, position.step.text,
                "删除原任务 " + position.task.number + " 的步骤 " + position.step.index
                        + "；若它是该大流程唯一的步骤，则整个空大流程同步删除。");
        boolean deleted = EditableGuideStore.deleteStep(
                this, position.task.number, position.step.index);
        if (!deleted) {
            CorrectionStore.updateNote(this, record.id, "删除失败：流程至少需要保留一个大流程。");
            fail("删除失败：流程至少需要保留一个大流程");
            return;
        }
        if (guideOverlay != null) guideOverlay.refresh();
        Toast.makeText(this, "当前步骤已删除，正在保存现场依据", Toast.LENGTH_SHORT).show();
        requestScreenshot(record.id, 0, 0, true);
    }

    private void handleGuideBranchAction() {
        BranchStore.Definition active = BranchStore.active(this);
        if (active == null) {
            EditableGuideStore.ensureStableIds(this);
            GuideSessionStore.Position position = GuideSessionStore.current(
                    this, EditableGuideStore.load(this));
            BranchStore.Definition draft = BranchStore.createDraft(this, position);
            if (draft == null) {
                fail("创建分支失败：请在当前大流程仍有后续步骤的位置创建，且一次只校准一个分支");
                return;
            }
            Toast.makeText(this, "正在截屏，随后请框选分支2的判断条件", Toast.LENGTH_SHORT).show();
            requestBranchConditionScreenshot(draft);
            return;
        }
        if (BranchStore.PHASE_BRANCH2.equals(active.phase)) {
            BranchStore.BranchStepPosition position = BranchStore.currentBranch2(this);
            if (position == null) {
                fail("分支2没有可结束的步骤");
                return;
            }
            requestBranchEndScreenshot(true, position.position + 1, position.step.text);
            return;
        }
        if (BranchStore.PHASE_BRANCH1.equals(active.phase)) {
            GuideSessionStore.Position position = GuideSessionStore.current(
                    this, EditableGuideStore.load(this));
            if (position == null || position.task.number != active.taskNumber) {
                fail("请先回到分支1所属的大流程，再设置分支1结束");
                return;
            }
            requestBranchEndScreenshot(false, position.step.index, position.step.text);
            return;
        }
        if (BranchStore.PHASE_CONDITION.equals(active.phase)) {
            BranchStore.cancelDraft(this, active.id);
            if (guideOverlay != null) guideOverlay.refresh();
            Toast.makeText(this, "未完成的分支已取消", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private void requestBranchConditionScreenshot(BranchStore.Definition draft) {
        boolean restoreProbe = overlay != null && overlay.getVisibility() == View.VISIBLE;
        boolean restoreGuide = guideOverlay != null && guideOverlay.isShowing();
        if (restoreProbe) overlay.setVisibility(View.INVISIBLE);
        if (restoreGuide) guideOverlay.hideTemporarily();
        File rawFile = BranchStore.screenshotFile(this, draft.id, "condition-raw");
        File outputFile = BranchStore.screenshotFile(this, draft.id, "condition");
        new android.os.Handler(getMainLooper()).postDelayed(() -> takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY, mainExecutor, new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        try {
                            writeScreenshotResult(screenshot, rawFile, outputFile);
                            Intent intent = new Intent(ProbeAccessibilityService.this,
                                    BranchConditionActivity.class);
                            intent.putExtra(BranchConditionActivity.EXTRA_RAW_PATH,
                                    rawFile.getAbsolutePath());
                            intent.putExtra(BranchConditionActivity.EXTRA_OUTPUT_PATH,
                                    outputFile.getAbsolutePath());
                            intent.putExtra(BranchConditionActivity.EXTRA_BRANCH_ID, draft.id);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                        } catch (IOException | RuntimeException error) {
                            BranchStore.cancelDraft(ProbeAccessibilityService.this, draft.id);
                            restoreOverlays(restoreProbe, restoreGuide);
                            fail("分支条件截图失败：" + error.getMessage());
                        }
                    }

                    @Override public void onFailure(int errorCode) {
                        BranchStore.cancelDraft(ProbeAccessibilityService.this, draft.id);
                        restoreOverlays(restoreProbe, restoreGuide);
                        fail("分支条件截图失败，错误码=" + errorCode);
                    }
                }), 220);
    }

    private void requestBranchEndScreenshot(boolean branch2, int stepIndex, String stepText) {
        BranchStore.Definition active = BranchStore.active(this);
        if (active == null) return;
        boolean restoreProbe = overlay != null && overlay.getVisibility() == View.VISIBLE;
        boolean restoreGuide = guideOverlay != null && guideOverlay.isShowing();
        if (restoreProbe) overlay.setVisibility(View.INVISIBLE);
        if (restoreGuide) guideOverlay.hideTemporarily();
        File file = BranchStore.screenshotFile(this, active.id,
                branch2 ? "branch2-end" : "branch1-end");
        new android.os.Handler(getMainLooper()).postDelayed(() -> takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY, mainExecutor, new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        try {
                            writeScreenshotResult(screenshot, file);
                            if (branch2) {
                                BranchStore.Definition updated = BranchStore.finishBranch2(
                                        ProbeAccessibilityService.this, file.getAbsolutePath());
                                if (updated == null) throw new IOException("分支2状态已经变化");
                                CorrectionStore.Record record = CorrectionStore.recordAction(
                                        ProbeAccessibilityService.this,
                                        CorrectionStore.ACTION_BRANCH2_END, updated.taskNumber,
                                        updated.taskTitle + " / 分支2", stepIndex, stepText,
                                        "分支2在此逻辑状态结束；完整画面允许包含动态差异。");
                                CorrectionStore.attachScreenshot(ProbeAccessibilityService.this,
                                        record.id, file.getAbsolutePath());
                                GuideSessionStore.moveToStep(ProbeAccessibilityService.this,
                                        EditableGuideStore.load(ProbeAccessibilityService.this),
                                        updated.taskNumber, updated.branch1StartStepId);
                                Toast.makeText(ProbeAccessibilityService.this,
                                        "分支2已结束。请把游戏重新操作到分支判断处，再按分支1继续校准。",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                GuideSessionStore.Position mainPosition = GuideSessionStore.current(
                                        ProbeAccessibilityService.this,
                                        EditableGuideStore.load(ProbeAccessibilityService.this));
                                BranchStore.Definition updated = BranchStore.finishBranch1(
                                        ProbeAccessibilityService.this, mainPosition,
                                        file.getAbsolutePath());
                                if (updated == null) throw new IOException("分支1状态已经变化");
                                CorrectionStore.Record record = CorrectionStore.recordAction(
                                        ProbeAccessibilityService.this,
                                        CorrectionStore.ACTION_BRANCH1_END, updated.taskNumber,
                                        updated.taskTitle + " / 分支1", stepIndex, stepText,
                                        "分支1与分支2在此建立逻辑汇合；不要求两张动态整图完全一致。");
                                CorrectionStore.attachScreenshot(ProbeAccessibilityService.this,
                                        record.id, file.getAbsolutePath());
                                GuideSessionStore.next(ProbeAccessibilityService.this,
                                        EditableGuideStore.load(ProbeAccessibilityService.this));
                                Toast.makeText(ProbeAccessibilityService.this,
                                        "两个分支已逻辑汇合，继续后面的主流程。",
                                        Toast.LENGTH_LONG).show();
                            }
                            if (guideOverlay != null) guideOverlay.refresh();
                            restoreOverlays(restoreProbe, restoreGuide);
                        } catch (IOException | RuntimeException error) {
                            restoreOverlays(restoreProbe, restoreGuide);
                            fail("分支结束记录失败：" + error.getMessage());
                        }
                    }

                    @Override public void onFailure(int errorCode) {
                        restoreOverlays(restoreProbe, restoreGuide);
                        fail("分支结束截图失败，错误码=" + errorCode);
                    }
                }), 220);
    }

    private void writeScreenshotResult(ScreenshotResult screenshot, File... files)
            throws IOException {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        Bitmap wrapped = null;
        Bitmap copy = null;
        try {
            wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (wrapped == null) throw new IOException("无法读取硬件缓冲区");
            copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) throw new IOException("无法复制位图");
            for (File file : files) writeBitmap(copy, file);
        } finally {
            if (copy != null) copy.recycle();
            if (wrapped != null) wrapped.recycle();
            buffer.close();
        }
    }

    private void requestScreenshot(String correctionRecordId, int guideTaskNumber,
                                   int guideStepNumber, boolean openAnnotation) {
        requestScreenshot(correctionRecordId, guideTaskNumber, guideStepNumber,
                openAnnotation, null, null);
    }

    private void requestScreenshot(String correctionRecordId, int guideTaskNumber,
                                   int guideStepNumber, boolean openAnnotation,
                                   String branchId, String branchStepId) {
        boolean shouldRestoreOverlay = overlay != null && overlay.getVisibility() == View.VISIBLE;
        boolean shouldRestoreGuide = guideOverlay != null && guideOverlay.isShowing();
        if (shouldRestoreOverlay) overlay.setVisibility(View.INVISIBLE);
        if (shouldRestoreGuide) guideOverlay.hideTemporarily();
        new android.os.Handler(getMainLooper()).postDelayed(() -> takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY, mainExecutor,
                new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        saveScreenshot(screenshot, correctionRecordId, guideTaskNumber,
                                guideStepNumber, openAnnotation, branchId, branchStepId,
                                shouldRestoreOverlay, shouldRestoreGuide);
                    }

                    @Override public void onFailure(int errorCode) {
                        restoreOverlays(shouldRestoreOverlay, shouldRestoreGuide);
                        fail("截屏失败，错误码=" + errorCode);
                    }
                }), (shouldRestoreOverlay || shouldRestoreGuide) ? 220 : 0);
    }

    private void restoreOverlays(boolean restoreProbe, boolean restoreGuide) {
        if (restoreProbe && overlay != null) overlay.setVisibility(View.VISIBLE);
        if (restoreGuide && guideOverlay != null) guideOverlay.restoreAfterCapture();
    }

    private void saveScreenshot(ScreenshotResult screenshot, String correctionRecordId,
                                int guideTaskNumber, int guideStepNumber,
                                boolean openAnnotation, String branchId, String branchStepId,
                                boolean restoreProbe,
                                boolean restoreGuide) {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        Bitmap wrapped = null;
        Bitmap copy = null;
        boolean annotationStarted = false;
        try {
            wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (wrapped == null) {
                fail("截屏失败：无法读取硬件缓冲区");
                return;
            }
            copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
            if (copy == null) {
                fail("截屏失败：无法复制位图");
                return;
            }
            File file;
            File rawFile = null;
            if (correctionRecordId != null && openAnnotation) {
                rawFile = CorrectionStore.rawScreenshotFile(this, correctionRecordId);
                file = CorrectionStore.screenshotFile(this, correctionRecordId);
                writeBitmap(copy, rawFile);
                writeBitmap(copy, file);
            } else if (correctionRecordId != null) {
                file = CorrectionStore.screenshotFile(this, correctionRecordId);
                writeBitmap(copy, file);
            } else if (guideTaskNumber > 0 && guideStepNumber > 0) {
                file = EditableGuideStore.stepScreenshotFile(this, guideTaskNumber, guideStepNumber);
                writeBitmap(copy, file);
            } else {
                file = new File(outputDir(), "screen-" + fileStamp() + ".png");
                writeBitmap(copy, file);
            }
            if (correctionRecordId != null) {
                CorrectionStore.attachScreenshot(this, correctionRecordId, file.getAbsolutePath());
                if (rawFile != null) {
                    CorrectionStore.attachRawScreenshot(this, correctionRecordId,
                            rawFile.getAbsolutePath());
                }
            }
            if (guideTaskNumber > 0 && guideStepNumber > 0) {
                EditableGuideStore.attachStepScreenshot(
                        this, guideTaskNumber, guideStepNumber, file.getAbsolutePath());
                if (guideOverlay != null) guideOverlay.refresh();
            }
            if (branchId != null && branchStepId != null) {
                BranchStore.attachBranch2Screenshot(this, branchId, branchStepId,
                        file.getAbsolutePath());
                if (guideOverlay != null) guideOverlay.refresh();
            }
            String result;
            if (correctionRecordId != null) {
                result = "校准标记和现场截图已保存：" + file.getName();
            } else if (guideTaskNumber > 0 && guideStepNumber > 0) {
                result = "新步骤及清晰参考图已保存：" + file.getName();
            } else {
                result = "截屏成功：" + copy.getWidth() + "×" + copy.getHeight()
                        + "，已保存 " + file.getName();
            }
            ProbeStore.setResult(this, result);
            if (openAnnotation && rawFile != null) {
                Intent intent = new Intent(this, ScreenshotAnnotateActivity.class);
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_RAW_PATH, rawFile.getAbsolutePath());
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_OUTPUT_PATH, file.getAbsolutePath());
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_RECORD_ID, correctionRecordId);
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_TASK_NUMBER, guideTaskNumber);
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_STEP_NUMBER, guideStepNumber);
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_BRANCH_ID, branchId);
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_BRANCH_STEP_ID, branchStepId);
                intent.putExtra(ScreenshotAnnotateActivity.EXTRA_RETURN_TO_GAME, true);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                annotationStarted = true;
            } else {
                Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            }
        } catch (IOException | RuntimeException error) {
            fail("截屏保存失败：" + error.getMessage());
        } finally {
            if (!annotationStarted) restoreOverlays(restoreProbe, restoreGuide);
            if (copy != null) copy.recycle();
            if (wrapped != null) wrapped.recycle();
            buffer.close();
        }
    }

    private void writeBitmap(Bitmap bitmap, File file) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                throw new IOException("PNG 编码失败");
            }
        }
    }

    void resumeGuideAfterAnnotation() {
        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (GuideSessionStore.isActive(this)) showGuideOverlay();
        }, 350);
    }

    void performOwnAppClickTest() {
        if (!isOwnAppForeground()) {
            fail("安全限制：点击测试只能在本测试 App 前台执行");
            return;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            fail("点击测试失败：没有根节点");
            return;
        }
        java.util.List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(
                getPackageName() + ":id/probe_click_target");
        boolean performed = false;
        if (nodes != null && !nodes.isEmpty()) {
            performed = nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            for (AccessibilityNodeInfo node : nodes) node.recycle();
        }
        root.recycle();
        ProbeStore.setResult(this, performed ? "无障碍点击动作已发送" : "点击测试失败：未找到目标按钮");
        Toast.makeText(this, ProbeStore.lastResult(this), Toast.LENGTH_SHORT).show();
    }

    void performOwnAppSwipeTest() {
        if (!isOwnAppForeground()) {
            fail("安全限制：滑动测试只能在本测试 App 前台执行");
            return;
        }
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        Path path = new Path();
        path.moveTo(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.78f);
        path.lineTo(metrics.widthPixels * 0.5f, metrics.heightPixels * 0.30f);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 550))
                .build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                ProbeStore.setResult(ProbeAccessibilityService.this, "无障碍滑动测试成功");
                Toast.makeText(ProbeAccessibilityService.this, "滑动测试成功", Toast.LENGTH_SHORT).show();
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                fail("无障碍滑动测试被取消");
            }
        }, null);
    }

    void performOwnAppBackTest() {
        if (!isOwnAppForeground()) {
            fail("安全限制：返回测试只能在本测试 App 前台执行");
            return;
        }
        boolean ok = performGlobalAction(GLOBAL_ACTION_BACK);
        ProbeStore.setResult(this, ok ? "无障碍返回动作已发送" : "无障碍返回动作失败");
    }

    private boolean isOwnAppForeground() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        CharSequence packageName = root.getPackageName();
        boolean own = packageName != null && getPackageName().contentEquals(packageName);
        root.recycle();
        return own;
    }

    private File outputDir() {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = getFilesDir();
        File dir = new File(base, "probe");
        if (!dir.exists() && !dir.mkdirs()) {
            return base;
        }
        return dir;
    }

    private void writeText(File file, String text) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private void fail(String message) {
        ProbeStore.setResult(this, message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String quoted(Object value) {
        if (value == null) return "\"\"";
        return "\"" + value.toString().replace("\n", "\\n").replace("\"", "\\\"") + "\"";
    }

    private static StringBuilder indent(StringBuilder builder, int depth) {
        int safeDepth = Math.min(depth, 24);
        for (int i = 0; i < safeDepth; i++) builder.append("  ");
        return builder;
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
    }

    private static String fileStamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class NodeDepth {
        final AccessibilityNodeInfo node;
        final int depth;

        NodeDepth(AccessibilityNodeInfo node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
