package com.oplus.posteffect.drawable;

import android.graphics.drawable.GradientDrawable;

public class MetaBallBlurDrawable {
    public int color;
    public int[] colors;
    public GradientDrawable.Orientation orientation;
    public void setMetaColor(int value) { color=value;setGradientColors(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{value}); }
    public void setGradientColors(GradientDrawable.Orientation direction,int[] values) { orientation=direction;colors=values.clone(); }
}
