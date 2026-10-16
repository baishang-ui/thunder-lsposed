package io.github.baishangui.thunder;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.core.graphics.PathParser;
import java.util.EnumMap;
import java.util.Map;

/** App-only design preview. SystemUI uses compiled XML resources and native drawables. */
final class IosIconDrawable extends Drawable {
    private record Shape(Path path, float width) { }
    private static final Map<IosStatusIcons.Kind,Shape[]> SHAPES = new EnumMap<>(IosStatusIcons.Kind.class);
    private static final Shape[][] STACKED=new Shape[2][];
    static {
        for(var kind:IosStatusIcons.Kind.values())SHAPES.put(kind,parse(IosStatusIcons.paths(kind)));
        for(int row=1;row<=2;row++)STACKED[row-1]=parse(IosIconAssets.stacked(row));
    }
    private static Shape[] parse(IosStatusIcons.Stroke[] paths) {
        Shape[] shapes=new Shape[paths.length];
        for(int i=0;i<paths.length;i++) {
            var source=paths[i];Path path=PathParser.createPathFromPathData(source.path());
            if(source.evenOdd())path.setFillType(Path.FillType.EVEN_ODD);
            Matrix matrix=new Matrix();matrix.setValues(new float[]{source.scale(),0,source.x(),0,source.scale(),source.y(),0,0,1});
            path.transform(matrix);shapes[i]=new Shape(path,source.width());
        }
        return shapes;
    }
    private static final float GAP=3f, HEIGHT=20f;
    private static final Map<IosStatusIcons.Kind,float[]> INK=new EnumMap<>(IosStatusIcons.Kind.class);
    private static final Map<IosStatusIcons.Kind,float[]> METRICS=new EnumMap<>(IosStatusIcons.Kind.class);
    private static final Map<IosStatusIcons.Kind,Path> SLASHES=new EnumMap<>(IosStatusIcons.Kind.class);
    private static final float[] STACKED_METRICS=IosIconAssets.metrics(IosStatusIcons.Kind.CELLULAR,true);
    static { for(var kind:IosStatusIcons.Kind.values()) {
        INK.put(kind,IosIconAssets.bounds(kind));METRICS.put(kind,IosIconAssets.metrics(kind,false));
        float[] ink=INK.get(kind);SLASHES.put(kind,PathParser.createPathFromPathData("M"+ink[0]+",3 L"+ink[2]+",21"));
    } }
    static float cellWidth(IosStatusIcons.Symbol symbol) {
        if(symbol.kind()==IosStatusIcons.Kind.BATTERY)return 30f;
        if(symbol.kind()==IosStatusIcons.Kind.LABEL)return Math.max(9f,Math.min(52f,symbol.label().length()*6.3f))+GAP;
        return metrics(symbol)[0];
    }
    static float cellHeight() { return HEIGHT; }
    private static float[] metrics(IosStatusIcons.Symbol symbol) {
        return symbol.row()>0?STACKED_METRICS:METRICS.get(symbol.kind());
    }
    IosStatusIcons.Symbol symbol() { return symbol; }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final IosStatusIcons.Symbol symbol;
    private int color = Color.WHITE, alpha = 255;
    private ColorStateList tint;
    private boolean charging;
    private static final Typeface NUMERALS = Typeface.create("sans-serif",Typeface.BOLD);
    private int batteryColor = Color.WHITE;
    private final Rect numeralBounds = new Rect();
    IosIconDrawable(IosStatusIcons.Symbol symbol) { this.symbol=symbol; }
    void battery(boolean charging, int fill) { this.charging=charging;this.batteryColor=fill; }
    void color(int value) { color=value;invalidateSelf(); }
    @Override public void draw(Canvas canvas) {
        Rect b=getBounds();
        if(b.isEmpty())return;
        int save=canvas.save();
        try {
            float scale;
            if(symbol.kind()==IosStatusIcons.Kind.BATTERY) {
                scale=Math.min(b.width()/30f,b.height()/20f);
                canvas.translate(b.exactCenterX()-13.5f*scale,b.exactCenterY()-12*scale);
            } else if(symbol.kind()==IosStatusIcons.Kind.LABEL) {
                scale=Math.min(b.width()/cellWidth(symbol),b.height()/HEIGHT);
                canvas.translate(b.exactCenterX()-cellWidth(symbol)*scale/2,b.exactCenterY()-10*scale);
            } else {
                float[] metrics=metrics(symbol);
                scale=Math.min(b.width()/cellWidth(symbol),b.height()/HEIGHT)*metrics[1];
                canvas.translate(b.exactCenterX()-metrics[2]*scale,b.exactCenterY()-metrics[3]*scale);
            }
            canvas.scale(scale,scale);
            if(symbol.row()>0) {
                paint.setColor(color);paint.setStyle(Paint.Style.FILL);
                Shape[] bars=STACKED[symbol.row()-1];
                for(int i=0;i<bars.length;i++) {
                    opacity(symbol.kind()!=IosStatusIcons.Kind.NO_SERVICE && symbol.level()*4f/symbol.maximum()>=i+1?1:.24f);
                    canvas.drawPath(bars[i].path(),paint);
                }
                float rowY=symbol.row()==1?9:18;
                if(symbol.kind()==IosStatusIcons.Kind.NO_SERVICE || symbol.flags()!=0) {
                    opacity(1);paint.setTextSize(5.5f);paint.setTypeface(NUMERALS);
                    centeredText(canvas,symbol.kind()==IosStatusIcons.Kind.NO_SERVICE?"×":(symbol.flags()&IosStatusIcons.ROAMING)!=0?"R":"!",22,rowY);
                }
                return;
            }
            paint.setColor(color);paint.setAlpha(alpha*Color.alpha(color)/255);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            switch(symbol.kind()) {
                case WIFI -> wifi(canvas);
                case CELLULAR -> cellular(canvas);
                case BATTERY -> battery(canvas);
                case LABEL -> {
                    paint.setStyle(Paint.Style.FILL);paint.setTypeface(NUMERALS);
                    paint.setTextSize(Math.min(10f,10f*(cellWidth(symbol)-GAP)/Math.max(1f,symbol.label().length()*6.3f)));
                    centeredText(canvas,symbol.label(),cellWidth(symbol)/2,10);
                }
                case ACTIVITY -> {
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
                    Shape[] shapes=SHAPES.get(symbol.kind());
                    for(int i=0;i<shapes.length;i++) {
                        opacity((symbol.level()&(1<<i))!=0?1:.2f);paint.setStrokeWidth(shapes[i].width());canvas.drawPath(shapes[i].path(),paint);
                    }
                }
                default -> {
                    for(Shape shape:SHAPES.get(symbol.kind())) {
                        opacity(symbol.kind()==IosStatusIcons.Kind.NO_SERVICE && shape.width()==0?.24f:1f);
                        paint.setStyle(shape.width()==0?Paint.Style.FILL:Paint.Style.STROKE);
                        paint.setStrokeWidth(shape.width());canvas.drawPath(shape.path(),paint);
                    }
                }
            }
            if(symbol.flags()!=0)badge(canvas);
        } finally { canvas.restoreToCount(save); }
    }
    private void centeredText(Canvas canvas,String text,float cx,float cy) {
        paint.setTextAlign(Paint.Align.LEFT);paint.getTextBounds(text,0,text.length(),numeralBounds);
        canvas.drawText(text,cx-(numeralBounds.left+numeralBounds.right)/2f,cy-(numeralBounds.top+numeralBounds.bottom)/2f,paint);
    }
    private void badge(Canvas canvas) {
        opacity(1);paint.setStyle(Paint.Style.FILL);paint.setTypeface(NUMERALS);paint.setTextSize(8f);
        float[] ink=INK.get(symbol.kind());
        if((symbol.flags()&IosStatusIcons.ROAMING)!=0)centeredText(canvas,"R",ink[0]+2.5f,6);
        if((symbol.flags()&IosStatusIcons.WARNING)!=0)centeredText(canvas,"!",ink[2]-.5f,18);
        if((symbol.flags()&IosStatusIcons.NO_VOICE)!=0) {
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);
            canvas.drawPath(SLASHES.get(symbol.kind()),paint);
        }
        if((symbol.flags()&IosStatusIcons.CONNECTING)!=0) {
            paint.setStyle(Paint.Style.FILL);canvas.drawCircle(ink[2],20,1.2f,paint);
        }
    }
    private void opacity(float fraction) { paint.setAlpha(Math.round(alpha*Color.alpha(color)/255f*fraction)); }
    private void wifi(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        Shape[] shapes=SHAPES.get(IosStatusIcons.Kind.WIFI);
        for(int i=0;i<shapes.length;i++) {
            opacity((i==0?symbol.level()>0:symbol.level()*3f/symbol.maximum()>=i+.5f)?1:.24f);
            canvas.drawPath(shapes[i].path(),paint);
        }
    }
    private void cellular(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        Shape[] shapes=SHAPES.get(IosStatusIcons.Kind.CELLULAR);
        for(int i=0;i<shapes.length;i++) {
            opacity(symbol.level()*4f/symbol.maximum()>=4-i?1:.24f);
            canvas.drawPath(shapes[i].path(),paint);
        }
    }
    private void battery(Canvas c) {
        paint.setColorFilter(null);
        // iOS 16 percentage mode: one solid silhouette, never a frame plus inset progress.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(batteryColor);paint.setAlpha(alpha*Color.alpha(batteryColor)/255);
        Shape[] silhouette=SHAPES.get(IosStatusIcons.Kind.BATTERY);
        c.drawPath(silhouette[0].path(),paint);
        paint.setColor(color);opacity(.5f);
        c.drawPath(silhouette[1].path(),paint);
        paint.setColor(contrast(batteryColor));paint.setAlpha(alpha);
        paint.setTypeface(NUMERALS);paint.setTextSize(charging?(symbol.level()==100?8.2f:9.6f):(symbol.level()==100?9.5f:10.4f));
        paint.setTextAlign(Paint.Align.LEFT);
        // Leave a visible inner inset around the numbers, including 100 and charging.
        String numerals=Integer.toString(symbol.level());
        paint.getTextBounds(numerals,0,numerals.length(),numeralBounds);
        float baseline=12-(numeralBounds.top+numeralBounds.bottom)/2f;
        float centeredX=(charging?10.3f:12.25f)-(numeralBounds.left+numeralBounds.right)/2f;
        c.drawText(numerals,centeredX,baseline,paint);
        if(charging) {
            // Keep the same body width while charging; place the mark inside the silhouette.
            paint.setColor(contrast(batteryColor));paint.setAlpha(alpha);
            int save=c.save();c.translate(18.6f,7.1f);c.scale(.24f,.45f);
            c.drawPath(SHAPES.get(IosStatusIcons.Kind.PERFORMANCE)[0].path(),paint);c.restoreToCount(save);
        }
    }
    private static int contrast(int color) { return Color.luminance(color)>.45?Color.BLACK:Color.WHITE; }
    @Override public void setAlpha(int value) { alpha=value;invalidateSelf(); }
    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public void setTintList(ColorStateList value) { tint=value;if(value!=null)color(value.getColorForState(getState(),value.getDefaultColor())); }
    @Override protected boolean onStateChange(int[] state) { if(tint==null)return false;color(tint.getColorForState(state,tint.getDefaultColor()));return true; }
    @Override public boolean isStateful() { return tint!=null && tint.isStateful(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }

}
