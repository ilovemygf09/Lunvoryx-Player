package tv.lunvoryx.player.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import tv.lunvoryx.player.R;
import tv.lunvoryx.player.data.Channel;
import tv.lunvoryx.player.data.PlaylistRepository;
import static tv.lunvoryx.player.ui.TvStyle.*;

/** Native TV home: all tiles lead to actual library content or a real action. */
public final class HomeScreen extends ScrollView {
    public interface Actions {
        void navigate(String section);
        void addList();
        void demo();
        void play(List<Channel> queue,int index);
    }
    public HomeScreen(Context context,PlaylistRepository repository,Actions actions){
        super(context);setFillViewport(true);setClipToPadding(false);setVerticalScrollBarEnabled(false);
        LinearLayout body=column(context);body.setPadding(dp(context,4),0,dp(context,4),dp(context,20));addView(body);
        FrameLayout hero=new FrameLayout(context);body.addView(hero,new LinearLayout.LayoutParams(-1,dp(context,280)));
        LinearLayout copy=column(context);copy.setPadding(dp(context,16),dp(context,65),0,0);hero.addView(copy,new FrameLayout.LayoutParams(-1,-1));
        TextView badge=text(context,"En directo",11,TEXT);badge.setTypeface(Typeface.DEFAULT_BOLD);badge.setPadding(dp(context,10),dp(context,4),dp(context,10),dp(context,4));badge.setBackground(TvStyle.squircleSolid(context,12,0xFFE3222A));copy.addView(badge,new LinearLayout.LayoutParams(-2,-2));
        TextView heading=text(context,"TV en vivo",46,TEXT);heading.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-2,-2);hp.topMargin=dp(context,8);copy.addView(heading,hp);
        TextView description=text(context,"Tus canales favoritos, en un mismo lugar.\nDeportes, noticias y mucho más.",14,MUTED);description.setLineSpacing(dp(context,3),1);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(dp(context,350),-2);dp.topMargin=dp(context,5);copy.addView(description,dp);
        LinearLayout watch=row(context);watch.setGravity(Gravity.CENTER);watch.setPadding(dp(context,17),0,dp(context,20),0);watch.setBackground(pill(context));watch.setFocusable(true);watch.setClickable(true);watch.setContentDescription("Ver ahora, abrir televisión en vivo");
        LineIcon play=new LineIcon(context,"play");play.setColor(INK);watch.addView(play,new LinearLayout.LayoutParams(dp(context,18),dp(context,18)));TextView label=text(context,"Ver ahora",14,INK);label.setTypeface(Typeface.DEFAULT_BOLD);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(context,7);watch.addView(label,lp);watch.setOnClickListener(v->{if(repository.getAllChannels().isEmpty())actions.addList();else actions.navigate("TV en vivo");});
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-2,dp(context,36));wp.topMargin=dp(context,17);copy.addView(watch,wp);
        LinearLayout tiles=row(context);View[] categoryViews=new View[4];String[] names={"TV en vivo","Películas","Series","Ajustes"},icons={"tv","movie","series","settings"};int[] photos={R.drawable.category_live,R.drawable.category_movies,R.drawable.category_series,R.drawable.category_live};
        watch.setId(View.generateViewId());
        for(int i=0;i<names.length;i++){final String name=names[i];FrameLayout tile=category(context,name,icons[i],photos[i],i);tile.setId(View.generateViewId());categoryViews[i]=tile;tile.setNextFocusUpId(watch.getId());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(context,88),1);if(i<3)p.rightMargin=dp(context,11);tiles.addView(tile,p);tile.setOnClickListener(v->actions.navigate(name));}
        watch.setNextFocusDownId(categoryViews[0].getId());
        body.addView(tiles);
        ArrayList<Channel> recent=new ArrayList<>();for(String id:repository.getRecentIds()){Channel c=repository.findChannel(id);if(c!=null)recent.add(c);if(recent.size()==4)break;}
        List<Channel> library=repository.getAllChannels();boolean history=!recent.isEmpty();if(!history)for(Channel c:library){recent.add(c);if(recent.size()==4)break;}
        LinearLayout section=row(context);TextView title=text(context,history?"Seguir viendo":recent.isEmpty()?"Tu televisión empieza aquí":"En tu biblioteca",18,TEXT);title.setTypeface(Typeface.DEFAULT_BOLD);section.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(!recent.isEmpty()){TextView all=button(context,"Ver todo",()->actions.navigate(history?"Recientes":"Todos"));all.setTextSize(11);all.setMinHeight(0);all.setPadding(dp(context,12),dp(context,5),dp(context,12),dp(context,5));section.addView(all);}
        LinearLayout.LayoutParams shp=new LinearLayout.LayoutParams(-1,-2);shp.topMargin=dp(context,17);shp.bottomMargin=dp(context,10);body.addView(section,shp);
        if(recent.isEmpty()){
            LinearLayout empty=row(context);empty.setPadding(dp(context,18),dp(context,12),dp(context,16),dp(context,12));empty.setBackground(TvStyle.squircleCard(context,14));
            LinearLayout words=column(context);TextView first=text(context,"Añade tu lista. Elige qué ver.",15,TEXT);first.setTypeface(Typeface.DEFAULT_BOLD);words.addView(first);TextView second=text(context,"Importa una URL o un archivo M3U.",12,MUTED);second.setPadding(0,dp(context,6),0,0);words.addView(second);empty.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView add=button(context,"Añadir lista",actions::addList);add.setId(View.generateViewId());empty.addView(add);TextView demo=button(context,"Probar",actions::demo);demo.setId(View.generateViewId());add.setNextFocusUpId(categoryViews[0].getId());demo.setNextFocusUpId(categoryViews[3].getId());for(int i=0;i<4;i++)categoryViews[i].setNextFocusDownId(i<2?add.getId():demo.getId());LinearLayout.LayoutParams dmp=new LinearLayout.LayoutParams(-2,-2);dmp.leftMargin=dp(context,8);empty.addView(demo,dmp);body.addView(empty,new LinearLayout.LayoutParams(-1,dp(context,84)));
        }else{
            LinearLayout items=row(context);SharedPreferences progress=context.getSharedPreferences("watch_progress",Context.MODE_PRIVATE);
            for(int i=0;i<recent.size();i++){Channel channel=recent.get(i);final int index=i;FrameLayout card=mediaCard(context,channel,progress);card.setId(View.generateViewId());card.setNextFocusUpId(categoryViews[i].getId());categoryViews[i].setNextFocusDownId(card.getId());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(context,102),1);if(i<3)p.rightMargin=dp(context,11);items.addView(card,p);card.setOnClickListener(v->actions.play(recent,index));}
            if(!recent.isEmpty())for(int i=recent.size();i<4;i++)categoryViews[i].setNextFocusDownId(items.getChildAt(recent.size()-1).getId());
            for(int i=recent.size();i<4;i++){View spacer=new View(context);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,1,1);if(i<3)p.rightMargin=dp(context,11);items.addView(spacer,p);}body.addView(items);
        }
    }
    private static android.graphics.drawable.Drawable pill(Context c){return TvStyle.squircleSolid(c,12,0xFFFFFFFF);}
    private static FrameLayout frame(Context c){FrameLayout f=new FrameLayout(c);f.setBackground(TvStyle.squircleCard(c,12));f.setClipToOutline(true);f.setFocusable(true);f.setClickable(true);return f;}
    private static FrameLayout category(Context c,String label,String icon,int photo,int index){
        FrameLayout frame=frame(c);frame.setContentDescription(label);ImageView image=new ImageView(c);image.setImageBitmap(Artwork.get(c,photo,600));image.setScaleType(ImageView.ScaleType.CENTER_CROP);if(index==3){android.graphics.ColorMatrix m=new android.graphics.ColorMatrix();m.setSaturation(.1f);image.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));}frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(c);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x15121A31,0x8020253C}));frame.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=column(c);content.setGravity(Gravity.CENTER);LineIcon symbol=new LineIcon(c,icon);content.addView(symbol,new LinearLayout.LayoutParams(dp(c,31),dp(c,31)));TextView title=text(c,label,16,TEXT);title.setTypeface(Typeface.DEFAULT_BOLD);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);tp.topMargin=dp(c,8);content.addView(title,tp);frame.addView(content,new FrameLayout.LayoutParams(-1,-1));return frame;
    }
    private static FrameLayout mediaCard(Context c,Channel channel,SharedPreferences progress){
        FrameLayout card=frame(c);card.setContentDescription("Reproducir "+channel.name);ImageView image=new ImageView(c);image.setImageBitmap(Artwork.get(c,R.drawable.category_movies,600));image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(c.getSharedPreferences("display",Context.MODE_PRIVATE).getBoolean("logos",true)&&!channel.logoUrl.isEmpty()){ImageView poster=new ImageView(c);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);LogoLoader.load(channel.logoUrl,poster);card.addView(poster,new FrameLayout.LayoutParams(-1,-1));}
        View shade=new View(c);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00202538,0xF0101321}));card.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout caption=column(c);caption.setPadding(dp(c,12),0,dp(c,12),dp(c,9));TextView title=text(c,channel.name,14,TEXT);title.setTypeface(Typeface.DEFAULT_BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.addView(title);TextView group=text(c,channel.group,10,MUTED);group.setSingleLine();group.setEllipsize(android.text.TextUtils.TruncateAt.END);group.setPadding(0,dp(c,3),0,0);caption.addView(group);
        long position=progress.getLong(channel.id+".position",0),duration=progress.getLong(channel.id+".duration",0);if(position>0&&duration>0){ProgressBar bar=new ProgressBar(c,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(1000);bar.setProgress((int)Math.min(1000,position*1000/duration));bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF9FCFFF));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x887B7E8A));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(c,4));p.topMargin=dp(c,6);caption.addView(bar,p);}
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);card.addView(caption,cp);return card;
    }
}
