package com.oplus.systemui.plugins.seedling.card.ui.view;
public class CardView extends android.view.View {
    public android.view.View content=new android.view.View();
    public final Background bg=new Background();
    public android.view.View getContentView(){return content;}
    public Background getBgView(){return bg;}
    public void setLeftTopRightBottomForBlur(android.graphics.Rect r){bg.bounds=r;}
    public static class Background extends android.view.View {
        public com.oplus.view.ViewRootManager c=new com.oplus.view.ViewRootManager(this);
        public android.graphics.Rect bounds;
        public android.graphics.Rect getBounds(){return bounds;}
        public void setBounds(android.graphics.Rect r){bounds=r;}
    }
}
