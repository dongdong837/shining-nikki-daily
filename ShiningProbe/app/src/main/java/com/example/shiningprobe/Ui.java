package com.example.shiningprobe;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int PURPLE = Color.rgb(103, 80, 164);
    static final int DARK = Color.rgb(42, 35, 54);
    static final int MUTED = Color.rgb(90, 83, 102);
    static final int BACKGROUND = Color.rgb(248, 247, 252);
    static final int ERROR = Color.rgb(176, 48, 64);

    private Ui() {}

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static TextView text(Context context, String value, float size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    static TextView section(Context context, String value) {
        TextView view = text(context, value, 18, Color.rgb(62, 49, 78));
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setPadding(0, dp(context, 12), 0, dp(context, 8));
        return view;
    }

    static Button button(Context context, String label, int color, View.OnClickListener listener) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(roundRect(context, color, 14, Color.TRANSPARENT));
        button.setOnClickListener(listener);
        button.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
        button.setMinHeight(dp(context, 50));
        button.setLayoutParams(matchWrapBottom(context, 10));
        return button;
    }

    static GradientDrawable roundRect(Context context, int fill, int radiusDp, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static LinearLayout.LayoutParams matchWrapBottom(Context context, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(context, bottomDp);
        return params;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
