package com.playrang.rangstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** The big breathing voice orb (like ChatGPT's voice mode), in Mitra's saffron colours. */
final class OrbView extends View {
    static final int IDLE = 0, LISTEN = 1, SPEAK = 2, MUTED = 3, CONNECT = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int mode = CONNECT;
    private float target, level;
    private final long t0 = System.currentTimeMillis();

    OrbView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setMode(int m) { mode = m; invalidate(); }
    void setLevel(float l) { target = Math.max(0f, Math.min(1f, l)); }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float t = (System.currentTimeMillis() - t0) / 1000f;
        level += (target - level) * 0.25f;
        target *= 0.92f;
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float base = Math.min(w, h) * 0.30f;
        float breathe = (float) Math.sin(t * (mode == CONNECT ? 3.2 : 1.6)) * 0.03f;
        float r = base * (1f + breathe + level * (mode == SPEAK ? 0.32f : 0.22f));

        int c1, c2, c3;
        switch (mode) {
            case MUTED: c1 = Color.parseColor("#6E625A"); c2 = Color.parseColor("#4A403A"); c3 = Color.parseColor("#2B2420"); break;
            case CONNECT: c1 = Color.parseColor("#FFD2A8"); c2 = Color.parseColor("#F2A066"); c3 = Color.parseColor("#B8642E"); break;
            case SPEAK: c1 = Color.parseColor("#FFF1C9"); c2 = Color.parseColor("#FF9933"); c3 = Color.parseColor("#D9381E"); break;
            default: c1 = Color.parseColor("#FFE4C2"); c2 = Color.parseColor("#F26A1B"); c3 = Color.parseColor("#B8361A"); break;
        }
        // soft glow rings
        for (int i = 3; i >= 1; i--) {
            float rr = r * (1f + 0.12f * i + level * 0.10f * i + (float) Math.sin(t * 1.3 + i) * 0.015f);
            ring.setStrokeWidth(r * 0.05f);
            ring.setColor(c2);
            ring.setAlpha(mode == MUTED ? 14 : (int) (26 + 40 * level) / i);
            c.drawCircle(cx, cy, rr, ring);
        }
        // the orb, its highlight drifting slowly
        float hx = cx + (float) Math.cos(t * 0.7) * r * 0.25f, hy = cy - r * 0.35f + (float) Math.sin(t * 0.9) * r * 0.12f;
        paint.setShader(new RadialGradient(hx, hy, r * 1.5f, new int[]{c1, c2, c3}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        // slight wobble so it feels alive
        float wob = (mode == SPEAK || mode == LISTEN) ? level * 0.06f : 0f;
        c.save();
        c.scale(1f + wob * (float) Math.sin(t * 7), 1f + wob * (float) Math.cos(t * 6), cx, cy);
        c.drawCircle(cx, cy, r, paint);
        c.restore();
        postInvalidateOnAnimation();
    }
}
