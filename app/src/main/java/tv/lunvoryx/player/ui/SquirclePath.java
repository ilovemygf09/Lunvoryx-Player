package tv.lunvoryx.player.ui;

import android.graphics.Path;
import android.graphics.RectF;

/**
 * Generates continuous-curvature squircle paths with G2 corner smoothing
 * (Apple / Figma continuous corners style).
 * Transitions curvature smoothly from straight edges into corners using dual
 * cubic Bézier curves per corner, eliminating the abrupt curvature discontinuity
 * of standard circular arcs.
 */
public final class SquirclePath {
    public static final float DEFAULT_SMOOTHING = 0.65f; // Figma/iOS continuous curvature factor

    private SquirclePath() {}

    public static void build(Path path, float left, float top, float right, float bottom, float radius) {
        build(path, left, top, right, bottom, radius, DEFAULT_SMOOTHING, true, true, true, true);
    }

    public static void build(Path path, RectF bounds, float radius) {
        build(path, bounds.left, bounds.top, bounds.right, bounds.bottom, radius, DEFAULT_SMOOTHING, true, true, true, true);
    }

    public static void build(Path path, float left, float top, float right, float bottom,
                             float radius, float smoothing,
                             boolean tl, boolean tr, boolean br, boolean bl) {
        path.reset();
        float w = right - left;
        float h = bottom - top;
        if (w <= 0 || h <= 0) return;

        if (radius <= 0.5f || (!tl && !tr && !br && !bl)) {
            path.addRect(left, top, right, bottom, Path.Direction.CW);
            return;
        }

        float maxP = Math.min(w, h) / 2.0f;
        float p = Math.min((1.0f + smoothing) * radius, maxP);
        float rEff = p / (1.0f + smoothing);
        float d = 0.285f * rEff;
        float c1 = 0.52f * (p - d);
        float c2 = 0.38f * d;

        // Start on the top edge after top-left corner
        path.moveTo(left + (tl ? p : 0), top);

        // 1. Top edge -> Top-Right corner
        path.lineTo(right - (tr ? p : 0), top);
        if (tr) {
            path.cubicTo(right - p + c1, top,
                         right - d - c2, top + d - c2,
                         right - d,      top + d);
            path.cubicTo(right - d + c2, top + d + c2,
                         right,          top + p - c1,
                         right,          top + p);
        } else {
            path.lineTo(right, top);
        }

        // 2. Right edge -> Bottom-Right corner
        path.lineTo(right, bottom - (br ? p : 0));
        if (br) {
            path.cubicTo(right,          bottom - p + c1,
                         right - d + c2, bottom - d - c2,
                         right - d,      bottom - d);
            path.cubicTo(right - d - c2, bottom - d + c2,
                         right - p + c1, bottom,
                         right - p,      bottom);
        } else {
            path.lineTo(right, bottom);
        }

        // 3. Bottom edge -> Bottom-Left corner
        path.lineTo(left + (bl ? p : 0), bottom);
        if (bl) {
            path.cubicTo(left + p - c1,  bottom,
                         left + d + c2,  bottom - d + c2,
                         left + d,       bottom - d);
            path.cubicTo(left + d - c2,  bottom - d - c2,
                         left,           bottom - p + c1,
                         left,           bottom - p);
        } else {
            path.lineTo(left, bottom);
        }

        // 4. Left edge -> Top-Left corner
        path.lineTo(left, top + (tl ? p : 0));
        if (tl) {
            path.cubicTo(left,          top + p - c1,
                         left + d - c2, top + d + c2,
                         left + d,      top + d);
            path.cubicTo(left + d + c2, top + d - c2,
                         left + p - c1, top,
                         left + p,      top);
        } else {
            path.lineTo(left, top);
        }

        path.close();
    }
}
