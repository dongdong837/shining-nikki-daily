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

final class BranchStore {
    static final String PHASE_CONDITION = "condition";
    static final String PHASE_BRANCH2 = "branch2";
    static final String PHASE_BRANCH1 = "branch1";
    static final String PHASE_COMPLETE = "complete";
    private static final String FILE_NAME = "guide-branches.json";

    private BranchStore() {}

    static synchronized Definition createDraft(Context context,
                                               GuideSessionStore.Position position) {
        if (position == null) return null;
        int currentIndex = position.step.index - 1;
        if (currentIndex + 1 >= position.task.steps.size()) return null;
        List<Definition> definitions = load(context);
        for (Definition existing : definitions) {
            if (!PHASE_COMPLETE.equals(existing.phase)) return null;
        }
        Definition definition = new Definition();
        definition.id = "branch-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date());
        definition.sessionId = GuideSessionStore.sessionId(context);
        definition.taskNumber = position.task.number;
        definition.taskTitle = position.task.title;
        definition.decisionStepId = position.step.id;
        definition.decisionStepIndex = position.step.index;
        definition.decisionStepText = position.step.text;
        definition.branch1StartStepId = position.task.steps.get(currentIndex + 1).id;
        definition.phase = PHASE_CONDITION;
        definition.createdAt = System.currentTimeMillis();
        definitions.add(definition);
        save(context, definitions);
        return definition;
    }

    static synchronized Definition active(Context context) {
        List<Definition> definitions = load(context);
        for (int i = definitions.size() - 1; i >= 0; i--) {
            Definition definition = definitions.get(i);
            if (!PHASE_COMPLETE.equals(definition.phase)) return definition;
        }
        return null;
    }

    static synchronized Definition find(Context context, String id) {
        for (Definition definition : load(context)) if (definition.id.equals(id)) return definition;
        return null;
    }

    static synchronized void cancelDraft(Context context, String id) {
        List<Definition> definitions = load(context);
        definitions.removeIf(item -> item.id.equals(id) && PHASE_CONDITION.equals(item.phase));
        save(context, definitions);
    }

    static synchronized boolean attachCondition(Context context, String id,
                                                String rawPath, String annotatedPath,
                                                int left, int top, int right, int bottom,
                                                int imageWidth, int imageHeight) {
        List<Definition> definitions = load(context);
        Definition definition = findIn(definitions, id);
        if (definition == null) return false;
        definition.conditionRawPath = rawPath;
        definition.conditionImagePath = annotatedPath;
        definition.conditionLeft = left;
        definition.conditionTop = top;
        definition.conditionRight = right;
        definition.conditionBottom = bottom;
        definition.conditionImageWidth = imageWidth;
        definition.conditionImageHeight = imageHeight;
        definition.phase = PHASE_BRANCH2;
        if (definition.branch2Steps.isEmpty()) {
            BranchStep step = new BranchStep();
            step.id = newStepId();
            step.text = "分支2步骤1：请记录该分支的第一个操作。";
            definition.branch2Steps.add(step);
            definition.branch2Position = 0;
        }
        save(context, definitions);
        return true;
    }

    static synchronized BranchStepPosition currentBranch2(Context context) {
        Definition definition = active(context);
        if (definition == null || !PHASE_BRANCH2.equals(definition.phase)
                || definition.branch2Steps.isEmpty()) return null;
        definition.branch2Position = Math.max(0,
                Math.min(definition.branch2Position, definition.branch2Steps.size() - 1));
        return new BranchStepPosition(definition,
                definition.branch2Steps.get(definition.branch2Position),
                definition.branch2Position, definition.branch2Steps.size());
    }

    static synchronized int insertBranch2Step(Context context, String text) {
        List<Definition> definitions = load(context);
        Definition definition = activeIn(definitions);
        if (definition == null || !PHASE_BRANCH2.equals(definition.phase)) return -1;
        int insertionIndex = Math.max(0, Math.min(
                definition.branch2Position + 1, definition.branch2Steps.size()));
        BranchStep step = new BranchStep();
        step.id = newStepId();
        step.text = text;
        definition.branch2Steps.add(insertionIndex, step);
        definition.branch2Position = insertionIndex;
        save(context, definitions);
        return insertionIndex + 1;
    }

