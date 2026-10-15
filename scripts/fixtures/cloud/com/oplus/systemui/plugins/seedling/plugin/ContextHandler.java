package com.oplus.systemui.plugins.seedling.plugin;
public class ContextHandler {
    public static final Companion Companion=new Companion();
    public static class Companion {
        public ContextHandler getInstance(){return instance;}
    }
    public static final ContextHandler instance=new ContextHandler();
    public android.content.Context context=new android.content.Context();
    public android.content.Context getPluginCtx(){return context;}
}
