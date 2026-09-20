package com.example.shiningprobe;

import android.content.Context;
import android.content.SharedPreferences;

final class ProbeStore {
    private static final String PREFS = "probe_state";
    private static final String KEY_PACKAGE = "foreground_package";
    private static final String KEY_CLASS = "foreground_class";
    private static final String KEY_RESULT = "last_result";

    private ProbeStore() {}

    static void setForeground(Context context, CharSequence packageName, CharSequence className) {
        prefs(context).edit()
                .putString(KEY_PACKAGE, safe(packageName))
                .putString(KEY_CLASS, safe(className))
                .apply();
    }

    static void setResult(Context context, String result) {
        prefs(context).edit().putString(KEY_RESULT, result).apply();
    }

    static String foregroundPackage(Context context) {
        return prefs(context).getString(KEY_PACKAGE, "尚未读取");
    }

    static String foregroundClass(Context context) {
        return prefs(context).getString(KEY_CLASS, "");
    }

    static String lastResult(Context context) {
        return prefs(context).getString(KEY_RESULT, "尚未执行测试");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String safe(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