    static synchronized boolean deleteCurrentBranch2Step(Context context) {
        List<Definition> definitions = load(context);
        Definition definition = activeIn(definitions);
        if (definition == null || !PHASE_BRANCH2.equals(definition.phase)
                || definition.branch2Steps.size() <= 1) return false;
        int position = Math.max(0, Math.min(
                definition.branch2Position, definition.branch2Steps.size() - 1));
        definition.branch2Steps.remove(position);
        definition.branch2Position = Math.min(position, definition.branch2Steps.size() - 1);
        save(context, definitions);
        return true;
    }

    static synchronized boolean nextBranch2Step(Context context) {
        List<Definition> definitions = load(context);
        Definition definition = activeIn(definitions);
        if (definition == null || !PHASE_BRANCH2.equals(definition.phase)) return false;
        if (definition.branch2Position + 1 >= definition.branch2Steps.size()) return false;
        definition.branch2Position++;
        save(context, definitions);
        return true;
    }

    static synchronized boolean previousBranch2Step(Context context) {
        List<Definition> definitions = load(context);
        Definition definition = activeIn(definitions);
        if (definition == null || !PHASE_BRANCH2.equals(definition.phase)
                || definition.branch2Position <= 0) return false;
        definition.branch2Position--;
        save(context, definitions);
        return true;
    }

    static synchronized void attachBranch2Screenshot(Context context, String branchId,
                                                     String stepId, String path) {
        List<Definition> definitions = load(context);
        BranchStep step = findBranchStep(definitions, branchId, stepId);
        if (step == null) return;
        step.imageAsset = "file:" + path;
        save(context, definitions);
    }

    static synchronized void attachBranch2ClickPoint(Context context, String branchId,
                                                     String stepId, String path, int x, int y,
                                                     int width, int height) {
        List<Definition> definitions = load(context);
        BranchStep step = findBranchStep(definitions, branchId, stepId);
        if (step == null) return;
        step.imageAsset = "file:" + path;
        step.clickX = x;
        step.clickY = y;
        step.imageWidth = width;
        step.imageHeight = height;
        save(context, definitions);
    }

    static synchronized Definition finishBranch2(Context context, String screenshotPath) {
        List<Definition> definitions = load(context);
        Definition definition = activeIn(definitions);
        if (definition == null || !PHASE_BRANCH2.equals(definition.phase)
                || definition.branch2Steps.isEmpty()) return null;
        int position = Math.max(0, Math.min(
                definition.branch2Position, definition.branch2Steps.size() - 1));
        definition.branch2EndStepId = definition.branch2Steps.get(position).id;
        definition.branch2EndScreenshotPath = screenshotPath;
        definition.phase = PHASE_BRANCH1;
        save(context, definitions);
        return definition;
    }

    static synchronized Definition finishBranch1(Context context,
                                                  GuideSessionStore.Position position,
                                                  String screenshotPath) {
        List<Definition> definitions = load(context);
        Definition definition = activeIn(definitions);
        if (definition == null || !PHASE_BRANCH1.equals(definition.phase)
                || position == null || position.task.number != definition.taskNumber) return null;
        definition.branch1EndStepId = position.step.id;
        definition.branch1EndStepIndex = position.step.index;
        definition.branch1EndScreenshotPath = screenshotPath;
        definition.phase = PHASE_COMPLETE;
        definition.completedAt = System.currentTimeMillis();
        save(context, definitions);
        return definition;
    }

