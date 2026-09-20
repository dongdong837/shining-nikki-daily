package com.example.shiningprobe;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GuideCatalog {
    private static final Pattern TASK_PATTERN = Pattern.compile("^###\\s+(\\d+)\\.\\s+(.+)$");
    private static final Pattern STEP_PATTERN = Pattern.compile("^\\d+\\.\\s+(.+)$");
    private static final Pattern IMAGE_PATTERN = Pattern.compile(".*<img\\s+src=\"\\./闪暖日活流程图片/([^\"]+)\".*");
    private static volatile GuideCatalog cached;

    final List<GuideTask> tasks;
    final String error;

    GuideCatalog(List<GuideTask> tasks, String error) {
        this.tasks = Collections.unmodifiableList(tasks);
        this.error = error;
    }

    static GuideCatalog load(Context context) {
        GuideCatalog local = cached;
        if (local != null) return local;
        synchronized (GuideCatalog.class) {
            if (cached == null) cached = parse(context.getApplicationContext());
            return cached;
        }
    }

    private static GuideCatalog parse(Context context) {
        List<GuideTask> tasks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("guide/flow.md"), StandardCharsets.UTF_8))) {
            boolean inDailySteps = false;
            GuideTask currentTask = null;
            GuideStep lastStep = null;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.equals("## 三、日活步骤")) {
                    inDailySteps = true;
                    continue;
                }
                if (inDailySteps && trimmed.startsWith("## 四、")) break;
                if (!inDailySteps) continue;

                Matcher taskMatcher = TASK_PATTERN.matcher(trimmed);
                if (taskMatcher.matches()) {
                    int number = Integer.parseInt(taskMatcher.group(1));
                    String rawTitle = clean(taskMatcher.group(2));
                    String title = rawTitle.replaceFirst("（.*）$", "");
                    String timing = rawTitle.equals(title) ? "" : rawTitle.substring(title.length());
                    currentTask = new GuideTask(number, title, timing);
                    tasks.add(currentTask);
                    lastStep = null;
                    continue;
                }
                if (currentTask == null) continue;

                Matcher stepMatcher = STEP_PATTERN.matcher(trimmed);
                if (stepMatcher.matches()) {
                    lastStep = new GuideStep(currentTask.steps.size() + 1, clean(stepMatcher.group(1)));
                    lastStep.id = "builtin-t" + currentTask.number + "-s" + lastStep.index;
                    currentTask.steps.add(lastStep);
                    continue;
                }

                Matcher imageMatcher = IMAGE_PATTERN.matcher(trimmed);
                if (imageMatcher.matches() && lastStep != null && lastStep.imageAsset.isEmpty()) {
                    lastStep.imageAsset = "guide/images/" + imageMatcher.group(1);
                    continue;
                }

                if (trimmed.startsWith("完成判断：")
                        || trimmed.startsWith("安全规则：")
                        || trimmed.startsWith("异常处理：")
                        || trimmed.startsWith("App 识别基准：")) {
                    currentTask.notes.add(clean(trimmed));
                }
            }
            return new GuideCatalog(tasks, tasks.isEmpty() ? "流程文件中没有解析到日活步骤" : "");
        } catch (IOException | RuntimeException error) {
            return new GuideCatalog(tasks, "读取流程失败：" + error.getMessage());
        }
    }

    GuideTask findTask(int number) {
        for (GuideTask task : tasks) if (task.number == number) return task;
        return null;
    }

    int totalStepCount() {
        int total = 0;
        for (GuideTask task : tasks) total += task.steps.size();
        return total;
    }

    private static String clean(String value) {
        return value.replace("**", "").replace("`", "").trim();
    }

    static final class GuideTask {
        final int number;
        final String title;
        final String timing;
        final List<GuideStep> steps = new ArrayList<>();
        final List<String> notes = new ArrayList<>();

        GuideTask(int number, String title, String timing) {
            this.number = number;
            this.title = title;
            this.timing = timing;
        }
    }

    static final class GuideStep {
        final int index;
        final String text;
        String id = "";
        String imageAsset = "";
        int clickX = -1;
        int clickY = -1;
        int imageWidth;
        int imageHeight;

        GuideStep(int index, String text) {
            this.index = index;
            this.text = text;
        }

        boolean hasClickPoint() {
            return clickX >= 0 && clickY >= 0 && imageWidth > 0 && imageHeight > 0;
        }
    }
}
