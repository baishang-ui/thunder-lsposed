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
import android.graphics.drawable.DrawableWrapper;
import androidx.core.graphics.PathParser;
import java.util.EnumMap;
import java.util.Map;

/** Shared vector renderer for the app preview and SystemUI; no module resources needed in the host. */
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
            if(symbol.row()>0) {
                // C17 overlays both painters in one Box: each uses the entire viewport.
                float scale=Math.min(b.width()/24f,b.height()/24f);
                canvas.translate(b.exactCenterX()-12*scale,b.exactCenterY()-12*scale);canvas.scale(scale,scale);
                paint.setColor(color);paint.setStyle(Paint.Style.FILL);
                Shape[] bars=STACKED[symbol.row()-1];
                for(int i=0;i<bars.length;i++) {
                    opacity(symbol.level()*4f/symbol.maximum()>=i+1?1:.24f);
                    canvas.drawPath(bars[i].path(),paint);
                }
                return;
            }
            float width=symbol.kind()==IosStatusIcons.Kind.BATTERY?27f:24f;
            float scale=Math.min(b.width()/width,b.height()/(symbol.kind()==IosStatusIcons.Kind.BATTERY?13f:24f));
            canvas.translate(b.exactCenterX()-(symbol.kind()==IosStatusIcons.Kind.BATTERY?width/2:12f)*scale,b.exactCenterY()-12*scale);
            canvas.scale(scale,scale);
            paint.setColor(color);paint.setAlpha(alpha*Color.alpha(color)/255);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            switch(symbol.kind()) {
                case WIFI -> wifi(canvas);
                case CELLULAR -> cellular(canvas);
                case BATTERY -> battery(canvas);
                default -> {
                    for(Shape shape:SHAPES.get(symbol.kind())) {
                        paint.setStyle(shape.width()==0?Paint.Style.FILL:Paint.Style.STROKE);
                        paint.setStrokeWidth(shape.width());canvas.drawPath(shape.path(),paint);
                    }
                }
            }
        } finally { canvas.restoreToCount(save); }
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
        paint.setTypeface(NUMERALS);paint.setTextSize(charging?(symbol.level()==100?9.4f:10.6f):(symbol.level()==100?11.2f:12f));
        paint.setTextAlign(Paint.Align.LEFT);
        // Larger numerals sit optically centered in the filled body, including 0 and 100.
        String numerals=Integer.toString(symbol.level());
        paint.getTextBounds(numerals,0,numerals.length(),numeralBounds);
        float baseline=12-(numeralBounds.top+numeralBounds.bottom)/2f;
        float centeredX=(charging?10.6f:12.25f)-(numeralBounds.left+numeralBounds.right)/2f;
        c.drawText(numerals,centeredX,baseline,paint);
        if(charging) {
            // Keep the same body width while charging; place the mark inside the silhouette.
            paint.setColor(contrast(batteryColor));paint.setAlpha(alpha);
            int save=c.save();c.translate(19.4f,8.3f);c.scale(.18f,.31f);
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

    /** Retain the native drawable and its metrics/state so switching off is exact and immediate. */
    static final class Replacement extends DrawableWrapper {
        private IosIconDrawable icon;
        private final String hit;
        private final int stackedSize;
        Replacement(Drawable nativeIcon, IosStatusIcons.Symbol symbol) {
            this(nativeIcon,symbol,0);
        }
        Replacement(Drawable nativeIcon, IosStatusIcons.Symbol symbol,int stackedSize) {
            super(nativeIcon);icon=new IosIconDrawable(symbol);
            this.stackedSize=stackedSize;
            hit=symbol.row()>0?"stacked":symbol.kind().name().toLowerCase(java.util.Locale.ROOT);
            icon.setAlpha(nativeIcon.getAlpha());icon.setColorFilter(nativeIcon.getColorFilter());
        }
        @Override public void draw(Canvas canvas) {
            if(!IosStatusIcons.active(ShadeHook.snapshot())) { super.draw(canvas);return; }
            icon.setBounds(getBounds());icon.draw(canvas);
            ShadeHook.resourceHit("status-bar-ios-symbol-draw");
            ShadeHook.resourceHit("status-bar-ios-"+hit+"-draw");
        }
        @Override public void setAlpha(int alpha) { super.setAlpha(alpha);if(icon!=null)icon.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { super.setColorFilter(filter);if(icon!=null)icon.setColorFilter(filter); }
        @Override public void setTintList(ColorStateList tint) { super.setTintList(tint);if(icon!=null)icon.setTintList(tint); }
        @Override protected boolean onStateChange(int[] state) { boolean changed=super.onStateChange(state);return (icon!=null&&icon.setState(state))||changed; }
        @Override public boolean isStateful() { return super.isStateful() || (icon!=null&&icon.isStateful()); }
        @Override public int getIntrinsicWidth() { return stackedSize>0&&IosStatusIcons.active(ShadeHook.snapshot())?stackedSize:super.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return stackedSize>0&&IosStatusIcons.active(ShadeHook.snapshot())?stackedSize:super.getIntrinsicHeight(); }
        @Override public ConstantState getConstantState() { return null; }
    }
}
