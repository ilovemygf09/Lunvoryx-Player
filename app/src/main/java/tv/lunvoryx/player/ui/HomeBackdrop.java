package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import tv.lunvoryx.player.R;

public final class HomeBackdrop extends View {
    private final Bitmap image;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private LinearGradient sideShade, bottomShade;
    private float artHeight;
    private boolean home = true;
    private boolean intuitive = false;

    public HomeBackdrop(Context context) {
        super(context);
        image = Artwork.get(context, R.drawable.hero_palma, 1920);
    }

    public void setHome(boolean value) {
        home = value;
        invalidate();
    }

    public void setIntuitive(boolean value) {
        this.intuitive = value;
        updateBounds(getWidth(), getHeight());
        invalidate();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateBounds(width, height);
    }

    private void updateBounds(int width, int height) {
        if (width <= 0 || height <= 0 || image == null) return;
        artHeight = height * .68f;
        float left = intuitive ? 0 : width * .15f;
        float scale = Math.max((width - left) / image.getWidth(), artHeight / image.getHeight());
        destination.set(left, 0, width, image.getHeight() * scale);
        if (intuitive) {
            sideShade = new LinearGradient(0, 0, width, 0,
                    new int[]{0xCC060A14, 0x55060A14, 0x11060A14, 0x66060A14},
                    new float[]{0, .3f, .7f, 1f}, Shader.TileMode.CLAMP);
            bottomShade = new LinearGradient(0, artHeight * .35f, 0, artHeight,
                    new int[]{0x00060A14, 0xEE060A14}, null, Shader.TileMode.CLAMP);
        } else {
            sideShade = new LinearGradient(0, 0, width, 0,
                    new int[]{TvStyle.BG, 0xF20B0D18, 0x6B0B0D18, 0x000B0D18},
                    new float[]{0, .19f, .48f, .85f}, Shader.TileMode.CLAMP);
            bottomShade = new LinearGradient(0, artHeight * .48f, 0, artHeight,
                    new int[]{0x000B0D18, TvStyle.BG}, null, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas c) {
        c.drawColor(intuitive ? 0xFF060A14 : TvStyle.BG);
        float w = getWidth();
        paint.setShader(null);
        paint.setAlpha(home ? 255 : 60);
        c.save();
        c.clipRect(0, 0, w, artHeight);
        if (image != null) c.drawBitmap(image, null, destination, paint);
        paint.setAlpha(255);
        if (sideShade != null) {
            paint.setShader(sideShade);
            c.drawRect(0, 0, w, artHeight, paint);
        }
        if (bottomShade != null) {
            paint.setShader(bottomShade);
            c.drawRect(0, artHeight * (intuitive ? .35f : .48f), w, artHeight, paint);
            paint.setShader(null);
        }
        c.restore();
    }
}
