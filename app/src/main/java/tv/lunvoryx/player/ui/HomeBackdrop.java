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
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF destination=new RectF();
    private LinearGradient sideShade,bottomShade;
    private float artHeight;
    private boolean home=true;
    public HomeBackdrop(Context context){super(context);image=Artwork.get(context,R.drawable.hero_palma,1920);}
    public void setHome(boolean value){home=value;invalidate();}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
        super.onSizeChanged(width,height,oldWidth,oldHeight);artHeight=height*.68f;
        float left=width*.15f,scale=Math.max((width-left)/image.getWidth(),artHeight/image.getHeight());
        destination.set(left,0,width,image.getHeight()*scale);
        sideShade=new LinearGradient(0,0,width,0,new int[]{TvStyle.BG,0xF20B0D18,0x6B0B0D18,0x000B0D18},new float[]{0,.19f,.48f,.85f},Shader.TileMode.CLAMP);
        bottomShade=new LinearGradient(0,artHeight*.48f,0,artHeight,new int[]{0x000B0D18,TvStyle.BG},null,Shader.TileMode.CLAMP);
    }
    @Override protected void onDraw(Canvas c){
        c.drawColor(TvStyle.BG);float w=getWidth();
        paint.setShader(null);paint.setAlpha(home?255:75);
        c.save();c.clipRect(0,0,w,artHeight);c.drawBitmap(image,null,destination,paint);paint.setAlpha(255);
        paint.setShader(sideShade);c.drawRect(0,0,w,artHeight,paint);
        paint.setShader(bottomShade);c.drawRect(0,artHeight*.48f,w,artHeight,paint);paint.setShader(null);c.restore();
    }
}
