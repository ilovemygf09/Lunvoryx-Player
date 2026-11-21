package tv.lunvoryx.player.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class TvStyle {
    public static final int BG=0xFF0B0D18, PANEL=0xD9202332, RAISED=0xFF353745,
        TEXT=0xFFF8F8FB, MUTED=0xFFB5B5C4, ACCENT=0xFFF0F0F5, INK=0xFF15151C,
        LINE=0xFF4A4B5A, ERROR=0xFFFFB4A9;
    private TvStyle() {}
    public static int dp(Context c,float v){ return Math.round(v*c.getResources().getDisplayMetrics().density); }
    public static GradientDrawable shape(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(c,radius)); return d;
    }

    // G2 Continuous-Curvature Squircle & Anti-Slop Frosted Glass builders:
    public static SquircleGlassDrawable squircleCard(Context c, float radiusDp) {
        return new SquircleGlassDrawable(c, radiusDp, SquircleGlassDrawable.GlassMode.CARD);
    }
    public static SquircleGlassDrawable squircleInput(Context c, float radiusDp) {
        return new SquircleGlassDrawable(c, radiusDp, SquircleGlassDrawable.GlassMode.INPUT);
    }
    public static SquircleGlassDrawable squircleSurface(Context c, float radiusDp) {
        return new SquircleGlassDrawable(c, radiusDp, SquircleGlassDrawable.GlassMode.SURFACE);
    }
    public static SquircleGlassDrawable squircleSolid(Context c, float radiusDp, int color) {
        return new SquircleGlassDrawable(c, radiusDp, color);
    }
    public static SquircleGlassDrawable squircleAccent(Context c, float radiusDp) {
        return new SquircleGlassDrawable(c, radiusDp, SquircleGlassDrawable.GlassMode.ACCENT_GLOW);
    }

    public static Drawable focusBackground(Context c, int base) {
        return squircleCard(c, 14);
    }

    public static TextView text(Context c,String label,int size,int color){
        TextView t=new TextView(c); t.setText(label); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setIncludeFontPadding(false); return t;
    }
    public static TextView button(Context c,String label,Runnable action){
        TextView b=text(c,label,15,TEXT); b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(c,18),dp(c,12),dp(c,18),dp(c,12)); b.setMinHeight(dp(c,48));
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setFocusable(true); b.setClickable(true); b.setBackground(squircleCard(c, 12));
        b.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_focused},new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{0xFFFFFFFF,0xFFFFFFFF,TEXT}));
        b.setOnClickListener(v->action.run()); return b;
    }
    public static LinearLayout column(Context c){ LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public static LinearLayout row(Context c){ LinearLayout l=new LinearLayout(c); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    public static void fullscreen(android.app.Activity a){ a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
}
