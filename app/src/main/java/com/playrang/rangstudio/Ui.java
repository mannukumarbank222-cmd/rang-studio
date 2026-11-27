package com.playrang.rangstudio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building native views in code. */
final class Ui {
    static final int SAFFRON = Color.parseColor("#F26A1B");
    static final int SAFFRON_LIGHT = Color.parseColor("#FF9933");
    static final int SAFFRON_DARK = Color.parseColor("#E2551A");
    static final int BG = Color.parseColor("#FFF7F0");
    static final int TEXT = Color.parseColor("#2B2118");
    static final int MUTED = Color.parseColor("#8A7A6E");
    static final int BOT_BUBBLE = Color.WHITE;
    static final int CHIP_BG = Color.parseColor("#FFE9D6");

    private Ui() {}

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable bubble(int color, float r, boolean mine) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        // top-left, top-right, bottom-right, bottom-left
        float small = r / 4f;
        if (mine) g.setCornerRadii(new float[]{r, r, r, r, small, small, r, r});
        else g.setCornerRadii(new float[]{r, r, r, r, r, r, small, small});
        return g;
    }

    static GradientDrawable gradient(int a, int b) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        return g;
    }

    static GradientDrawable stroke(int fill, int strokeColor, int strokePx, float r) {
        GradientDrawable g = round(fill, r);
        g.setStroke(strokePx, strokeColor);
        return g;
    }

    /** Minimum touch target (Material / Android accessibility guideline). */
    static final int TOUCH_DP = 48;

    /** Content never stretches wider than this on tablets / landscape, so lines stay readable. */
    static final int MAX_CONTENT_DP = 640;

    /** Gives a view at least a 48dp x 48dp touch area. */
    static <T extends View> T touch(T v) {
        int m = dp(v.getContext(), TOUCH_DP);
        v.setMinimumHeight(m);
        v.setMinimumWidth(m);
        if (v instanceof TextView) {
            ((TextView) v).setMinHeight(m);
            ((TextView) v).setMinWidth(m);
        }
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    /** Wraps a background in a ripple so taps give visual feedback. */
    static Drawable ripple(Drawable content, int rippleColor) {
        Drawable mask = content != null ? content : new GradientDrawable();
        if (content == null) ((GradientDrawable) mask).setColor(Color.WHITE);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    /** Single line with "…" so a long title never pushes buttons off a small screen. */
    static void oneLine(TextView t) {
        t.setSingleLine(true);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** Max width for a chat bubble: ~80% of the window, capped on tablets / landscape. */
    static int bubbleMaxWidth(Context c) {
        int w = c.getResources().getDisplayMetrics().widthPixels;
        return Math.min((int) (w * 0.80f), dp(c, 520));
    }

    /** Short screen: landscape phone, split-screen, or a small phone with a big font. */
    static boolean shortScreen(Context c) {
        Configuration cf = c.getResources().getConfiguration();
        return cf.screenHeightDp > 0 && cf.screenHeightDp / Math.max(1f, cf.fontScale) < 480;
    }

    /** A vertical LinearLayout that never grows wider than {@link #MAX_CONTENT_DP} (centred by its parent). */
    static class MaxWidthColumn extends LinearLayout {
        private final int maxW;

        MaxWidthColumn(Context c) { this(c, MAX_CONTENT_DP); }

        MaxWidthColumn(Context c, int maxDp) {
            super(c);
            setOrientation(VERTICAL);
            maxW = dp(c, maxDp);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int w = MeasureSpec.getSize(widthSpec);
            if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && w > maxW) {
                widthSpec = MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.getMode(widthSpec));
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
