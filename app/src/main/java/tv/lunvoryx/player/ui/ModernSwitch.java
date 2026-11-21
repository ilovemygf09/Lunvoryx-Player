package tv.lunvoryx.player.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import static tv.lunvoryx.player.ui.TvStyle.dp;

public final class ModernSwitch extends View {
    private boolean checked;
    private float position; // 0.0 (off) to 1.0 (on)
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    public ModernSwitch(Context context) {
        super(context);
        thumbPaint.setColor(0xFFFFFFFF);
        thumbPaint.setStyle(Paint.Style.FILL);
    }

    public void setChecked(boolean checked, boolean animate) {
        this.checked = checked;
        float target = checked ? 1f : 0f;
        if (!animate) {
            position = target;
            invalidate();
        } else {
            ValueAnimator anim = ValueAnimator.ofFloat(position, target);
            anim.setDuration(160);
            anim.addUpdateListener(a -> {
                position = (Float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        }
    }

    public boolean isChecked() {
        return checked;
    }

    private final android.graphics.Path trackPath = new android.graphics.Path();

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;

        // Interpolate track color: off is #4E5262, on is vibrant blue #257CFF
        int offColor = 0xFF4E5262;
        int onColor = 0xFF257CFF;
        int currentTrack = blendColors(offColor, onColor, position);

        trackPaint.setColor(currentTrack);
        trackPaint.setStyle(Paint.Style.FILL);

        // Continuous corner smoothing (squircle) for the switch track
        SquirclePath.build(trackPath, 0, 0, w, h, r);
        canvas.drawPath(trackPath, trackPaint);

        // Thumb with smooth continuous squircle
        float pad = dp(getContext(), 2.5f);
        float thumbR = r - pad;
        float thumbX = (pad + thumbR) + position * (w - 2 * pad - 2 * thumbR);
        float thumbY = h / 2f;

        canvas.drawCircle(thumbX, thumbY, thumbR, thumbPaint);
    }

    private static int blendColors(int c1, int c2, float ratio) {
        float ir = 1f - ratio;
        int a = (int) (((c1 >> 24) & 0xff) * ir + ((c2 >> 24) & 0xff) * ratio);
        int r = (int) (((c1 >> 16) & 0xff) * ir + ((c2 >> 16) & 0xff) * ratio);
        int g = (int) (((c1 >> 8) & 0xff) * ir + ((c2 >> 8) & 0xff) * ratio);
        int b = (int) ((c1 & 0xff) * ir + (c2 & 0xff) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
