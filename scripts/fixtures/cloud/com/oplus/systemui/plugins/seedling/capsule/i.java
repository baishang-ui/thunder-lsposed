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
        public final java.util.Map<String,Capsule> incoming=new java.util.HashMap<>();
        public Capsule f(String key){return unbound?null:incoming.getOrDefault(key,capsule);}
    }
    public static class Capsule extends com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView {
        public int drawableWidth=-1;
        public int targetWidth=-1,targetHeight=-1;
        @Override public int getMeasuredWidth(){return targetWidth<0?getWidth():targetWidth;}
        @Override public int getMeasuredHeight(){return targetHeight<0?getHeight():targetHeight;}
        public int getCapsuleDrawableWidth(){return drawableWidth<0?getWidth():drawableWidth;}
        public void setCapsuleDrawableWidth(int value){drawableWidth=value;}
    }
}
