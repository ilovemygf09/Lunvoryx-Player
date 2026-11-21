package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Professional, anti-slop frosted glassmorphism drawable with
 * G2 continuous-curvature squircle corners (corner smoothing).
 * Features:
 * - Multi-stop translucent glass gradient base with layered depth
 * - Top-lit specular rim light (bevel reflection catching overhead key light)
 * - Dynamic D-pad TV focus state with electric neon cyan-blue continuous glow border
 * - StateList integration for automatic focus updates
 */
public class SquircleGlassDrawable extends Drawable {
    public enum GlassMode {
        CARD,       // Standard content card: balanced translucent frosted glass
        INPUT,      // Text field / list item: deep dark frosted glass
        SURFACE,    // Sidebar / modal / background panel: dark glass with subtle color depth
        SOLID,      // Solid color (e.g. active white pill or accent button)
        ACCENT_GLOW // Highlighted card with permanent glowing border (e.g. Xtream Codes card)
    }

    private final float density;
    private final float radiusPx;
    private final float smoothing;
    private final GlassMode mode;

    private int solidColor = 0;
    private int customStrokeColor = 0;
    private float customStrokeWidthPx = -1;

    private boolean tl = true, tr = true, br = true, bl = true;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerBevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path fillPath = new Path();
    private final Path strokePath = new Path();
    private final android.graphics.RectF innerBevelRect = new android.graphics.RectF();

    private boolean isFocused = false;
    private boolean isPressed = false;
    private boolean isSelected = false;

    public SquircleGlassDrawable(Context context, float radiusDp, GlassMode mode) {
        this.density = context.getResources().getDisplayMetrics().density;
        this.radiusPx = radiusDp * density;
        this.smoothing = SquirclePath.DEFAULT_SMOOTHING;
        this.mode = mode;

        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        innerBevelPaint.setStyle(Paint.Style.FILL);
    }

    public SquircleGlassDrawable(Context context, float radiusDp, int solidColor) {
        this(context, radiusDp, GlassMode.SOLID);
        this.solidColor = solidColor;
    }

    public SquircleGlassDrawable setCorners(boolean tl, boolean tr, boolean br, boolean bl) {
        this.tl = tl;
        this.tr = tr;
        this.br = br;
        this.bl = bl;
        updatePaths(getBounds());
        invalidateSelf();
        return this;
    }

    public SquircleGlassDrawable setSolidColor(int color) {
        this.solidColor = color;
        invalidateSelf();
        return this;
    }

    public SquircleGlassDrawable setCustomStroke(int color, float widthDp) {
        this.customStrokeColor = color;
        this.customStrokeWidthPx = widthDp * density;
        updatePaths(getBounds());
        invalidateSelf();
        return this;
    }

    public SquircleGlassDrawable setSelected(boolean selected) {
        if (this.isSelected != selected) {
            this.isSelected = selected;
            invalidateSelf();
        }
        return this;
    }

    public SquircleGlassDrawable setFocused(boolean focused) {
        if (this.isFocused != focused) {
            this.isFocused = focused;
            updateShaders(getBounds());
            invalidateSelf();
        }
        return this;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean focused = false;
        boolean pressed = false;
        boolean selected = false;
        if (state != null) {
            for (int s : state) {
                if (s == android.R.attr.state_focused) focused = true;
                if (s == android.R.attr.state_pressed) pressed = true;
                if (s == android.R.attr.state_selected) selected = true;
            }
        }
        boolean changed = (focused != isFocused) || (pressed != isPressed) || (selected != isSelected);
        if (changed) {
            this.isFocused = focused;
            this.isPressed = pressed;
            this.isSelected = selected;
            updateShaders(getBounds());
            invalidateSelf();
        }
        return changed;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        updatePaths(bounds);
        updateShaders(bounds);
    }

    private void updatePaths(Rect bounds) {
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;

        float l = bounds.left;
        float t = bounds.top;
        float r = bounds.right;
        float b = bounds.bottom;

        // Build outer fill path
        SquirclePath.build(fillPath, l, t, r, b, radiusPx, smoothing, tl, tr, br, bl);

        // Build inset stroke path to prevent edge clipping
        float strokeW = getStrokeWidth();
        float inset = strokeW / 2.0f;
        float strokeR = Math.max(0, radiusPx - inset);
        SquirclePath.build(strokePath, l + inset, t + inset, r - inset, b - inset,
                strokeR, smoothing, tl, tr, br, bl);
    }

    private float getStrokeWidth() {
        if (customStrokeWidthPx > 0) return customStrokeWidthPx;
        if (isFocused || isPressed) return 1.5f * density; // Crisp Apple TV focus stroke
        if (mode == GlassMode.INPUT) return 1.35f * density; // Defined, crisp border for inputs
        if (mode == GlassMode.ACCENT_GLOW) return 1.25f * density;
        if (mode == GlassMode.SOLID) return 0f;
        return 1.1f * density; // Defined, crisp squircle rim stroke
    }

