package com.example.shiningprobe;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class CorrectionStore {
    private static final String FILE_NAME = "guide-corrections.json";
    static final String ACTION_ERROR = "error";
    static final String ACTION_INSERT_TASK = "insert_task";
    static final String ACTION_INSERT_STEP = "insert_step";
    static final String ACTION_DELETE_STEP = "delete_step";
    static final String ACTION_CLICK_POINT = "click_point";
    static final String ACTION_CREATE_BRANCH = "create_branch";
    static final String ACTION_BRANCH2_END = "branch2_end";
    static final String ACTION_BRANCH1_END = "branch1_end";

    private CorrectionStore() {}

    static synchronized Record mark(Context context, GuideSessionStore.Position position) {
        List<Record> records = load(context);
        long now = System.currentTimeMillis();
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date(now));
        Record record = new Record(id, GuideSessionStore.sessionId(context),
                position.task.number, position.task.title, position.step.index,
                position.step.text, now, ACTION_ERROR, "待补充说明", "", "");
        records.add(record);
        save(context, records);
        return record;
    }

    static synchronized Record recordAction(Context context, String actionType,
                                            int taskNumber, String taskTitle, int stepIndex,
                                            String stepText, String note) {
        List<Record> records = load(context);
        long now = System.currentTimeMillis();
        String id = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date(now));
        Record record = new Record(id, GuideSessionStore.sessionId(context),
                taskNumber, taskTitle, stepIndex, stepText, now, actionType,
                note, "", "");
        records.add(record);
        save(context, records);
        return record;
    }

    static synchronized void importUnloggedInsertedSteps(Context context, GuideCatalog catalog) {
        List<Record> records = load(context);
        boolean changed = false;
        for (GuideCatalog.GuideTask task : catalog.tasks) {
            for (GuideCatalog.GuideStep step : task.steps) {
                if (!step.text.startsWith("新插入步骤：")) continue;
                boolean exists = false;
                String screenshot = step.imageAsset.startsWith("file:")
                        ? step.imageAsset.substring("file:".length()) : "";
                for (Record record : records) {
                    if (ACTION_INSERT_STEP.equals(record.actionType)
                            && ((!screenshot.isEmpty() && screenshot.equals(record.screenshotPath))
                            || (record.taskNumber == task.number
                            && record.stepIndex == step.index
                            && record.stepText.equals(step.text)))) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;
                long now = System.currentTimeMillis();
                String id = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
                        .format(new Date(now)) + "-m" + task.number + "-" + step.index;
                records.add(new Record(id, "旧版本迁移", task.number, task.title,
                        step.index, step.text, now, ACTION_INSERT_STEP,
                        "从上一版恢复：此步骤由引导悬浮窗插入，请统一补充正式说明。",
                        screenshot, ""));
                changed = true;
            }
        }
        if (changed) save(context, records);
    }

    static synchronized List<Record> load(Context context) {
        File file = dataFile(context);
        if (!file.exists()) return new ArrayList<>();
        try {
            String text = readText(file);
            JSONArray array = new JSONArray(text);
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) records.add(Record.fromJson(array.getJSONObject(i)));
            records.sort(Comparator.comparingLong((Record item) -> item.createdAt).reversed());
            return records;
        } catch (IOException | JSONException | RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    static synchronized void updateNote(Context context, String id, String note) {
        List<Record> records = load(context);
        for (Record record : records) if (record.id.equals(id)) record.note = note.trim();
        save(context, records);
    }

    static synchronized void attachScreenshot(Context context, String id, String screenshotPath) {
        List<Record> records = load(context);
        for (Record record : records) if (record.id.equals(id)) record.screenshotPath = screenshotPath;
        save(context, records);
    }

    static synchronized void attachRawScreenshot(Context context, String id, String screenshotPath) {
        List<Record> records = load(context);
        for (Record record : records) if (record.id.equals(id)) record.rawScreenshotPath = screenshotPath;
        save(context, records);
    }

    static synchronized void updateClickPoint(Context context, String id,
                                              int x, int y, int imageWidth, int imageHeight) {
        List<Record> records = load(context);
        for (Record record : records) {
            if (!record.id.equals(id)) continue;
            record.clickX = x;
            record.clickY = y;
            record.imageWidth = imageWidth;
            record.imageHeight = imageHeight;
        }
        save(context, records);
    }

    static synchronized void attachBranchLink(Context context, String id,
                                              String branchId, String branchStepId) {
        List<Record> records = load(context);
        for (Record record : records) {
            if (!record.id.equals(id)) continue;
            record.branchId = branchId;
            record.branchStepId = branchStepId;
        }
        save(context, records);
    }

    static synchronized void delete(Context context, String id) {
        List<Record> records = load(context);
        records.removeIf(record -> record.id.equals(id));
        save(context, records);
    }

    static synchronized File exportMarkdown(Context context) throws IOException {
        List<Record> records = load(context);
        File dir = reviewDir(context);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File report = new File(dir, "闪暖流程校准-" + stamp + ".md");
        StringBuilder markdown = new StringBuilder();
        markdown.append("# 闪暖流程校准记录\n\n")
                .append("- 导出时间：").append(displayTime(System.currentTimeMillis())).append("\n")
                .append("- 标记数量：").append(records.size()).append("\n\n");
        int index = 1;
        for (Record record : records) {
            markdown.append("## ").append(index++).append(". ").append(record.actionLabel())
                    .append(" · 任务 ").append(record.taskNumber)
                    .append(" · ").append(record.taskTitle).append(" · 步骤 ")
                    .append(record.stepIndex).append("\n\n")
                    .append("- 操作类型：").append(record.actionLabel()).append("\n")
                    .append("- 标记时间：").append(displayTime(record.createdAt)).append("\n")
                    .append("- 会话：").append(record.sessionId).append("\n")
                    .append("- 原步骤：").append(record.stepText).append("\n")
                    .append("- 修改说明：").append(record.note.isEmpty() ? "待补充说明" : record.note).append("\n");
            if (!record.screenshotPath.isEmpty()) {
                markdown.append("- 现场截图：`").append(record.screenshotPath).append("`\n");
            }
            if (record.hasClickPoint()) {
                markdown.append("- 点击坐标：(").append(record.clickX).append(", ")
                        .append(record.clickY).append(") / ")
                        .append(record.imageWidth).append("×").append(record.imageHeight)
                        .append("，相对位置 ")
                        .append(String.format(Locale.CHINA, "%.1f%%, %.1f%%",
                                record.clickX * 100f / record.imageWidth,
                                record.clickY * 100f / record.imageHeight))
                        .append("\n");
            }
            markdown.append('\n');
        }
        writeText(report, markdown.toString());
        try {
            writeText(new File(dir, "闪暖流程校准-" + stamp + ".json"), toJson(records).toString(2));
        } catch (JSONException error) {
            throw new IOException("JSON 汇总生成失败", error);
        }
        BranchStore.export(context, dir, stamp);
        return report;
    }

    static File screenshotFile(Context context, String recordId) {
        File screenshots = new File(reviewDir(context), "screenshots");
        if (!screenshots.exists()) screenshots.mkdirs();
        return new File(screenshots, "mark-" + recordId + ".png");
    }

    static File rawScreenshotFile(Context context, String recordId) {
        File screenshots = new File(reviewDir(context), "screenshots");
        if (!screenshots.exists()) screenshots.mkdirs();
        return new File(screenshots, "mark-" + recordId + "-raw.png");
    }

    static File reviewDir(Context context) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File dir = new File(base, "guide-review");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static String displayTime(long value) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date(value));
    }

    private static File dataFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void save(Context context, List<Record> records) {
        try {
            writeText(dataFile(context), toJson(records).toString());
        } catch (IOException | JSONException ignored) {
        }
    }

    private static JSONArray toJson(List<Record> records) throws JSONException {
        JSONArray array = new JSONArray();
        for (Record record : records) array.put(record.toJson());
        return array;
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = stream.read(buffer, offset, buffer.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    static final class Record {
        final String id;
        final String sessionId;
        final int taskNumber;
        final String taskTitle;
        final int stepIndex;
        final String stepText;
        final long createdAt;
        final String actionType;
        String note;
        String screenshotPath;
        String rawScreenshotPath;
        int clickX = -1;
        int clickY = -1;
        int imageWidth;
        int imageHeight;
        String branchId = "";
        String branchStepId = "";

        Record(String id, String sessionId, int taskNumber, String taskTitle, int stepIndex,
               String stepText, long createdAt, String actionType, String note,
               String screenshotPath, String rawScreenshotPath) {
            this.id = id;
            this.sessionId = sessionId;
            this.taskNumber = taskNumber;
            this.taskTitle = taskTitle;
            this.stepIndex = stepIndex;
            this.stepText = stepText;
            this.createdAt = createdAt;
            this.actionType = actionType;
            this.note = note;
            this.screenshotPath = screenshotPath;
            this.rawScreenshotPath = rawScreenshotPath;
        }

        String actionLabel() {
            if (ACTION_INSERT_TASK.equals(actionType)) return "插入大流程";
            if (ACTION_INSERT_STEP.equals(actionType)) return "插入小步骤";
            if (ACTION_DELETE_STEP.equals(actionType)) return "删除当前步骤";
            if (ACTION_CLICK_POINT.equals(actionType)) return "补充点击位置";
            if (ACTION_CREATE_BRANCH.equals(actionType)) return "创建分支2";
            if (ACTION_BRANCH2_END.equals(actionType)) return "分支2结束";
            if (ACTION_BRANCH1_END.equals(actionType)) return "分支1结束并汇合";
            return "标记有误";
        }

        boolean hasClickPoint() {
            return clickX >= 0 && clickY >= 0 && imageWidth > 0 && imageHeight > 0;
        }

        String clickPointText() {
            if (!hasClickPoint()) return "尚未标注点击位置";
            return "点击位置：(" + clickX + ", " + clickY + ") / "
                    + imageWidth + "×" + imageHeight + "，相对位置 "
                    + String.format(Locale.CHINA, "%.1f%%, %.1f%%",
                    clickX * 100f / imageWidth, clickY * 100f / imageHeight);
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("id", id)
                    .put("sessionId", sessionId)
                    .put("taskNumber", taskNumber)
                    .put("taskTitle", taskTitle)
                    .put("stepIndex", stepIndex)
                    .put("stepText", stepText)
                    .put("createdAt", createdAt)
                    .put("actionType", actionType)
                    .put("note", note)
                    .put("screenshotPath", screenshotPath)
                    .put("rawScreenshotPath", rawScreenshotPath)
                    .put("clickX", clickX)
                    .put("clickY", clickY)
                    .put("imageWidth", imageWidth)
                    .put("imageHeight", imageHeight)
                    .put("branchId", branchId)
                    .put("branchStepId", branchStepId);
        }

        static Record fromJson(JSONObject object) {
            Record record = new Record(object.optString("id"), object.optString("sessionId"),
                    object.optInt("taskNumber"), object.optString("taskTitle"),
                    object.optInt("stepIndex"), object.optString("stepText"),
                    object.optLong("createdAt"), object.optString("actionType", ACTION_ERROR),
                    object.optString("note"), object.optString("screenshotPath"),
                    object.optString("rawScreenshotPath"));
            record.clickX = object.optInt("clickX", -1);
            record.clickY = object.optInt("clickY", -1);
            record.imageWidth = object.optInt("imageWidth");
            record.imageHeight = object.optInt("imageHeight");
            record.branchId = object.optString("branchId");
            record.branchStepId = object.optString("branchStepId");
            return record;
        }
    }
}
