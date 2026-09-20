package com.example.shiningprobe;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

@SuppressLint("SetTextI18n")
public class ScreenshotViewerActivity extends Activity {
    static final String EXTRA_PATH = "screenshot_path";
    private Bitmap bitmap;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || path.isEmpty() || !new File(path).exists()) {
            Toast.makeText(this, "原始截图不存在或已被移动", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(buildContent(path));
    }

    private ScrollView buildContent(String path) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(20, 18, 23));

        LinearLayout root = Ui.column(this);
        root.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 24));
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = Ui.text(this, "清晰原图 · 可上下滚动查看", 18, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
        root.addView(title);

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        TextView info = Ui.text(this, bounds.outWidth + " × " + bounds.outHeight
                + " · 原始 PNG 未压缩缩小", 13, Color.LTGRAY);
        info.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 10));
        root.addView(info);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap != null) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription("清晰的现场原始截图");
            root.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            root.addView(Ui.text(this, "图片解码失败", 16, Color.WHITE));
        }

        root.addView(Ui.button(this, "返回校准记录", Color.rgb(100, 96, 105), v -> finish()));
        return scroll;
    }

    @Override protected void onDestroy() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        super.onDestroy();
    }
}
