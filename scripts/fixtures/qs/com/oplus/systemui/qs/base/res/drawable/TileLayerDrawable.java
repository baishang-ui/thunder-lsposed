package com.oplus.systemui.qs.base.res.drawable;
public class TileLayerDrawable extends android.graphics.drawable.LayerDrawable {
 public Object pathProvider=new com.oplusos.systemui.common.outline.RoundRectOutlineProvider(28f,1.4f);
 public int pathInvalidations;
 public TileLayerDrawable(android.graphics.drawable.Drawable... layers){super(layers);setBounds(new android.graphics.Rect(0,0,352,352));}
 public Object getPathProvider(){return pathProvider;}
 public void invalidatePath(){pathInvalidations++;}
}
