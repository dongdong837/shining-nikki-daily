package com.example.shiningprobe;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class GuideSessionStore {
    private static final String PREFS = "guide_session";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_PAUSED = "paused";
    private static final String KEY_SELECTED = "selected";
    private static final String KEY_TASK_POSITION = "task_position";
    private static final String KEY_STEP_POSITION = "step_position";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_STARTED_AT = "started_at";

    private GuideSessionStore() {}

    static void start(Context context, List<Integer> selectedTaskNumbers) {
        StringBuilder csv = new StringBuilder();
        for (Integer number : selectedTaskNumbers) {
            if (csv.length() > 0) csv.append(',');
            csv.append(number);
        }
        String sessionId = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        prefs(context).edit()
                .putBoolean(KEY_ACTIVE, true)
                .putBoolean(KEY_PAUSED, false)
                .putString(KEY_SELECTED, csv.toString())
                .putInt(KEY_TASK_POSITION, 0)
                .putInt(KEY_STEP_POSITION, 0)
                .putString(KEY_SESSION_ID, sessionId)
                .putLong(KEY_STARTED_AT, System.currentTimeMillis())
                .apply();
    }

    static boolean isActive(Context context) {
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    static boolean isPaused(Context context) {
        return prefs(context).getBoolean(KEY_PAUSED, false);
    }

    static void setPaused(Context context, boolean paused) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    static String sessionId(Context context) {
        return prefs(context).getString(KEY_SESSION_ID, "未开始");
    }

    static long startedAt(Context context) {
        return prefs(context).getLong(KEY_STARTED_AT, 0L);
    }

    static List<Integer> selectedTaskNumbers(Context context) {
        List<Integer> result = new ArrayList<>();
        String csv = prefs(context).getString(KEY_SELECTED, "");
        if (csv == null || csv.isEmpty()) return result;
        for (String part : csv.split(",")) {
            try {
                result.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    static Position current(Context context, GuideCatalog catalog) {
        if (!isActive(context)) return null;
        List<Integer> selected = selectedTaskNumbers(context);
        int taskPosition = prefs(context).getInt(KEY_TASK_POSITION, 0);
        int stepPosition = prefs(context).getInt(KEY_STEP_POSITION, 0);
        if (taskPosition < 0 || taskPosition >= selected.size()) return null;
        GuideCatalog.GuideTask task = catalog.findTask(selected.get(taskPosition));
        if (task == null || task.steps.isEmpty()) return null;
        stepPosition = Math.max(0, Math.min(stepPosition, task.steps.size() - 1));
        return new Position(task, task.steps.get(stepPosition), taskPosition, selected.size());
    }

    static boolean next(Context context, GuideCatalog catalog) {
        Position current = current(context, catalog);
        if (current == null) return true;
        SharedPreferences preferences = prefs(context);
        int taskPosition = current.selectedTaskPosition;
        int stepPosition = preferences.getInt(KEY_STEP_POSITION, 0);
        if (stepPosition + 1 < current.task.steps.size()) {
            preferences.edit().putInt(KEY_STEP_POSITION, stepPosition + 1).apply();
            return false;
        }
        if (taskPosition + 1 < current.selectedTaskCount) {
            preferences.edit()
                    .putInt(KEY_TASK_POSITION, taskPosition + 1)
                    .putInt(KEY_STEP_POSITION, 0)
                    .apply();
            return false;
        }
        finish(context);
        return true;
    }

    static void previous(Context context, GuideCatalog catalog) {
        Position current = current(context, catalog);
        if (current == null) return;
        SharedPreferences preferences = prefs(context);
        int stepPosition = preferences.getInt(KEY_STEP_POSITION, 0);
        if (stepPosition > 0) {
            preferences.edit().putInt(KEY_STEP_POSITION, stepPosition - 1).apply();
            return;
        }
        if (current.selectedTaskPosition <= 0) return;
        List<Integer> selected = selectedTaskNumbers(context);
        GuideCatalog.GuideTask previousTask = catalog.findTask(selected.get(current.selectedTaskPosition - 1));
        int previousStep = previousTask == null || previousTask.steps.isEmpty()
                ? 0 : previousTask.steps.size() - 1;
        preferences.edit()
                .putInt(KEY_TASK_POSITION, current.selectedTaskPosition - 1)
                .putInt(KEY_STEP_POSITION, previousStep)
                .apply();
    }

    static boolean moveToStep(Context context, GuideCatalog catalog,
                              int taskNumber, String stepId) {
        List<Integer> selected = selectedTaskNumbers(context);
        int taskPosition = selected.indexOf(taskNumber);
        GuideCatalog.GuideTask task = catalog.findTask(taskNumber);
        if (taskPosition < 0 || task == null) return false;
        for (int stepPosition = 0; stepPosition < task.steps.size(); stepPosition++) {
            if (!task.steps.get(stepPosition).id.equals(stepId)) continue;
            prefs(context).edit()
                    .putInt(KEY_TASK_POSITION, taskPosition)
                    .putInt(KEY_STEP_POSITION, stepPosition)
                    .apply();
            return true;
        }
        return false;
    }

    static void finish(Context context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_PAUSED, false).apply();
    }

    static void onTaskInserted(Context context, int newTaskNumber) {
        List<Integer> selected = selectedTaskNumbers(context);
        if (selected.isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        int currentPosition = preferences.getInt(KEY_TASK_POSITION, 0);
        int currentOldNumber = currentPosition >= 0 && currentPosition < selected.size()
                ? selected.get(currentPosition) : -1;
        List<Integer> updated = new ArrayList<>();
        for (Integer number : selected) updated.add(number >= newTaskNumber ? number + 1 : number);
        updated.add(newTaskNumber);
        Collections.sort(updated);
        int mappedCurrent = currentOldNumber < 0 ? -1
                : (currentOldNumber >= newTaskNumber ? currentOldNumber + 1 : currentOldNumber);
        int updatedPosition = mappedCurrent < 0 ? currentPosition : updated.indexOf(mappedCurrent);
        preferences.edit()
                .putString(KEY_SELECTED, toCsv(updated))
                .putInt(KEY_TASK_POSITION, Math.max(0, updatedPosition))
                .apply();
    }

    static void onStepInserted(Context context, int taskNumber, int zeroBasedInsertionIndex) {
        if (!isActive(context)) return;
        List<Integer> selected = selectedTaskNumbers(context);
        SharedPreferences preferences = prefs(context);
        int taskPosition = preferences.getInt(KEY_TASK_POSITION, 0);
        if (taskPosition < 0 || taskPosition >= selected.size()
                || selected.get(taskPosition) != taskNumber) return;
        int stepPosition = preferences.getInt(KEY_STEP_POSITION, 0);
        if (zeroBasedInsertionIndex <= stepPosition) {
            preferences.edit().putInt(KEY_STEP_POSITION, stepPosition + 1).apply();
        }
    }

    static void onStepDeleted(Context context, int taskNumber,
                              int zeroBasedDeletionIndex, int remainingCount) {
        if (!isActive(context)) return;
        List<Integer> selected = selectedTaskNumbers(context);
        SharedPreferences preferences = prefs(context);
        int taskPosition = preferences.getInt(KEY_TASK_POSITION, 0);
        if (taskPosition < 0 || taskPosition >= selected.size()
                || selected.get(taskPosition) != taskNumber) return;
        int stepPosition = preferences.getInt(KEY_STEP_POSITION, 0);
        if (zeroBasedDeletionIndex < stepPosition) stepPosition--;
        else if (zeroBasedDeletionIndex == stepPosition) {
            stepPosition = Math.min(stepPosition, Math.max(0, remainingCount - 1));
        }
        preferences.edit().putInt(KEY_STEP_POSITION, Math.max(0, stepPosition)).apply();
    }

    static void onTaskDeleted(Context context, int deletedTaskNumber) {
        List<Integer> selected = selectedTaskNumbers(context);
        if (selected.isEmpty()) return;
        SharedPreferences preferences = prefs(context);
        int currentPosition = preferences.getInt(KEY_TASK_POSITION, 0);
        int currentOldNumber = currentPosition >= 0 && currentPosition < selected.size()
                ? selected.get(currentPosition) : -1;
        List<Integer> updated = new ArrayList<>();
        for (Integer number : selected) {
            if (number == deletedTaskNumber) continue;
            updated.add(number > deletedTaskNumber ? number - 1 : number);
        }
        if (updated.isEmpty()) {
            finish(context);
            preferences.edit().putString(KEY_SELECTED, "").apply();
            return;
        }
        int updatedPosition;
        if (currentOldNumber == deletedTaskNumber) {
            updatedPosition = Math.min(currentPosition, updated.size() - 1);
        } else {
            int mappedCurrent = currentOldNumber > deletedTaskNumber
                    ? currentOldNumber - 1 : currentOldNumber;
            updatedPosition = updated.indexOf(mappedCurrent);
            if (updatedPosition < 0) updatedPosition = Math.min(currentPosition, updated.size() - 1);
        }
        preferences.edit()
                .putString(KEY_SELECTED, toCsv(updated))
                .putInt(KEY_TASK_POSITION, updatedPosition)
                .putInt(KEY_STEP_POSITION, 0)
                .apply();
    }

    static String statusText(Context context, GuideCatalog catalog) {
        Position position = current(context, catalog);
        if (position == null) return "未开始校准流程";
        return "任务 " + (position.selectedTaskPosition + 1) + "/" + position.selectedTaskCount
                + " · " + position.task.title + " · 步骤 " + position.step.index + "/"
                + position.task.steps.size() + (isPaused(context) ? "（已暂停）" : "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String toCsv(List<Integer> numbers) {
        StringBuilder csv = new StringBuilder();
        for (Integer number : numbers) {
            if (csv.length() > 0) csv.append(',');
            csv.append(number);
        }
        return csv.toString();
    }

    static final class Position {
        final GuideCatalog.GuideTask task;
        final GuideCatalog.GuideStep step;
        final int selectedTaskPosition;
        final int selectedTaskCount;

        Position(GuideCatalog.GuideTask task, GuideCatalog.GuideStep step,
                 int selectedTaskPosition, int selectedTaskCount) {
            this.task = task;
            this.step = step;
            this.selectedTaskPosition = selectedTaskPosition;
            this.selectedTaskCount = selectedTaskCount;
        }
    }
}
