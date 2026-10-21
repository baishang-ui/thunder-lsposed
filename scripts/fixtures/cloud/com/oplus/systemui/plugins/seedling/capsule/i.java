package com.oplus.systemui.plugins.seedling.capsule;
public class i {
    public static final Companion e=new Companion();
    public final Host c=new Host();
    public static final i instance=new i();
    public static class Companion {public i u(android.content.Context c){return instance;}}
    public static class Host {
        public final Container container=new Container();
        public Container l(){return container;}
    }
    public static class Container extends android.view.View {
        public String l="current";
        public boolean mini,unbound;
        public final android.view.View miniView=new android.view.View();
        public boolean j(){return mini;}
        public android.view.View getMiniView(){return miniView;}
        public final Capsule capsule=new Capsule();
        public Capsule f(String key){return unbound?null:capsule;}
    }
    public static class Capsule extends com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView {
        public int drawableWidth=-1;
        public int getCapsuleDrawableWidth(){return drawableWidth<0?getWidth():drawableWidth;}
        public void setCapsuleDrawableWidth(int value){drawableWidth=value;}
    }
}