    static File screenshotFile(Context context, String branchId, String label) {
        File dir = new File(CorrectionStore.reviewDir(context), "branches");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, branchId + "-" + label + ".png");
    }

    static synchronized File export(Context context, File dir, String stamp) throws IOException {
        List<Definition> definitions = load(context);
        File json = new File(dir, "闪暖流程分支-" + stamp + ".json");
        File markdown = new File(dir, "闪暖流程分支-" + stamp + ".md");
        try {
            writeText(json, toJson(definitions).toString(2));
        } catch (JSONException error) {
            throw new IOException("分支 JSON 生成失败", error);
        }
        StringBuilder output = new StringBuilder("# 闪暖流程分支记录\n\n");
        for (Definition definition : definitions) {
            output.append("## 任务 ").append(definition.taskNumber).append(" · ")
                    .append(definition.taskTitle).append("\n\n")
                    .append("- 分支 ID：`").append(definition.id).append("`\n")
                    .append("- 分支判断位于步骤：").append(definition.decisionStepIndex)
                    .append(" 之后\n")
                    .append("- 判断区域：(").append(definition.conditionLeft).append(", ")
                    .append(definition.conditionTop).append(") - (")
                    .append(definition.conditionRight).append(", ")
                    .append(definition.conditionBottom).append(") / ")
                    .append(definition.conditionImageWidth).append("×")
                    .append(definition.conditionImageHeight).append("\n")
                    .append("- 分支2步骤数：").append(definition.branch2Steps.size()).append("\n")
                    .append("- 分支1结束步骤：").append(definition.branch1EndStepIndex).append("\n")
                    .append("- 汇合方式：人工确认的逻辑汇合，不比较动态整图\n\n");
            for (int i = 0; i < definition.branch2Steps.size(); i++) {
                BranchStep step = definition.branch2Steps.get(i);
                output.append(i + 1).append(". ").append(step.text).append("\n");
            }
            output.append('\n');
        }
        writeText(markdown, output.toString());
        return markdown;
    }

    private static List<Definition> load(Context context) {
        File file = dataFile(context);
        if (!file.exists()) return new ArrayList<>();
        try {
            JSONArray array = new JSONArray(readText(file));
            List<Definition> definitions = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                definitions.add(Definition.fromJson(array.getJSONObject(i)));
            }
            return definitions;
        } catch (IOException | JSONException | RuntimeException error) {
            return new ArrayList<>();
        }
    }

    private static void save(Context context, List<Definition> definitions) {
        try {
            writeText(dataFile(context), toJson(definitions).toString());
        } catch (IOException | JSONException ignored) {
        }
    }

    private static JSONArray toJson(List<Definition> definitions) throws JSONException {
        JSONArray array = new JSONArray();
        for (Definition definition : definitions) array.put(definition.toJson());
        return array;
    }

    private static Definition activeIn(List<Definition> definitions) {
        for (int i = definitions.size() - 1; i >= 0; i--) {
            if (!PHASE_COMPLETE.equals(definitions.get(i).phase)) return definitions.get(i);
        }
        return null;
    }

    private static Definition findIn(List<Definition> definitions, String id) {
        for (Definition definition : definitions) if (definition.id.equals(id)) return definition;
        return null;
    }

    private static BranchStep findBranchStep(List<Definition> definitions,
                                             String branchId, String stepId) {
        Definition definition = findIn(definitions, branchId);
        if (definition == null) return null;
        for (BranchStep step : definition.branch2Steps) if (step.id.equals(stepId)) return step;
        return null;
    }

    private static String newStepId() {
        return "branch-step-" + System.currentTimeMillis() + "-"
                + Integer.toHexString((int) (Math.random() * Integer.MAX_VALUE));
    }

    private static File dataFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
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

    static final class Definition {
        String id = "";
        String sessionId = "";
        int taskNumber;
        String taskTitle = "";
        String decisionStepId = "";
        int decisionStepIndex;
        String decisionStepText = "";
        String branch1StartStepId = "";
        String phase = PHASE_CONDITION;
        long createdAt;
        long completedAt;
        String conditionRawPath = "";
        String conditionImagePath = "";
        int conditionLeft;
        int conditionTop;
        int conditionRight;
        int conditionBottom;
        int conditionImageWidth;
        int conditionImageHeight;
        final List<BranchStep> branch2Steps = new ArrayList<>();
        int branch2Position;
        String branch2EndStepId = "";
        String branch2EndScreenshotPath = "";
        String branch1EndStepId = "";
        int branch1EndStepIndex;
        String branch1EndScreenshotPath = "";

        JSONObject toJson() throws JSONException {
            JSONArray steps = new JSONArray();
            for (BranchStep step : branch2Steps) steps.put(step.toJson());
            return new JSONObject()
                    .put("id", id).put("sessionId", sessionId)
                    .put("taskNumber", taskNumber).put("taskTitle", taskTitle)
                    .put("decisionStepId", decisionStepId)
                    .put("decisionStepIndex", decisionStepIndex)
                    .put("decisionStepText", decisionStepText)
                    .put("branch1StartStepId", branch1StartStepId)
                    .put("phase", phase).put("createdAt", createdAt)
                    .put("completedAt", completedAt)
                    .put("conditionRawPath", conditionRawPath)
                    .put("conditionImagePath", conditionImagePath)
                    .put("conditionLeft", conditionLeft).put("conditionTop", conditionTop)
                    .put("conditionRight", conditionRight).put("conditionBottom", conditionBottom)
                    .put("conditionImageWidth", conditionImageWidth)
                    .put("conditionImageHeight", conditionImageHeight)
                    .put("branch2Steps", steps).put("branch2Position", branch2Position)
                    .put("branch2EndStepId", branch2EndStepId)
                    .put("branch2EndScreenshotPath", branch2EndScreenshotPath)
                    .put("branch1EndStepId", branch1EndStepId)
                    .put("branch1EndStepIndex", branch1EndStepIndex)
                    .put("branch1EndScreenshotPath", branch1EndScreenshotPath);
        }

        static Definition fromJson(JSONObject object) throws JSONException {
            Definition definition = new Definition();
            definition.id = object.optString("id");
            definition.sessionId = object.optString("sessionId");
            definition.taskNumber = object.optInt("taskNumber");
            definition.taskTitle = object.optString("taskTitle");
            definition.decisionStepId = object.optString("decisionStepId");
            definition.decisionStepIndex = object.optInt("decisionStepIndex");
            definition.decisionStepText = object.optString("decisionStepText");
            definition.branch1StartStepId = object.optString("branch1StartStepId");
            definition.phase = object.optString("phase", PHASE_CONDITION);
            definition.createdAt = object.optLong("createdAt");
            definition.completedAt = object.optLong("completedAt");
            definition.conditionRawPath = object.optString("conditionRawPath");
            definition.conditionImagePath = object.optString("conditionImagePath");
            definition.conditionLeft = object.optInt("conditionLeft");
            definition.conditionTop = object.optInt("conditionTop");
            definition.conditionRight = object.optInt("conditionRight");
            definition.conditionBottom = object.optInt("conditionBottom");
            definition.conditionImageWidth = object.optInt("conditionImageWidth");
            definition.conditionImageHeight = object.optInt("conditionImageHeight");
            JSONArray steps = object.optJSONArray("branch2Steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    definition.branch2Steps.add(BranchStep.fromJson(steps.getJSONObject(i)));
                }
            }
            definition.branch2Position = object.optInt("branch2Position");
            definition.branch2EndStepId = object.optString("branch2EndStepId");
            definition.branch2EndScreenshotPath = object.optString("branch2EndScreenshotPath");
            definition.branch1EndStepId = object.optString("branch1EndStepId");
            definition.branch1EndStepIndex = object.optInt("branch1EndStepIndex");
            definition.branch1EndScreenshotPath = object.optString("branch1EndScreenshotPath");
            return definition;
        }
    }

    static final class BranchStep {
        String id = "";
        String text = "";
        String imageAsset = "";
        int clickX = -1;
        int clickY = -1;
        int imageWidth;
        int imageHeight;

        boolean hasClickPoint() {
            return clickX >= 0 && clickY >= 0 && imageWidth > 0 && imageHeight > 0;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("text", text)
                    .put("imageAsset", imageAsset).put("clickX", clickX).put("clickY", clickY)
                    .put("imageWidth", imageWidth).put("imageHeight", imageHeight);
        }

        static BranchStep fromJson(JSONObject object) {
            BranchStep step = new BranchStep();
            step.id = object.optString("id");
            step.text = object.optString("text");
            step.imageAsset = object.optString("imageAsset");
            step.clickX = object.optInt("clickX", -1);
            step.clickY = object.optInt("clickY", -1);
            step.imageWidth = object.optInt("imageWidth");
            step.imageHeight = object.optInt("imageHeight");
            return step;
        }
    }

    static final class BranchStepPosition {
        final Definition definition;
        final BranchStep step;
        final int position;
        final int count;

        BranchStepPosition(Definition definition, BranchStep step, int position, int count) {
            this.definition = definition;
            this.step = step;
            this.position = position;
            this.count = count;
        }
    }
}
