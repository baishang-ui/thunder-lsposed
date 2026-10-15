package io.github.baishangui.thunder;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Overrides C17's presentation mode flow, leaving dual-SIM eligibility and per-SIM state native. */
final class IosSignalStackHook {
    private static final Map<Object,Boolean> modes=Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> own=ThreadLocal.withInitial(()->false);
    private static Class<?> mutableFlow;
    static void install(ClassLoader loader) {
        mutableFlow=Reflect.findClass("kotlinx.coroutines.flow.MutableStateFlow",loader);
        Class<?> state=Reflect.findClass("kotlinx.coroutines.flow.StateFlowImpl",loader);
        Api102Runtime.findAndHookMethod(state,"setValue",Object.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                if(own.get() || !(p.args[0] instanceof Boolean value) || !modes.containsKey(p.thisObject))return;
                modes.put(p.thisObject,value);
                HookConfig config=ShadeHook.snapshot();
                if(IosStatusIcons.active(config))p.args[0]=config.statusBarStackSignals;
            }
        });
        Api102Runtime.deoptimizeCallers(state,"emit","tryEmit");
        Class<?> interactor=Reflect.findClass(
            "com.oplus.systemui.statusbar.pipeline.mobile.domain.interactor.OplusMobileIconsInteractorExImpl",loader);
        Api102Runtime.hookAllConstructors(interactor,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { track(Reflect.getObjectField(p.thisObject,"isStackableMode")); }
        });
        Api102Runtime.findAndHookMethod(interactor,"isStackableMode",new HookCallback() {
            @Override protected void afterHookedMethod(Param p) { if(!p.hasThrowable())track(p.getResult()); }
        });
    }
    private static Object writable(Object flow) {
        if(flow==null)return null;
        if(mutableFlow.isInstance(flow))return flow;
        // stateIn returns a ReadonlyStateFlow around a MutableStateFlow; retain the native collector.
        if(!flow.getClass().getName().equals("kotlinx.coroutines.flow.ReadonlyStateFlow"))return null;
        for(Field field:flow.getClass().getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers()))continue;
            try {
                field.setAccessible(true);Object value=field.get(flow);
                if(mutableFlow.isInstance(value))return value;
            } catch(IllegalAccessException ignored) { }
        }
        return null;
    }
    private static void track(Object readonly) {
        Object flow=writable(readonly);if(flow==null || modes.containsKey(flow))return;
        Object value=Reflect.callMethod(flow,"getValue");if(!(value instanceof Boolean nativeMode))return;
        modes.put(flow,nativeMode);apply(flow);
    }
    private static void apply(Object flow) {
        HookConfig config=ShadeHook.snapshot();Boolean nativeMode=modes.get(flow);if(nativeMode==null)return;
        boolean value=IosStatusIcons.active(config)?config.statusBarStackSignals:nativeMode;
        own.set(true);
        try { Reflect.callMethod(flow,"setValue",value); }
        finally { own.remove(); }
        ShadeHook.resourceHit("status-bar-ios-signal-mode");
    }
    static void refresh() {
        ArrayList<Object> flows;
        synchronized(modes){flows=new ArrayList<>(modes.keySet());}
        for(Object flow:flows)apply(flow);
    }
    private IosSignalStackHook() { }
}
