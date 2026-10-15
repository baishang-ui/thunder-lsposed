package com.oplus.systemui.qs.base.res.drawable;
public class TileDrawableWrapper extends android.graphics.drawable.DrawableWrapper {
 public Object pathProvider=new com.oplusos.systemui.common.outline.RoundRectOutlineProvider(28f,1.4f);
 public int pathInvalidations;
 public TileDrawableWrapper(android.graphics.drawable.Drawable d){super(d);setBounds(new android.graphics.Rect(0,0,352,352));}
 public Object getPathProvider(){return pathProvider;}
 public void invalidatePath(){pathInvalidations++;}
 @Override protected boolean onStateChange(int[] state){return getDrawable()!=null&&getDrawable().setState(state);}
}
