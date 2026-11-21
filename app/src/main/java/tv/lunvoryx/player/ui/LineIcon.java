package tv.lunvoryx.player.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Consistent 24-unit outlined icons, drawn sharply at the TV's actual density. */
public final class LineIcon extends View {
    private final String kind;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color=TvStyle.TEXT;
    public LineIcon(Context c,String kind){super(c);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void setColor(int value){color=value;invalidate();}
    private void line(Canvas c,float... points){Path p=new Path();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);c.drawPath(p,paint);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.save();float size=Math.min(getWidth(),getHeight());canvas.translate((getWidth()-size)/2,(getHeight()-size)/2);canvas.scale(size/24,size/24);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.65f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        switch(kind){
            case "home":line(canvas,2,11,12,3,22,11);line(canvas,5,9,5,21,10,21,10,14,14,14,14,21,19,21,19,9);break;
            case "movie":canvas.drawRoundRect(3,8,21,21,2,2,paint);line(canvas,3,8,2,3,20,1,21,6,3,8);line(canvas,7,3,10,6);line(canvas,14,2,17,5);break;
            case "series":line(canvas,2,7,12,2,22,7,12,12,2,7);line(canvas,3,11,12,16,21,11);line(canvas,3,16,12,21,21,16);break;
            case "search":canvas.drawCircle(10,10,6.5f,paint);line(canvas,14.6f,14.6f,20.5f,20.5f);break;
            case "settings":
                Path gear=new Path();for(int i=0;i<32;i++){double a=i*Math.PI/16;float r=(i%4==0||i%4==3)?10:8;float x=12+(float)Math.sin(a)*r,y=12+(float)Math.cos(a)*r;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();canvas.drawPath(gear,paint);canvas.drawCircle(12,12,3.5f,paint);break;
            case "sort":
                // AZ and up/down arrow
                line(canvas,6,6,6,18);line(canvas,4,9,6,6,8,9);
                line(canvas,11,18,11,6);line(canvas,9,15,11,18,13,15);
                line(canvas,15,8,19,8,15,16,19,16);break;
            case "calendar":
                canvas.drawRoundRect(3,4,21,21,2.5f,2.5f,paint);line(canvas,3,9,21,9);
                line(canvas,8,2,8,5);line(canvas,16,2,16,5);
                canvas.drawCircle(7.5f,13.5f,1f,paint);canvas.drawCircle(12f,13.5f,1f,paint);canvas.drawCircle(16.5f,13.5f,1f,paint);
                canvas.drawCircle(7.5f,17.5f,1f,paint);canvas.drawCircle(12f,17.5f,1f,paint);canvas.drawCircle(16.5f,17.5f,1f,paint);break;
            case "refresh":
                // Circle arrow
                android.graphics.RectF rf=new android.graphics.RectF(4,4,20,20);
                canvas.drawArc(rf,40,280,false,paint);
                line(canvas,17,4,21,8,16,9);break;
            case "globe":
                canvas.drawCircle(12,12,8.5f,paint);
                line(canvas,3.5f,12,20.5f,12);
                line(canvas,5.5f,7.5f,18.5f,7.5f);
                line(canvas,5.5f,16.5f,18.5f,16.5f);
                canvas.drawOval(new android.graphics.RectF(8,3.5f,16,20.5f),paint);break;
            case "player":
                // Video player rounded rect with play triangle
                canvas.drawRoundRect(3,5,21,19,3,3,paint);
                Path tp=new Path();tp.moveTo(10.5f,9);tp.lineTo(15.5f,12);tp.lineTo(10.5f,15);tp.close();
                paint.setStyle(Paint.Style.FILL);canvas.drawPath(tp,paint);break;
            case "subtitles":
                canvas.drawRoundRect(3,5,21,19,3,3,paint);
                // CC text or lines
                line(canvas,7,10,10,10);line(canvas,7,14,10,14);line(canvas,7,10,7,14);
                line(canvas,14,10,17,10);line(canvas,14,14,17,14);line(canvas,14,10,14,14);break;
            case "buffer":
                // 3 stacked cylinder disks
                canvas.drawOval(new android.graphics.RectF(5,4,19,9),paint);
                line(canvas,5,6.5f,5,11.5f);line(canvas,19,6.5f,19,11.5f);
                canvas.drawArc(new android.graphics.RectF(5,9,19,14),0,180,false,paint);
                line(canvas,5,11.5f,5,16.5f);line(canvas,19,11.5f,19,16.5f);
                canvas.drawArc(new android.graphics.RectF(5,14,19,19),0,180,false,paint);break;
            case "key":
                canvas.drawCircle(8,12,4.5f,paint);line(canvas,12.5f,12,20,12);line(canvas,17,12,17,15);line(canvas,19.5f,12,19.5f,14.5f);break;
            case "chevron":
                line(canvas,10,7,15,12,10,17);break;
            case "all_grid":
                canvas.drawCircle(8,8,2.2f,paint);canvas.drawCircle(16,8,2.2f,paint);
                canvas.drawCircle(8,16,2.2f,paint);canvas.drawCircle(16,16,2.2f,paint);break;
            case "resume":
                canvas.drawArc(new android.graphics.RectF(4,4,20,20),60,280,false,paint);
                line(canvas,17,4,21,8,16,9);
                line(canvas,12,8,12,12,15,14);break;
            case "folder":
                Path f=new Path();f.moveTo(3,6);f.lineTo(9,6);f.lineTo(11,8);f.lineTo(21,8);f.lineTo(21,19);f.lineTo(3,19);f.close();
                canvas.drawPath(f,paint);break;
            case "categories":
                line(canvas,3,7,12,3,21,7,12,11,3,7);
                line(canvas,3,12,12,16,21,12);
                line(canvas,3,17,12,21,21,17);break;
            case "audio":
                canvas.drawRoundRect(3,6,21,18,2,2,paint);
                line(canvas,6,12,8,12);line(canvas,10,10,10,14);line(canvas,14,9,14,15);line(canvas,18,11,18,13);break;
            case "director":
                canvas.drawRoundRect(3,8,21,20,2,2,paint);
                line(canvas,3,8,2,3,20,1,21,6,3,8);break;
            case "cast":
                canvas.drawCircle(12,7,3.5f,paint);
                canvas.drawArc(new android.graphics.RectF(6,12,18,21),180,180,false,paint);
                canvas.drawCircle(5,9,2.5f,paint);
                canvas.drawCircle(19,9,2.5f,paint);break;
            case "sort_arrows":
                line(canvas,8,5,8,19);line(canvas,5,8,8,5,11,8);
                line(canvas,16,5,16,19);line(canvas,13,16,16,19,19,16);break;
            case "history":
                canvas.drawCircle(12,12,8,paint);
                line(canvas,12,7.5f,12,12,15.5f,14);break;
            case "trending":
                line(canvas,4,16,10,10,14,14,20,7);
                line(canvas,15,7,20,7,20,12);break;
            case "mic":
                canvas.drawRoundRect(9,4,15,14,3,3,paint);
                canvas.drawArc(new android.graphics.RectF(6,8,18,17),0,180,false,paint);
                line(canvas,12,17,12,21);line(canvas,8,21,16,21);break;
            case "close":
                line(canvas,7,7,17,17);line(canvas,17,7,7,17);break;
            case "info":
                canvas.drawCircle(12,12,8.5f,paint);
                canvas.drawCircle(12,8.5f,1f,paint);
                line(canvas,12,11.5f,12,16);break;
            case "trophy":
                canvas.drawRoundRect(7,4,17,14,3,3,paint);
                line(canvas,7,6,4,6,4,10,7,10);
                line(canvas,17,6,20,6,20,10,17,10);
                line(canvas,12,14,12,18);
                line(canvas,8,18,16,18);break;
            case "news":
                canvas.drawRoundRect(3,5,21,19,2,2,paint);
                line(canvas,6,8,18,8);
                line(canvas,6,12,11,12);line(canvas,6,15,11,15);
                canvas.drawRoundRect(13,11,18,16,1,1,paint);break;
            case "child":
                canvas.drawCircle(12,12,7,paint);
                canvas.drawCircle(9.5f,10.5f,1.2f,paint);
                canvas.drawCircle(14.5f,10.5f,1.2f,paint);
                canvas.drawArc(new android.graphics.RectF(9.5f,12,14.5f,15),0,180,false,paint);
                canvas.drawCircle(6.5f,6.5f,2.5f,paint);
                canvas.drawCircle(17.5f,6.5f,2.5f,paint);break;
            case "down":
                line(canvas,8,10,12,14,16,10);break;
            case "list":canvas.drawRoundRect(3,3,21,21,2,2,paint);line(canvas,7,8,17,8);line(canvas,7,12,17,12);line(canvas,7,16,14,16);break;
            case "star":Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?10:4.8f;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();canvas.drawPath(star,paint);break;
            case "play":paint.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(8,4);play.lineTo(21,12);play.lineTo(8,20);play.close();canvas.drawPath(play,paint);break;
            case "playlist_add":
                canvas.drawRoundRect(2,3,17,8,2,2,paint);canvas.drawCircle(6,5.5f,1f,paint);
                canvas.drawRoundRect(2,10,17,15,2,2,paint);canvas.drawCircle(6,12.5f,1f,paint);
                canvas.drawCircle(18,18,4.5f,paint);line(canvas,18,15.5f,18,20.5f);line(canvas,15.5f,18,20.5f,18);break;
            case "server":
                canvas.drawRoundRect(3,3,21,8,2,2,paint);canvas.drawCircle(6,5.5f,1f,paint);line(canvas,15,5.5f,18,5.5f);
                canvas.drawRoundRect(3,10,21,15,2,2,paint);canvas.drawCircle(6,12.5f,1f,paint);line(canvas,15,12.5f,18,12.5f);
                canvas.drawRoundRect(3,17,21,22,2,2,paint);canvas.drawCircle(6,19.5f,1f,paint);line(canvas,15,19.5f,18,19.5f);break;
            case "tag":
                Path tagP=new Path();tagP.moveTo(4,4);tagP.lineTo(13,4);tagP.lineTo(21,12);tagP.lineTo(12,21);tagP.lineTo(4,13);tagP.close();canvas.drawPath(tagP,paint);canvas.drawCircle(8,8,1.5f,paint);break;
            case "user":
                canvas.drawCircle(12,8,3.8f,paint);canvas.drawArc(new android.graphics.RectF(5,13,19,23),180,180,false,paint);break;
            case "lock":
                canvas.drawRoundRect(5,10,19,21,3,3,paint);canvas.drawArc(new android.graphics.RectF(8,4,16,13),180,180,false,paint);canvas.drawCircle(12,15,1.5f,paint);break;
            case "eye":
                Path ep=new Path();ep.moveTo(2,12);ep.quadTo(12,4,22,12);ep.quadTo(12,20,2,12);canvas.drawPath(ep,paint);canvas.drawCircle(12,12,3,paint);break;
            case "link":
                canvas.drawRoundRect(3,8,12,16,3,3,paint);canvas.drawRoundRect(12,8,21,16,3,3,paint);line(canvas,8,12,16,12);break;
            case "file":
                Path fp=new Path();fp.moveTo(5,3);fp.lineTo(14,3);fp.lineTo(19,8);fp.lineTo(19,21);fp.lineTo(5,21);fp.close();canvas.drawPath(fp,paint);line(canvas,14,3,14,8,19,8);break;
            case "phone":
                canvas.drawRoundRect(6,2,18,22,3,3,paint);line(canvas,6,5,18,5);line(canvas,6,19,18,19);canvas.drawCircle(12,20.5f,0.8f,paint);break;
            case "wifi":
                canvas.drawArc(new android.graphics.RectF(4,6,20,22),215,110,false,paint);
                canvas.drawArc(new android.graphics.RectF(7,10,17,20),215,110,false,paint);
                canvas.drawCircle(12,18,1.5f,paint);break;
            case "copy":
                canvas.drawRoundRect(8,8,20,20,2,2,paint);canvas.drawRoundRect(4,4,16,16,2,2,paint);break;
            case "bolt":
                Path bp=new Path();bp.moveTo(13,2);bp.lineTo(6,12);bp.lineTo(12,12);bp.lineTo(11,22);bp.lineTo(18,10);bp.lineTo(12,10);bp.close();canvas.drawPath(bp,paint);break;
            case "plus":
                line(canvas,12,5,12,19);line(canvas,5,12,19,12);break;
            case "brand":canvas.drawRoundRect(2,6,22,22,4,4,paint);line(canvas,7,1,12,6,17,1);paint.setStyle(Paint.Style.FILL);Path triangle=new Path();triangle.moveTo(10,10);triangle.lineTo(17,14);triangle.lineTo(10,18);triangle.close();canvas.drawPath(triangle,paint);break;
            default:canvas.drawRoundRect(2,4,22,18,2,2,paint);line(canvas,8,21,16,21);line(canvas,12,18,12,21);break;
        }
        canvas.restore();
    }
}
