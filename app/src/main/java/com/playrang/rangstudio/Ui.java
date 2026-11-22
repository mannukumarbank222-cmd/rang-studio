package com.playrang.rangstudio;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

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
}
