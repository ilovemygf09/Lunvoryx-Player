package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

final class IntuitivePanel {
    private IntuitivePanel() {}

    static GradientDrawable button(Context context, boolean focused) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF00A8FF, 0xFF056BFF, 0xFF0042C2});
        fill.setCornerRadius(TvStyle.dp(context, 24));
        fill.setStroke(TvStyle.dp(context, focused ? 2 : 1), focused ? 0xFFFFFFFF : 0xFF248DF9);
        return fill;
    }

    static void outline(android.view.View view, int radius) {
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(android.view.View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), TvStyle.dp(v.getContext(), radius));
            }
        });
        view.setClipToOutline(true);
    }

    static void focusRing(android.view.View view, boolean focused) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0);
        ring.setCornerRadius(TvStyle.dp(view.getContext(), 14));
        ring.setStroke(TvStyle.dp(view.getContext(), focused ? 2 : 1), focused ? 0xFF36BCFF : 0xFF29496D);
        view.setForeground(ring);
    }

    static GradientDrawable create(Context context) {
        GradientDrawable panel = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF142E51, 0xFF091D36, 0xFF041327});
        panel.setCornerRadius(TvStyle.dp(context, 16));
        panel.setStroke(TvStyle.dp(context, 1), 0xFF244974);
        return panel;
    }
}
