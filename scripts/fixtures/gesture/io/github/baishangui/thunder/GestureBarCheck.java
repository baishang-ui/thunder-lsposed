package io.github.baishangui.thunder;

import android.graphics.Insets;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;

public final class GestureBarCheck {
    private static final String BAR = "com.android.systemui.navigationbar.views.NavigationBar";
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    private static void config(boolean enabled, boolean hidden, boolean removed) throws Exception {
        Bundle b = new Bundle(); b.putBoolean("enabled",enabled);
        b.putBoolean("gesture_bar_hidden",hidden); b.putBoolean("gesture_bar_remove_inset",removed);
        var field=ShadeHook.class.getDeclaredField("config");field.setAccessible(true);field.set(null,new HookConfig(b));
    }
    public static final class Provider {
        public final int type;
        public Insets size = new Insets(0,0,0,28), minimum = new Insets(0,0,0,12);
        Provider(int type) { this.type=type; }
        public int getType() { return type; }
        public Provider setInsetsSize(Insets insets) { size=insets;return this; }
        public Provider setMinimalInsetsSizeInDisplayCutoutSafe(Insets insets) { minimum=insets;return this; }
    }
    public static final class Params {
        public int height=96, flags=545259560; public float alpha=1;
        public Provider[] providedInsets = {
            new Provider(WindowInsets.Type.navigationBars()), new Provider(WindowInsets.Type.tappableElement()),
            new Provider(WindowInsets.Type.systemGestures()), new Provider(WindowInsets.Type.mandatorySystemGestures()),
            new Provider(WindowInsets.Type.ime())
        };
    }
    public static final class Handler { public boolean mIsButtonForcedVisible; }
    public static final class Bar {
        public int mNavBarMode=2, mCurrentRotation=3, relayouts;
        public Handler mEdgeBackGestureHandler=new Handler();
        public Params current;
        Params layout() throws Throwable {
            Params nativeParams=new Params();
            var p=StatusBarIconCheck.param(this, mCurrentRotation, null);p.setResult(nativeParams);
            Api102Runtime.callbacks.get(BAR+"#getBarLayoutParamsForRotation").after(p);
            return (Params)p.getResult();
        }
        public void repositionNavigationBar(int rotation) throws Throwable {
            check(rotation==mCurrentRotation,"live relayout keeps actual rotation");
            relayouts++;current=layout();
        }
    }
    public static final class Handle extends View {
        public int invalidations;
        @Override public void invalidate() { invalidations++; }
    }
    public static void main(String[] args) throws Throwable {
        ShadeHook.install("com.android.systemui","com.android.systemui",GestureBarCheck.class.getClassLoader());
        Bar bar=new Bar();
        config(true,false,false);Params original=bar.layout();
        check(original.providedInsets[0].size.bottom==28,"default keeps native content inset");
        config(true,false,true);Params cleared=bar.layout();
        check(cleared.providedInsets[0].size==Insets.NONE && cleared.providedInsets[1].size==Insets.NONE,
            "cancel lift removes navigation and tappable content insets");
        check(cleared.providedInsets[0].minimum==Insets.NONE,"cutout minimum does not reintroduce bottom gap");
        for(int i=2;i<5;i++) check(cleared.providedInsets[i].size.bottom==28 && cleared.providedInsets[i].minimum.bottom==12,
            "gesture and keyboard source "+i+" retains native dimensions");
        check(cleared.height==96 && cleared.flags==545259560 && cleared.alpha==1,"navigation input window stays native");
        check(original.providedInsets[0].size.bottom==28,"old native params are not mutated");
        bar.mNavBarMode=0;check(bar.layout().providedInsets[0].size.bottom==28,"button navigation keeps content inset");
        bar.mNavBarMode=2;bar.mEdgeBackGestureHandler.mIsButtonForcedVisible=true;
        check(bar.layout().providedInsets[0].size.bottom==28,"forced accessibility buttons keep content inset");
        bar.mEdgeBackGestureHandler.mIsButtonForcedVisible=false;
        Handle handle=new Handle();
        for(String name:new String[]{"com.android.systemui.navigationbar.gestural.NavigationHandle",
                "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle",
                "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle"}) {
            var draw=Api102Runtime.callbacks.get(name+"#onDraw");
            var p=StatusBarIconCheck.param(handle,new android.graphics.Canvas());p.setResult("native");
            draw.before(p);check("native".equals(p.getResult()),"inset-only option retains visible indicator: "+name);
            config(true,true,true);p.setResult("native");draw.before(p);
            check(p.getResult()==null,"hide suppresses indicator drawing: "+name);
            config(true,false,true);
        }
        GestureBarHook.refresh();check(bar.current.providedInsets[0].size==Insets.NONE,"live enable sends zero content inset");
        config(true,true,false);GestureBarHook.refresh();
        check(bar.current.providedInsets[0].size.bottom==28,"live disable restores freshly generated native inset");
        check(handle.invalidations>0,"settings redraw already existing handles");
        int count=bar.relayouts;GestureBarHook.refresh();
        check(bar.relayouts==count,"unrelated settings avoid unnecessary navigation relayout");
        config(true,false,true);GestureBarHook.refresh();
        config(false,true,true);GestureBarHook.refresh();
        check(bar.current.providedInsets[0].size.bottom==28,"module disable restores original content inset");
        var draw=Api102Runtime.callbacks.get("com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle#onDraw");
        var drawing=StatusBarIconCheck.param(handle,new android.graphics.Canvas());drawing.setResult("native");
        draw.before(drawing);check("native".equals(drawing.getResult()),"module disable restores native indicator drawing");
        var preferences=SettingsProvider.preferences(new android.content.Context());
        preferences.edit().putBoolean("gesture_bar_hidden",true).putBoolean("gesture_bar_remove_inset",true).apply();
        android.os.Binder.uid=1000;
        Bundle fallback=new SettingsProvider().call("settings",null,null);
        check(fallback.getBoolean("gesture_bar_hidden",false) && fallback.getBoolean("gesture_bar_remove_inset",false),
            "provider fallback carries both navigation controls");
        System.out.println("PASS: "+checks+" gesture indicator, content inset, input, mode and restoration checks");
    }
}
