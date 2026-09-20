package com.example.shiningprobe;

import android.content.Context;

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
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class EditableGuideStore {
    private static final String FILE_NAME = "editable-guide.json";

    private EditableGuideStore() {}

    static synchronized GuideCatalog load(Context context) {
        File file = dataFile(context);
        if (!file.exists()) return GuideCatalog.load(context);
        try {
            JSONArray array = new JSONArray(readText(file));
            List<GuideCatalog.GuideTask> tasks = fromJson(array);
            if (tasks.isEmpty()) return GuideCatalog.load(context);
            return new GuideCatalog(tasks, "");
        } catch (IOException | JSONException | RuntimeException error) {
            GuideCatalog builtIn = GuideCatalog.load(context);
            return new GuideCatalog(new ArrayList<>(builtIn.tasks),
                    "手机端流程读取失败，已临时使用内置流程：" + error.getMessage());
        }
    }

    static synchronized boolean hasCustomFlow(Context context) {
        return dataFile(context).exists();
    }

    static synchronized void ensureStableIds(Context context) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        for (GuideCatalog.GuideTask task : tasks) {
            for (GuideCatalog.GuideStep step : task.steps) {
                if (step.id == null || step.id.isEmpty()) step.id = newStepId();
            }
        }
        save(context, renumber(tasks));
    }

    static synchronized int insertTask(Context context, int insertionIndex,
                                       String title, String firstStepText) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int safeIndex = Math.max(0, Math.min(insertionIndex, tasks.size()));
        GuideCatalog.GuideTask task = new GuideCatalog.GuideTask(0, title.trim(), "");
        GuideCatalog.GuideStep firstStep = new GuideCatalog.GuideStep(1, firstStepText.trim());
        firstStep.id = newStepId();
        task.steps.add(firstStep);
        tasks.add(safeIndex, task);
        save(context, renumber(tasks));
        GuideSessionStore.onTaskInserted(context, safeIndex + 1);
        return safeIndex + 1;
    }

    static synchronized void editTaskTitle(Context context, int taskNumber, String title) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int index = taskNumber - 1;
        if (index < 0 || index >= tasks.size()) return;
        GuideCatalog.GuideTask old = tasks.get(index);
        GuideCatalog.GuideTask replacement = new GuideCatalog.GuideTask(
                old.number, title.trim(), old.timing);
        replacement.notes.addAll(old.notes);
        for (GuideCatalog.GuideStep step : old.steps) {
            GuideCatalog.GuideStep copy = new GuideCatalog.GuideStep(step.index, step.text);
            copyStepData(step, copy);
            replacement.steps.add(copy);
        }
        tasks.set(index, replacement);
        save(context, renumber(tasks));
    }

    static synchronized int insertStep(Context context, int taskNumber,
                                       int insertionIndex, String text) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int taskIndex = taskNumber - 1;
        if (taskIndex < 0 || taskIndex >= tasks.size()) return -1;
        GuideCatalog.GuideTask task = tasks.get(taskIndex);
        int safeIndex = Math.max(0, Math.min(insertionIndex, task.steps.size()));
        GuideCatalog.GuideStep inserted = new GuideCatalog.GuideStep(0, text.trim());
        inserted.id = newStepId();
        task.steps.add(safeIndex, inserted);
        save(context, renumber(tasks));
        GuideSessionStore.onStepInserted(context, taskNumber, safeIndex);
        return safeIndex + 1;
    }

    static synchronized void editStep(Context context, int taskNumber,
                                      int stepNumber, String text) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int taskIndex = taskNumber - 1;
        if (taskIndex < 0 || taskIndex >= tasks.size()) return;
        GuideCatalog.GuideTask task = tasks.get(taskIndex);
        int stepIndex = stepNumber - 1;
        if (stepIndex < 0 || stepIndex >= task.steps.size()) return;
        GuideCatalog.GuideStep old = task.steps.get(stepIndex);
        GuideCatalog.GuideStep replacement = new GuideCatalog.GuideStep(old.index, text.trim());
        copyStepData(old, replacement);
        task.steps.set(stepIndex, replacement);
        save(context, renumber(tasks));
    }

    static synchronized boolean deleteStep(Context context, int taskNumber, int stepNumber) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int taskIndex = taskNumber - 1;
        if (taskIndex < 0 || taskIndex >= tasks.size()) return false;
        GuideCatalog.GuideTask task = tasks.get(taskIndex);
        int stepIndex = stepNumber - 1;
        if (stepIndex < 0 || stepIndex >= task.steps.size()) return false;
        if (task.steps.size() == 1) {
            tasks.remove(taskIndex);
            if (tasks.isEmpty()) return false;
            save(context, renumber(tasks));
            GuideSessionStore.onTaskDeleted(context, taskNumber);
        } else {
            task.steps.remove(stepIndex);
            int remainingCount = task.steps.size();
            save(context, renumber(tasks));
            GuideSessionStore.onStepDeleted(context, taskNumber, stepIndex, remainingCount);
        }
        return true;
    }

    static synchronized void attachStepScreenshot(Context context, int taskNumber,
                                                  int stepNumber, String screenshotPath) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int taskIndex = taskNumber - 1;
        if (taskIndex < 0 || taskIndex >= tasks.size()) return;
        GuideCatalog.GuideTask task = tasks.get(taskIndex);
        int stepIndex = stepNumber - 1;
        if (stepIndex < 0 || stepIndex >= task.steps.size()) return;
        GuideCatalog.GuideStep old = task.steps.get(stepIndex);
        GuideCatalog.GuideStep replacement = new GuideCatalog.GuideStep(old.index, old.text);
        copyStepData(old, replacement);
        replacement.imageAsset = "file:" + screenshotPath;
        task.steps.set(stepIndex, replacement);
        save(context, renumber(tasks));
    }

    static synchronized void attachStepClickPoint(Context context, int taskNumber, int stepNumber,
                                                  String screenshotPath, int x, int y,
                                                  int imageWidth, int imageHeight) {
        List<GuideCatalog.GuideTask> tasks = copyTasks(load(context));
        int taskIndex = taskNumber - 1;
        if (taskIndex < 0 || taskIndex >= tasks.size()) return;
        GuideCatalog.GuideTask task = tasks.get(taskIndex);
        int stepIndex = stepNumber - 1;
        if (stepIndex < 0 || stepIndex >= task.steps.size()) return;
        GuideCatalog.GuideStep old = task.steps.get(stepIndex);
        GuideCatalog.GuideStep replacement = new GuideCatalog.GuideStep(old.index, old.text);
        copyStepData(old, replacement);
        replacement.imageAsset = "file:" + screenshotPath;
        replacement.clickX = x;
        replacement.clickY = y;
        replacement.imageWidth = imageWidth;
        replacement.imageHeight = imageHeight;
        task.steps.set(stepIndex, replacement);
        save(context, renumber(tasks));
    }

    static File stepScreenshotFile(Context context, int taskNumber, int stepNumber) {
        File dir = new File(CorrectionStore.reviewDir(context), "flow-images");
        if (!dir.exists()) dir.mkdirs();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        return new File(dir, "task-" + taskNumber + "-step-" + stepNumber + "-" + stamp + ".png");
    }

    static synchronized void resetToBuiltIn(Context context) {
        File file = dataFile(context);
        if (file.exists()) file.delete();
    }

    static synchronized File export(Context context) throws IOException {
        GuideCatalog catalog = load(context);
        File dir = CorrectionStore.reviewDir(context);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File markdownFile = new File(dir, "手机端流程结构-" + stamp + ".md");
        File jsonFile = new File(dir, "手机端流程结构-" + stamp + ".json");
        writeText(markdownFile, toMarkdown(catalog));
        try {
            writeText(jsonFile, toJson(catalog.tasks).toString(2));
        } catch (JSONException error) {
            throw new IOException("流程 JSON 生成失败", error);
        }
        return markdownFile;
    }

    private static List<GuideCatalog.GuideTask> copyTasks(GuideCatalog catalog) {
        List<GuideCatalog.GuideTask> result = new ArrayList<>();
        for (GuideCatalog.GuideTask source : catalog.tasks) {
            GuideCatalog.GuideTask task = new GuideCatalog.GuideTask(
                    source.number, source.title, source.timing);
            task.notes.addAll(source.notes);
            for (GuideCatalog.GuideStep sourceStep : source.steps) {
                GuideCatalog.GuideStep step = new GuideCatalog.GuideStep(
                        sourceStep.index, sourceStep.text);
                copyStepData(sourceStep, step);
                task.steps.add(step);
            }
            result.add(task);
        }
        return result;
    }

    private static List<GuideCatalog.GuideTask> renumber(List<GuideCatalog.GuideTask> source) {
        List<GuideCatalog.GuideTask> result = new ArrayList<>();
        for (int taskIndex = 0; taskIndex < source.size(); taskIndex++) {
            GuideCatalog.GuideTask oldTask = source.get(taskIndex);
            GuideCatalog.GuideTask task = new GuideCatalog.GuideTask(
                    taskIndex + 1, oldTask.title, oldTask.timing);
            task.notes.addAll(oldTask.notes);
            for (int stepIndex = 0; stepIndex < oldTask.steps.size(); stepIndex++) {
                GuideCatalog.GuideStep oldStep = oldTask.steps.get(stepIndex);
                GuideCatalog.GuideStep step = new GuideCatalog.GuideStep(
                        stepIndex + 1, oldStep.text);
                copyStepData(oldStep, step);
                task.steps.add(step);
            }
            result.add(task);
        }
        return result;
    }

    private static void save(Context context, List<GuideCatalog.GuideTask> tasks) {
        try {
            writeText(dataFile(context), toJson(tasks).toString());
        } catch (IOException | JSONException ignored) {
        }
    }

    private static JSONArray toJson(List<GuideCatalog.GuideTask> tasks) throws JSONException {
        JSONArray taskArray = new JSONArray();
        for (GuideCatalog.GuideTask task : tasks) {
            JSONArray steps = new JSONArray();
            for (GuideCatalog.GuideStep step : task.steps) {
                steps.put(new JSONObject()
                        .put("id", step.id)
                        .put("text", step.text)
                        .put("imageAsset", step.imageAsset)
                        .put("clickX", step.clickX)
                        .put("clickY", step.clickY)
                        .put("imageWidth", step.imageWidth)
                        .put("imageHeight", step.imageHeight));
            }
            JSONArray notes = new JSONArray();
            for (String note : task.notes) notes.put(note);
            taskArray.put(new JSONObject()
                    .put("title", task.title)
                    .put("timing", task.timing)
                    .put("notes", notes)
                    .put("steps", steps));
        }
        return taskArray;
    }

    private static List<GuideCatalog.GuideTask> fromJson(JSONArray array) throws JSONException {
        List<GuideCatalog.GuideTask> tasks = new ArrayList<>();
        for (int taskIndex = 0; taskIndex < array.length(); taskIndex++) {
            JSONObject object = array.getJSONObject(taskIndex);
            GuideCatalog.GuideTask task = new GuideCatalog.GuideTask(
                    taskIndex + 1, object.optString("title"), object.optString("timing"));
            JSONArray notes = object.optJSONArray("notes");
            if (notes != null) {
                for (int i = 0; i < notes.length(); i++) task.notes.add(notes.optString(i));
            }
            JSONArray steps = object.optJSONArray("steps");
            if (steps != null) {
                for (int stepIndex = 0; stepIndex < steps.length(); stepIndex++) {
                    JSONObject stepObject = steps.getJSONObject(stepIndex);
                    GuideCatalog.GuideStep step = new GuideCatalog.GuideStep(
                            stepIndex + 1, stepObject.optString("text"));
                    step.id = stepObject.optString("id",
                            "legacy-t" + (taskIndex + 1) + "-s" + (stepIndex + 1));
                    step.imageAsset = stepObject.optString("imageAsset");
                    step.clickX = stepObject.optInt("clickX", -1);
                    step.clickY = stepObject.optInt("clickY", -1);
                    step.imageWidth = stepObject.optInt("imageWidth");
                    step.imageHeight = stepObject.optInt("imageHeight");
                    task.steps.add(step);
                }
            }
            tasks.add(task);
        }
        return tasks;
    }

    private static String toMarkdown(GuideCatalog catalog) {
        StringBuilder output = new StringBuilder();
        output.append("# 手机端编辑后的闪暖流程结构\n\n")
                .append("> 此文件用于把手机端插入、顺延和文字修改结果统一同步回正式流程。\n\n")
                .append("## 三、日活步骤\n\n");
        for (GuideCatalog.GuideTask task : catalog.tasks) {
            output.append("### ").append(task.number).append(". ").append(task.title)
                    .append(task.timing).append("\n\n");
            for (GuideCatalog.GuideStep step : task.steps) {
                output.append("1. ").append(step.text).append("\n\n");
                if (step.imageAsset.startsWith("file:")) {
                    output.append("   > 手机端现场参考图：`")
                            .append(step.imageAsset.substring("file:".length()))
                            .append("`\n\n");
                } else if (!step.imageAsset.isEmpty()) {
                    String name = step.imageAsset.replace("guide/images/", "");
                    output.append("   <img src=\"./闪暖日活流程图片/").append(name)
                            .append("\" alt=\"流程参考图\" style=\"zoom:25%;\" />\n\n");
                } else {
                    output.append("   > 新插入步骤：暂无参考图。\n\n");
                }
                if (step.hasClickPoint()) {
                    output.append("   > 点击坐标：(").append(step.clickX).append(", ")
                            .append(step.clickY).append(") / ")
                            .append(step.imageWidth).append("×").append(step.imageHeight)
                            .append(String.format(Locale.CHINA, "，相对位置 %.1f%%, %.1f%%",
                                    step.clickX * 100f / step.imageWidth,
                                    step.clickY * 100f / step.imageHeight))
                            .append("\n\n");
                }
            }
            for (String note : task.notes) output.append(note).append("\n\n");
        }
        return output.toString();
    }

    private static File dataFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void copyStepData(GuideCatalog.GuideStep source,
                                     GuideCatalog.GuideStep target) {
        target.imageAsset = source.imageAsset;
        target.id = source.id;
        target.clickX = source.clickX;
        target.clickY = source.clickY;
        target.imageWidth = source.imageWidth;
        target.imageHeight = source.imageHeight;
    }

    private static String newStepId() {
        return "mobile-" + System.currentTimeMillis() + "-"
                + Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
    }

    private static String readText(File file) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = stream.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(data, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private static void writeText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
