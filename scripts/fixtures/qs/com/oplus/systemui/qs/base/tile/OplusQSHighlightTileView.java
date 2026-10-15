package com.oplus.systemui.qs.base.tile;
public class OplusQSHighlightTileView extends android.view.View {
 public android.graphics.drawable.DrawableWrapper mBgDrawable;
 public android.graphics.drawable.Drawable icon=new android.graphics.drawable.Drawable();
 public OplusQSHighlightTileView(android.graphics.drawable.Drawable bg){
  mBgDrawable=new android.graphics.drawable.DrawableWrapper(bg);
  icon.setState(new int[]{com.oplus.systemui.qs.base.res.model.TileViewFlag.Active.getAttr()});
 }
}
