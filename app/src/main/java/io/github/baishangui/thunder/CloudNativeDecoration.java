package io.github.baishangui.thunder;

import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import java.util.Arrays;

/** Native capsule fill with its own bounds and opacity; the Surface outline owns its silhouette. */
final class CloudNativeDecoration {
    private final Drawable source;
    final Drawable drawable;
    CloudNativeDecoration(Drawable source,Resources resources,Resources.Theme theme) {
        this.source=source;
        Drawable.ConstantState state=source.getConstantState();
        if(state==null)throw new IllegalStateException("Native capsule decoration cannot be copied: "+source.getClass().getName());
        drawable=(resources==null?state.newDrawable():state.newDrawable(resources,theme)).mutate();
        refresh();
    }
    void refresh(){refresh(source,drawable);}
    private static void refresh(Drawable source,Drawable copy) {
        copy.setState(source.getState());copy.setLevel(source.getLevel());
        copy.setLayoutDirection(source.getLayoutDirection());copy.setColorFilter(source.getColorFilter());
        if(source instanceof GradientDrawable original&&copy instanceof GradientDrawable fill) {
            int[] colors=original.getColors();
            if(colors!=null) {
                if(!Arrays.equals(colors,fill.getColors()))fill.setColors(colors.clone());
                fill.setOrientation(original.getOrientation());
            }else if(original.getColor()!=null&&!original.getColor().equals(fill.getColor()))fill.setColor(original.getColor());
            fill.setCornerRadii(null);fill.setCornerRadius(0);
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
