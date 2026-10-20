package com.oplus.systemui.qs.media;
import com.oplus.systemui.qs.base.res.drawable.*;
public class OplusQsBaseMediaPanelView extends android.view.ViewGroup {
 public TileDrawableWrapper bgDrawable=new TileDrawableWrapper(new GradientTileDrawable());
 public TileDrawableWrapper themeBgDrawable=new TileDrawableWrapper(new GradientTileDrawable());
 public com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable transitionDrawable=new com.oplus.systemui.qs.media.multilight.OplusQsMediaBackgroundDrawable(bgDrawable,themeBgDrawable);
 public android.view.View bg=new android.view.View();
 public int outlineUpdates;
 public OplusQsBaseMediaPanelView(){bg.setBackground(transitionDrawable);}
 public android.view.View getBg(){return bg;}
 public void onOutlineUpdate(){outlineUpdates++;}
}
