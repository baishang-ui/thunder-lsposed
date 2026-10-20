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
        public final Capsule capsule=new Capsule();
        public Capsule f(String key){return capsule;}
    }
    public static class Capsule extends android.view.View {
        public int getCapsuleDrawableWidth(){return getWidth();}
    }
}
