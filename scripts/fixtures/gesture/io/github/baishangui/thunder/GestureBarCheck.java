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
    public static class Handle extends View {
        public final android.graphics.Paint mPaint = new android.graphics.Paint();
        public float mLastIntensity;
        public final BlurRoot root = new BlurRoot();
        @Override public Object getViewRootImpl() { return root; }
        public int invalidations;
        @Override public void invalidate() { invalidations++; }
    }
    public static class BlurRoot extends View.Root {
        public View.Blur blur;
        public int creations;
        public boolean fail;
        @Override public android.graphics.drawable.Drawable createBackgroundBlurDrawable() {
            creations++;
            if (fail) throw new IllegalStateException("blur unavailable");
            return blur = new View.Blur();
        }
    }
    private static void config(Bundle b) throws Exception {
        var field=ShadeHook.class.getDeclaredField("config");field.setAccessible(true);field.set(null,new HookConfig(b));
        GestureBarHook.refresh();
    }
    private static android.graphics.Canvas draw(View handle) throws Throwable {
        var canvas = new android.graphics.Canvas();
        var p = StatusBarIconCheck.param(handle, canvas); p.setResult("native");
        String name = handle instanceof com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle
            ? "com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle"
            : "com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle";
        Api102Runtime.callbacks.get(name + "#onDraw").before(p);
        check(p.getResult() == null, "custom appearance draws the indicator once");
        return canvas;
    }
    private static void sample(View handle, float intensity) throws Throwable {
        Api102Runtime.callbacks.get("com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle#setDarkIntensity")
            .after(StatusBarIconCheck.param(handle, intensity));
    }
    private static void appearance() throws Throwable {
        Bundle b = new Bundle(); b.putBoolean("enabled", true); b.putBoolean("gesture_style", true);
        b.putInt("gesture_length", 200); b.putInt("gesture_thickness", 8); b.putInt("gesture_bottom", 10);
        Handle handle = new Handle(); handle.actualWidth = 550; handle.actualHeight = 110;
        handle.mPaint.setColor(0xffeeeeee);
        int nativeWidth = handle.layout.width;
        config(b); var canvas = draw(handle); float[] rect = canvas.rectangles.get(0);
        check(handle.layout.width == 550 && rect[2]-rect[0] == 550, "length converts dp and updates the visible handle width");
        check(rect[3]-rect[1] == 22 && rect[3] == 82.5f, "thickness and bottom distance use separate dp controls");
        check(handle.mPaint.getColor() == 0xffeeeeee, "custom painting leaves native animation paint intact");
        int relayouts = handle.invalidations;
        b.putInt("gesture_bottom", 32); b.putInt("gesture_thickness", 16); config(b);
        rect = draw(handle).rectangles.get(0);
        check(rect[1] >= 0 && rect[3] <= handle.getHeight(), "large size and offset stay inside the navigation window");
        check(handle.invalidations > relayouts, "appearance edits invalidate already attached handles");
        b.putInt("gesture_material", 1); b.putBoolean("notification_card_tint", true);
        b.putInt("notification_card_color", 0x80664422); b.putBoolean("notification_card_blur", true);
        b.putInt("notification_card_radius", 72); b.putBoolean("notification_card_corner_enabled", true);
        b.putInt("notification_card_corner", 1); config(b);
        check(draw(handle).colors.get(0) == 0xffeeeeee && handle.root.creations == 0,
            "retired source and notification settings cannot change gesture material");
        b.putBoolean("gesture_tint", true); b.putInt("gesture_color", 0x80664422);
        b.putBoolean("gesture_blur", true); b.putInt("gesture_radius", 72);
        b.putBoolean("gesture_corner_enabled", true); b.putInt("gesture_corner", 1); config(b);
        canvas = draw(handle);
        check(canvas.colors.get(0) == 0x80664422, "gesture material supplies its own tint and transparency");
        check(handle.root.blur.radius == 72 && handle.root.blur.corner == 2.75f, "gesture blur and dp corner reach compositor");
        var blur = handle.root.blur; int creations = handle.root.creations;
        draw(handle); check(handle.root.creations == creations, "drawing reuses blur drawable on the same view root");
        b.putInt("gesture_material", 2); b.putBoolean("qs_card_tint", true); b.putInt("qs_card_color", 0x33abcdef); config(b);
        check(blur.radius == 0 && blur.getBounds().isEmpty(), "material change clears the previous compositor region");
        check(draw(handle).colors.get(0) == 0x80664422, "tile settings cannot change gesture material");
        b.putInt("gesture_color", 0x33abcdef); b.putBoolean("gesture_blur", false); config(b);
        b.putBoolean("gesture_colors", true); b.putInt("gesture_light_color", 0xffffccaa);
        b.putInt("gesture_dark_color", 0xff123456); config(b);
        sample(handle, 0); check(draw(handle).colors.get(0) == 0x33ffccaa, "dark background selects light color and keeps material opacity");
        sample(handle, 1); check(draw(handle).colors.get(0) == 0x33123456, "light background selects dark color");
        sample(handle, .5f); check(draw(handle).colors.get(0) == 0x33898080, "native intensity animation interpolates RGB channels");
        b.putBoolean("gesture_auto_color", false); config(b);
        handle.res.configuration.uiMode = 32;
        check(draw(handle).colors.get(0) == 0x33ffccaa, "manual colors follow system dark theme when sampling is off");
        handle.res.configuration.uiMode = 16;
        check(draw(handle).colors.get(0) == 0x33123456, "manual colors follow system light theme when sampling is off");
        b.putBoolean("gesture_blur", true); b.putInt("gesture_radius", 100); config(b); draw(handle);
        blur = handle.root.blur;
        for (var listener : handle.attachListeners) listener.onViewDetachedFromWindow(handle);
        check(blur.radius == 0, "detaching removes the blur region");
        for (var listener : handle.attachListeners) listener.onViewAttachedToWindow(handle);
        draw(handle); check(handle.root.creations > creations, "reattachment can recreate a compositor region");
        config(b); handle.root.fail = true; creations = handle.root.creations;
        draw(handle); draw(handle);
        check(handle.root.creations == creations + 1, "unavailable blur falls back without a failing allocation every frame");
        handle.root.fail = false; config(b); draw(handle);
        check(handle.root.blur.radius == 100, "configuration refresh retries recovered blur support");
        var software = new android.graphics.Canvas(); software.hardware = false;
        Api102Runtime.callbacks.get("com.oplus.systemui.navigationbar.gesture.sidegesture.OplusNavigationHandle#onDraw")
            .before(StatusBarIconCheck.param(handle, software));
        check(handle.root.blur.radius == 0 && !software.rectangles.isEmpty(), "software canvas draws tint without retaining hardware blur");
        draw(handle); check(handle.root.blur.radius == 100, "hardware drawing resumes blur after software fallback");
        b.putBoolean("gesture_bar_hidden", true); config(b);
        check(handle.layout.width == nativeWidth, "hiding restores the native layout width");
        b.putBoolean("gesture_bar_hidden", false); config(b); draw(handle);
        b.putBoolean("enabled", false); config(b);
        check(handle.layout.width == nativeWidth, "module disable restores the original handle width");
        b.putBoolean("enabled", true); config(b); draw(handle);
        handle.layout.width = 311; draw(handle); // Native setVertical writes the rotated width.
        b.putBoolean("gesture_style", false); config(b);
        check(handle.layout.width == 311, "disable preserves newer native orientation width");
        b.putBoolean("gesture_style", true); b.putInt("gesture_thickness", 8); b.putInt("gesture_bottom", 10); config(b);
        var oriented = new com.android.systemui.navigationbar.gestural.QuickswitchOrientedNavHandle();
        oriented.actualWidth = 110; oriented.actualHeight = 550;
        int overlayWidth = oriented.layout.width;
        for (int rotation : new int[]{1, 3}) {
            oriented.mDeltaRotation = rotation;
            rect = draw(oriented).rectangles.get(0);
            check(rect[2]-rect[0] == 22 && rect[3]-rect[1] == 550, "rotated quickswitch follows vertical indicator geometry");
            check(rotation == 1 ? rect[0] == 27.5f : rect[2] == 82.5f, "rotated bottom offset follows the correct screen edge");
        }
        check(oriented.layout.width == overlayWidth, "rotation overlay keeps its native full-window layout");
        check(GestureBarHook.blend(0, -1, -1) == 0 && GestureBarHook.blend(0, -1, 2) == -1, "color intensity is bounded");
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
        appearance();
        var preferences=SettingsProvider.preferences(new android.content.Context());
        preferences.edit().putBoolean("gesture_bar_hidden",true).putBoolean("gesture_bar_remove_inset",true).apply();
        preferences.edit().putBoolean("gesture_style",true).putInt("gesture_length",222)
            .putBoolean("gesture_colors",true).putBoolean("gesture_auto_color",false)
            .putInt("gesture_dark_color",0xff123456).putBoolean("gesture_blur",true).putInt("gesture_radius",88).apply();
        android.os.Binder.uid=1000;
        Bundle fallback=new SettingsProvider().call("settings",null,null);
        check(fallback.getBoolean("gesture_bar_hidden",false) && fallback.getBoolean("gesture_bar_remove_inset",false),
            "provider fallback carries both navigation controls");
        HookConfig fallbackConfig = new HookConfig(fallback);
        check(fallbackConfig.gestureStyle && fallbackConfig.gestureLength == 222 && !fallback.containsKey("gesture_material")
            && fallbackConfig.gestureColors && !fallbackConfig.gestureAutoColor && fallbackConfig.gestureDarkColor == 0xff123456
            && fallbackConfig.gestureArea.blur && fallbackConfig.gestureArea.radius == 88, "provider fallback carries full gesture appearance");
        System.out.println("PASS: "+checks+" gesture indicator, content inset, input, mode and restoration checks");
    }
}