    private void updateShaders(Rect bounds) {
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return;

        float w = bounds.width();
        float h = bounds.height();
        float strokeW = getStrokeWidth();
        strokePaint.setStrokeWidth(strokeW);

        // 1. FILL SHADER (Apple Obsidian Translucent Glass)
        if (mode == GlassMode.SOLID) {
            fillPaint.setShader(null);
            fillPaint.setColor(solidColor);
        } else if (isFocused || isPressed) {
            // Enhanced active glass fill when focused (Apple obsidian lift)
            LinearGradient fillGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    new int[]{0xF020263C, 0xF5181D2E, 0xF8111422},
                    new float[]{0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillGrad);
        } else {
            // Normal Frosted Glass gradient (Apple obsidian crystalline palettes)
            int[] fillColors;
            float[] fillStops = new float[]{0f, 0.45f, 1f};

            switch (mode) {
                case INPUT:
                    // Dark obsidian frosted glass
                    fillColors = new int[]{0xEE0F111B, 0xF50B0D15, 0xF8080910};
                    break;
                case SURFACE:
                    // Sidebar and major background container glass
                    fillColors = new int[]{0xF0131624, 0xF510121C, 0xF80D0F18};
                    break;
                case ACCENT_GLOW:
                    // Featured card glass
                    fillColors = new int[]{0xDC181D2E, 0xE8121524, 0xF00D0F1A};
                    break;
                case CARD:
                default:
                    // Apple crystalline translucent glass
                    fillColors = new int[]{0xDC181D2E, 0xE8121524, 0xF00D0F1A};
                    break;
            }

            LinearGradient fillGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    fillColors, fillStops,
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillGrad);
        }

        // 2. INNER BEVEL WASH (Apple Glass optical chamfer depth)
        if (mode != GlassMode.SOLID && h > 20 * density) {
            float bevelH = Math.min(20f * density, h * 0.3f);
            innerBevelRect.set(bounds.left, bounds.top, bounds.right, bounds.top + bevelH);
            LinearGradient bevelGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.top + bevelH,
                    new int[]{0x14FFFFFF, 0x04FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.4f, 1f},
                    Shader.TileMode.CLAMP
            );
            innerBevelPaint.setShader(bevelGrad);
        } else {
            innerBevelPaint.setShader(null);
        }

        // 3. STROKE SHADER (Apple Specular Rim Light or Focus Bloom)
        if (strokeW <= 0.01f) {
            strokePaint.setShader(null);
            strokePaint.setColor(0);
            strokePaint.clearShadowLayer();
        } else if (customStrokeColor != 0 && !isFocused) {
            strokePaint.setShader(null);
            strokePaint.setColor(customStrokeColor);
            strokePaint.clearShadowLayer();
        } else if (isFocused || isPressed) {
            // Apple TV luminous frosted pearl-white to celestial silver focus rim
            LinearGradient focusGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    new int[]{0xFFFFFFFF, 0xF5FFFFFF, 0xDCE6F0, 0xC4D2E0},
                    new float[]{0f, 0.22f, 0.78f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(focusGrad);
            strokePaint.setShadowLayer(4.0f * density, 0, 1.0f * density, 0x44FFFFFF);
        } else if (mode == GlassMode.INPUT) {
            // Crisp, defined silver-to-steel specular rim contour (exact match to reference mockup):
            // Top: 0x9CE8F0FA (simulates #8D98B8 / #A2A7BA on dark glass)
            // Mid: 0x72ADC2DE
            // Bottom: 0x587890B4 (simulates #637293)
            LinearGradient inputGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    new int[]{0x9CE8F0FA, 0x72ADC2DE, 0x48647CA0, 0x587890B4},
                    new float[]{0f, 0.25f, 0.78f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(inputGrad);
            strokePaint.clearShadowLayer();
        } else if (mode == GlassMode.ACCENT_GLOW) {
            // Luminous sapphire rim for featured cards
            LinearGradient glowGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    new int[]{0x884898FF, 0x553080F0, 0x221D5ED8, 0x382870E0},
                    new float[]{0f, 0.32f, 0.80f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(glowGrad);
            strokePaint.clearShadowLayer();
        } else if (mode == GlassMode.SURFACE) {
            // Sidebar and major background container glass
            LinearGradient surfaceGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    new int[]{0x4CD4E0F0, 0x3094A8C4, 0x1A506884, 0x24627A96},
                    new float[]{0f, 0.28f, 0.80f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(surfaceGrad);
            strokePaint.clearShadowLayer();
        } else {
            // CARD and default: clean, crisp, visible squircle glass border
            // Matches measured reference pill borders (#2F354B top, #242C41 sides)
            LinearGradient cardGrad = new LinearGradient(
                    0, bounds.top, 0, bounds.bottom,
                    new int[]{0x58DCE6F5, 0x3A9CB0CA, 0x20566E8A, 0x2C6C829E},
                    new float[]{0f, 0.26f, 0.80f, 1f},
                    Shader.TileMode.CLAMP
            );
            strokePaint.setShader(cardGrad);
            strokePaint.clearShadowLayer();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) return;

        // 1. Draw squircle frosted glass fill
        canvas.drawPath(fillPath, fillPaint);

        // 2. Inner specular bevel wash (Apple Glass optical depth)
        if (mode != GlassMode.SOLID && !isFocused && innerBevelPaint.getShader() != null) {
            canvas.save();
            canvas.clipPath(fillPath);
            canvas.drawRect(innerBevelRect, innerBevelPaint);
            canvas.restore();
        }

        // 3. Draw squircle specular rim light / focus border
        if (getStrokeWidth() > 0.01f) {
            canvas.drawPath(strokePath, strokePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
