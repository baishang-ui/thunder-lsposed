package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import java.util.Arrays;
import java.util.IdentityHashMap;

/** Native plate/foreground drawing with independent bounds and the current silhouette. */
final class CloudNativeDecoration {
    private static final String NATIVE_GRADIENT="com.oplus.systemui.plugins.seedling.util.d";
    private static final String[] GRADIENT_COLORS={"a","b","c"};
    private final Drawable source;
    final Drawable drawable;
    private final IdentityHashMap<Drawable,Stroke> strokes=new IdentityHashMap<>();
    private int width=-1,height=-1;
    private float radius=-1;
    private float weight=Float.NaN;
    private record Stroke(int width,ColorStateList color,float dashWidth,float dashGap) { }
    CloudNativeDecoration(Drawable source,Resources resources,Resources.Theme theme) {
        this.source=source;
        drawable=copy(source,resources,theme);
        refresh();
    }
    private static Drawable copy(Drawable source,Resources resources,Resources.Theme theme) {
        // A background blur drawable belongs to its original ViewRoot. The
        // independent native blur host draws this layer; keep its decoration.
        if(source.getClass().getName().equals("com.android.internal.graphics.drawable.BackgroundBlurDrawable"))
            return new ColorDrawable(0);
        if(source.getClass().getName().equals(NATIVE_GRADIENT)) {
            Object corners=Reflect.getObjectField(source,"d");
            Drawable result=(Drawable)Reflect.newInstance(source.getClass(),Reflect.getIntField(source,"a"),
                Reflect.getIntField(source,"b"),Reflect.getIntField(source,"c"),
                Reflect.newInstance(corners.getClass(),Reflect.callMethod(corners,"d"),Reflect.callMethod(corners,"e"),Reflect.callMethod(corners,"f")));
            Reflect.setAdditionalInstanceField(result,"thunder.cloud-capsule-decoration",true);return result;
        }
        if(source instanceof LayerDrawable layers) {
            Drawable[] fills=new Drawable[layers.getNumberOfLayers()];
            for(int i=0;i<fills.length;i++)fills[i]=copy(layers.getDrawable(i),resources,theme);
            LayerDrawable result=new LayerDrawable(fills);
            result.setPaddingMode(LayerDrawable.PADDING_MODE_STACK);
            return result;
        }
        Drawable.ConstantState state=source.getConstantState();
        if(state==null)throw new IllegalStateException("Native capsule decoration cannot be copied: "+source.getClass().getName());
        return (resources==null?state.newDrawable():state.newDrawable(resources,theme)).mutate();
    }
    void geometry(int width,int height,float radius,boolean smooth) {
        geometry(width,height,radius,smooth?CloudCornerGeometry.WEIGHT:-1f);
    }
    void geometry(int width,int height,float radius,float weight) {
        float value=Math.max(0,Math.min(radius,Math.min(width,height)/2f));
        if(this.width==width&&this.height==height&&this.radius==value&&this.weight==weight)return;
        this.width=width;this.height=height;this.radius=value;this.weight=weight;
        drawable.setBounds(0,0,width,height);CloudHook.roundContinuous(drawable,value,weight);
    }
    void refresh(){refresh(source,drawable);}
    private void refresh(Drawable source,Drawable copy) {
        copy.setState(source.getState());copy.setLevel(source.getLevel());
        copy.setLayoutDirection(source.getLayoutDirection());copy.setColorFilter(source.getColorFilter());
        if(source instanceof GradientDrawable original&&copy instanceof GradientDrawable fill) {
            int[] colors=original.getColors();
            if(colors!=null) {
                if(!Arrays.equals(colors,fill.getColors()))fill.setColors(colors.clone());
                fill.setOrientation(original.getOrientation());
            }else if(original.getColor()!=null&&!original.getColor().equals(fill.getColor()))fill.setColor(original.getColor());
            // Native dark intensity changes the foreground stroke in place.
            // Refresh it without importing native width-animation bounds or
            // replacing the independent current corner geometry.
            Object state=original.getConstantState();
            Stroke stroke=new Stroke(Reflect.getIntField(state,"mStrokeWidth"),
                (ColorStateList)Reflect.getObjectField(state,"mStrokeColors"),
                ((Number)Reflect.getObjectField(state,"mStrokeDashWidth")).floatValue(),
                ((Number)Reflect.getObjectField(state,"mStrokeDashGap")).floatValue());
            if(!stroke.equals(strokes.put(copy,stroke)))fill.setStroke(Math.max(0,stroke.width),stroke.color,stroke.dashWidth,stroke.dashGap);
        }else if(source.getClass().getName().equals(NATIVE_GRADIENT)) {
            boolean changed=false;
            for(String field:GRADIENT_COLORS) {
                int color=Reflect.getIntField(source,field);
                if(Reflect.getIntField(copy,field)!=color){Reflect.setObjectField(copy,field,color);changed=true;}
            }
            // This firmware Drawable has no ConstantState and clips its own
            // gradient with a cached Triple shared with current geometry.
            if(changed)Reflect.callMethod(copy,"onBoundsChange",copy.getBounds());
        }else if(source instanceof ColorDrawable original&&copy instanceof ColorDrawable fill) {
            // ConstantState retains the base color when the host animates ColorDrawable.setAlpha.
            Drawable.ConstantState state=original.getConstantState();
            ColorDrawable base=(ColorDrawable)state.newDrawable().mutate();base.setAlpha(255);
            if(fill.getColor()!=base.getColor())fill.setColor(base.getColor());
        }else if(source instanceof LayerDrawable originals&&copy instanceof LayerDrawable fills) {
            for(int i=0;i<originals.getNumberOfLayers();i++)refresh(originals.getDrawable(i),fills.getDrawable(i));
        }else if(source instanceof DrawableWrapper original&&copy instanceof DrawableWrapper fill)
            refresh(original.getDrawable(),fill.getDrawable());
        // Visibility belongs to the independent host, never to the native drawable animator.
        copy.setAlpha(255);
    }
}
