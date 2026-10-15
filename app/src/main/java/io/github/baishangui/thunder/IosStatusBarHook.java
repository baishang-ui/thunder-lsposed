package io.github.baishangui.thunder;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** C17 has three independent status icon renderers; each keeps its native state and visibility. */
final class IosStatusBarHook {
    private static final Map<Drawable,Boolean> tracked = Collections.synchronizedMap(new WeakHashMap<>());
    private record BatteryBinding(java.lang.ref.WeakReference<Object>[] views,Object style,boolean active) { }
    private static final Map<ImageView,BatteryBinding> batteryBindings=Collections.synchronizedMap(new WeakHashMap<>());
    private static java.lang.reflect.Method batteryBinder;
    private static final ThreadLocal<Boolean> rebindingBattery=ThreadLocal.withInitial(()->false);
    private record BatteryLabel(WeakReference<Object> owner,int visibility) { }
    private static final Map<Drawable,WeakReference<Object>> batteryOwners=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,BatteryLabel> batteryLabels=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Resources,Map<Integer,String>> names = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Integer> stackedDepth=ThreadLocal.withInitial(()->0);
    static void installStackedScope(ClassLoader loader) {
        hookStackedScope(Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconKt",loader),"OplusStackedMobileIcon");
    }
    static void installStackedLayoutScope(ClassLoader loader) {
        // Compose can re-enter the layout independently of its outer composable.
        hookStackedScope(Reflect.findClass("com.android.systemui.statusbar.pipeline.shared.ui.composable.OplusStackedMobileIconLayoutStrategy",loader),"SignalIcon");
    }
    private static void hookStackedScope(Class<?> type,String prefix) {
        boolean found=false;
        for(var method:type.getDeclaredMethods()) {
            if(!method.getName().equals(prefix) && !method.getName().startsWith(prefix+"-"))continue;
            Object[] signature=new Object[method.getParameterCount()+1];
            System.arraycopy(method.getParameterTypes(),0,signature,0,method.getParameterCount());
            signature[signature.length-1]=new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) { stackedDepth.set(stackedDepth.get()+1); }
                @Override protected void afterHookedMethod(Param p) {
                    int depth=stackedDepth.get()-1;
                    if(depth<=0)stackedDepth.remove();else stackedDepth.set(depth);
                }
            };
            Api102Runtime.findAndHookMethod(type,method.getName(),signature);
            // Keep the tiny AppCompatResources loader observable even in optimized host code.
            Api102Runtime.deoptimizeCallers(type,method.getName());found=true;
        }
        if(!found)throw new IllegalStateException("Missing stacked signal scope: "+type.getName());
    }
    static void installStackedResources(ClassLoader loader) {
        Api102Runtime.findAndHookMethod(Reflect.findClass("androidx.appcompat.content.res.AppCompatResources",loader),
            "getDrawable",Context.class,int.class,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(stackedDepth.get()==0 || p.hasThrowable() || !(p.getResult() instanceof Drawable drawable)
                            || drawable instanceof IosIconDrawable.Replacement)return;
                    String name=resourceName(((Context)p.args[0]).getResources(),(Integer)p.args[1]);
                    var symbol=IosStatusIcons.signal(name,false);
                    if(symbol!=null && symbol.row()>0) {
                        var replacement=new IosIconDrawable.Replacement(drawable,symbol,
                            Math.round(20*((Context)p.args[0]).getResources().getDisplayMetrics().density));
                        tracked.put(replacement,Boolean.TRUE);p.setResult(replacement);
                    }
                }
            });
    }
    static void installDynamicSignals(ClassLoader loader) {
        Class<?> signal=Reflect.findClass("com.android.settingslib.graph.SignalDrawable",loader);
        Api102Runtime.findAndHookMethod(signal,"draw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Drawable nativeIcon=(Drawable)p.thisObject;
                Drawable.Callback callback=nativeIcon.getCallback();
                for(int depth=0;depth<6 && callback instanceof Drawable parent;depth++)callback=parent.getCallback();
                if(!(callback instanceof ImageView image) || !Boolean.FALSE.equals(signalOwner(image)))return;
                tracked.put(nativeIcon,Boolean.TRUE);
                if(!IosStatusIcons.active(ShadeHook.snapshot()))return;
                int encoded=nativeIcon.getLevel(),level=encoded&255,maximum=((encoded>>8)&255)-1;
                int status=encoded>>>16;
                if(status>3 || maximum<1 || maximum>5 || level>maximum)return;
                int flags=status==2?IosStatusIcons.WARNING:status==3?IosStatusIcons.CONNECTING:0;
                Paint paint=(Paint)Reflect.getObjectField(nativeIcon,"mForegroundPaint");
                String state=level+":"+maximum+":"+status+":"+flags;
                IosIconDrawable icon=(IosIconDrawable)Reflect.getAdditionalInstanceField(nativeIcon,"thunder.iosSignal");
                if(!state.equals(Reflect.getAdditionalInstanceField(nativeIcon,"thunder.iosSignalState"))) {
                    icon=new IosIconDrawable(new IosStatusIcons.Symbol(status==1?IosStatusIcons.Kind.NO_SERVICE:IosStatusIcons.Kind.CELLULAR,status==1?0:level,maximum).marked(flags));
                    Reflect.setAdditionalInstanceField(nativeIcon,"thunder.iosSignal",icon);
                    Reflect.setAdditionalInstanceField(nativeIcon,"thunder.iosSignalState",state);
                }
                icon.color(paint.getColor()|0xff000000);icon.setAlpha(paint.getAlpha());icon.setColorFilter(paint.getColorFilter());
                icon.setBounds(nativeIcon.getBounds());icon.draw((Canvas)p.args[0]);p.setResult(null);
                ShadeHook.resourceHit("status-bar-ios-cellular-dynamic-draw");
            }
        });
    }
    static void installSymbols(ClassLoader loader) {
        Class<?> view=Reflect.findClass("com.android.systemui.statusbar.StatusBarIconView",loader);
        Api102Runtime.findAndHookMethod(view,"getIcon","com.android.internal.statusbar.StatusBarIcon",new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if(p.hasThrowable() || !(p.getResult() instanceof Drawable drawable))return;
                // Notification icons use the same class; never restyle a notification's app identity.
                if(Reflect.getObjectField(p.thisObject,"mNotification")!=null)return;
                String slot=(String)Reflect.getObjectField(p.thisObject,"mSlot");
                Icon source=(Icon)Reflect.getObjectField(p.args[0],"icon");
                if(source==null || source.getType()!=Icon.TYPE_RESOURCE || !"com.android.systemui".equals(source.getResPackage()))return;
                String name=resourceName(((View)p.thisObject).getResources(),source.getResId());
                var symbol=IosStatusIcons.slot(slot,name);
                if(symbol!=null) {
                    p.setResult(wrap(drawable,symbol,(ImageView)p.thisObject));
                    ((View)p.thisObject).post(()->IosStatusBarMetrics.observe((ImageView)p.thisObject));
                }
            }
        });
    }
    static void installSignals() {
        // Scope the platform setter to signal views, rather than replacing shared QS drawables.
        Api102Runtime.findAndHookMethod(ImageView.class,"setImageResource",int.class,new HookCallback() {
            @Override protected void afterHookedMethod(HookCallback.Param p) {
                if(p.hasThrowable() || (Integer)p.args[0]==0)return;
                ImageView image=(ImageView)p.thisObject;
                Boolean wifi=signalOwner(image);
                String resource=resourceName(image.getResources(),(Integer)p.args[0]);
                if(!resource.startsWith("com.android.systemui:drawable/"))return;
                var symbol=wifi!=null ? IosStatusIcons.system(resource)
                    : IosStatusIcons.slot(symbolOwner(image),resource);
                if(symbol==null)return;
                Drawable nativeIcon=image.getDrawable();
                if(nativeIcon!=null && !(nativeIcon instanceof IosIconDrawable.Replacement))image.setImageDrawable(wrap(nativeIcon,symbol,image));
            }
        });
    }
    static void installBattery(ClassLoader loader) {
        Class<?> horizontal=Reflect.findClass("com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable",loader);
        Api102Runtime.findAndHookMethod(horizontal,"draw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(HookCallback.Param p) {
                Drawable nativeIcon=(Drawable)p.thisObject;
                tracked.put(nativeIcon,Boolean.TRUE);
                if(!IosStatusIcons.active(ShadeHook.snapshot()))return;
                int level=(Integer)Reflect.callMethod(nativeIcon,"getBatteryLevel");
                if(level<0 || level>100)return;
                boolean percent=Reflect.getBooleanField(nativeIcon,"isShowPercentIn");
                var area=nativeIcon.getBounds();
                if(area.isEmpty())return;
                WeakReference<Object> ownerRef=batteryOwners.get(nativeIcon);Object owner=ownerRef==null?null:ownerRef.get();
                boolean charging=(Integer)Reflect.callMethod(nativeIcon,"getChargeIconId")>0
                    || (owner!=null && Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(owner,"thunder.iosCharging")));
                int outline=(Integer)Reflect.callMethod(nativeIcon,"getOutlineColor");
                int fill=(Integer)Reflect.callMethod(nativeIcon,"getProgressColor");
                IosIconDrawable icon=(IosIconDrawable)Reflect.getAdditionalInstanceField(nativeIcon,"thunder.iosBattery");
                // Store one renderer per state, avoiding paths/paints allocated on every frame.
                String state=level+":"+charging+":"+percent+":"+outline+":"+fill;
                if(!state.equals(Reflect.getAdditionalInstanceField(nativeIcon,"thunder.iosBatteryState"))) {
                    icon=new IosIconDrawable(new IosStatusIcons.Symbol(IosStatusIcons.Kind.BATTERY,level,100));
                    icon.color(outline);icon.battery(charging,fill);
                    Reflect.setAdditionalInstanceField(nativeIcon,"thunder.iosBattery",icon);
                    Reflect.setAdditionalInstanceField(nativeIcon,"thunder.iosBatteryState",state);
                }
                Paint paint=(Paint)Reflect.getObjectField(nativeIcon,"paint");
                icon.setAlpha(paint.getAlpha());icon.setColorFilter(paint.getColorFilter());
                icon.setBounds(area);
                icon.draw((Canvas)p.args[0]);p.setResult(null);
                ShadeHook.resourceHit("status-bar-ios-battery");
            }
        });
    }
    static void installBatteryLabels(ClassLoader loader) {
        Class<?> binder=Reflect.findClass("com.oplus.systemui.statusbar.pipeline.battery.ui.binder.BatteryViewBinder",loader);
        Api102Runtime.findAndHookMethod(binder,"bind$updatePercentOutView",TextView.class,
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
            "com.oplus.systemui.statusbar.pipeline.battery.ui.model.PercentOutIcon",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable() || !(p.args[0] instanceof TextView label))return;
                    batteryLabels.put(label,new BatteryLabel(new WeakReference<>(p.args[1]),label.getVisibility()));
                    applyBatteryLabel(label);
                }
            });
        for(var method:binder.getDeclaredMethods())if(method.getName().equals("bind$updateBatteryIconStyle")) {
            method.setAccessible(true);batteryBinder=method;break;
        }
        Api102Runtime.findAndHookMethod(binder,"bind$updateReverseChargeView",ImageView.class,
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
            "com.oplus.systemui.statusbar.pipeline.battery.ui.model.ChargeIcon",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable() || !(p.args[0] instanceof ImageView image))return;
                    Drawable drawable=image.getDrawable();
                    if(drawable!=null && !(drawable instanceof IosIconDrawable.Replacement))
                        image.setImageDrawable(wrap(drawable,new IosStatusIcons.Symbol(IosStatusIcons.Kind.REVERSE_CHARGE),image));
                }
            });
        Api102Runtime.findAndHookMethod(binder,"bind$updateChargingView",ImageView.class,
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
            "com.oplus.systemui.statusbar.pipeline.battery.ui.model.ChargeIcon",new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable() || !(p.args[0] instanceof ImageView image))return;
                    if(p.args[2]!=null)Reflect.setAdditionalInstanceField(p.args[1],"thunder.iosCharging",Reflect.getBooleanField(p.args[2],"isVisible"));
                    Object drawable=Reflect.getObjectField(p.args[1],"batteryStyleDrawable");
                    if(drawable instanceof Drawable icon){batteryOwners.put(icon,new WeakReference<>(p.args[1]));icon.invalidateSelf();}
                    batteryLabels.put(image,new BatteryLabel(new WeakReference<>(p.args[1]),image.getVisibility()));
                    applyBatteryLabel(image);
                }
            });
        Api102Runtime.findAndHookMethod(binder,"bind$updateBatteryIconStyle",ImageView.class,
            "com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView",
            "com.oplus.systemui.statusbar.pipeline.battery.ui.viewmodel.LocationBasedBatteryViewModel",
            "android.widget.FrameLayout",TextView.class,
            "com.oplus.systemui.statusbar.pipeline.battery.ui.model.BatteryIconStyle",new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    if(!(p.args[0] instanceof ImageView image) || p.args[5]==null)return;
                    boolean active=IosStatusIcons.active(ShadeHook.snapshot());
                    IosStatusBarMetrics.reset(image);
                    if(!rebindingBattery.get()) {
                        @SuppressWarnings("unchecked") WeakReference<Object>[] views=new WeakReference[5];
                        for(int i=0;i<5;i++)views[i]=new WeakReference<>(p.args[i]);
                        batteryBindings.put(image,new BatteryBinding(views,p.args[5],active));
                    }
                    p.setObjectExtra("nativeBatteryStyle",p.args[5]);
                    p.args[5]=batteryStyle(p.args[5],active);
                }
                @Override protected void afterHookedMethod(Param p) {
                    Object original=p.getObjectExtra("nativeBatteryStyle");
                    if(original!=null)p.args[5]=original;
                    if(!p.hasThrowable() && p.args[0] instanceof ImageView image) {
                        Object drawable=Reflect.getObjectField(p.args[1],"batteryStyleDrawable");
                        if(drawable instanceof Drawable icon)batteryOwners.put(icon,new WeakReference<>(p.args[1]));
                        IosStatusBarMetrics.observe(image);
                    }
                    refreshBatteryLabels();
                }
            });
    }
    private static Object batteryStyle(Object original,boolean active) {
        int style=(Integer)Reflect.callMethod(original,"getIconStyle");
        if(!active || (style!=1 && style!=3 && style!=4 && style!=5))return original;
        // Copy the immutable UI model; never change the user's battery style setting.
        return Reflect.callMethod(original,"copy",1,true,
            Reflect.getObjectField(original,"localeList"),Reflect.getIntField(original,"densityDpi"),
            Reflect.getObjectField(original,"scaleFactor"),Reflect.getIntField(original,"frameColorMode"),
            Reflect.getBooleanField(original,"isThemeModeColor"),Reflect.getIntField(original,"themeId"),
            false,false,Reflect.getIntField(original,"batteryOutChargeBgMode"));
    }
    private static void refreshBatteryStyles() {
        if(batteryBinder==null)return;
        ArrayList<ImageView> images;
        synchronized(batteryBindings){images=new ArrayList<>(batteryBindings.keySet());}
        for(ImageView image:images)image.post(()->{
            BatteryBinding binding=batteryBindings.get(image);boolean active=IosStatusIcons.active(ShadeHook.snapshot());
            if(binding==null || binding.active==active)return;
            Object[] args=new Object[6];
            for(int i=0;i<5;i++)args[i]=binding.views[i].get();
            // Percent TextView can be absent; the meter and view model are required by the native binder.
            if(args[0]==null || args[1]==null || args[2]==null)return;
            IosStatusBarMetrics.reset(image);
            args[5]=batteryStyle(binding.style,active);
            try {
                rebindingBattery.set(true);batteryBinder.invoke(null,args);
                batteryBindings.put(image,new BatteryBinding(binding.views,binding.style,active));
                IosStatusBarMetrics.observe(image);
            } catch(ReflectiveOperationException e) { ShadeHook.frameworkFailure("status-bar-ios-battery-rebind",e); }
            finally { rebindingBattery.remove(); }
        });
    }
    private static void applyBatteryLabel(View label) {
        BatteryLabel state=batteryLabels.get(label);if(state==null)return;
        Object owner=state.owner.get();
        Object drawable=owner==null?null:Reflect.getObjectField(owner,"batteryStyleDrawable");
        boolean internal=IosStatusIcons.active(ShadeHook.snapshot()) && drawable!=null
            && drawable.getClass().getName().equals("com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable");
        int visibility=internal?View.GONE:state.visibility;
        if(label.getVisibility()!=visibility)label.setVisibility(visibility);
    }
    private static void refreshBatteryLabels() {
        ArrayList<View> labels;
        synchronized(batteryLabels){labels=new ArrayList<>(batteryLabels.keySet());}
        for(View label:labels)label.post(()->applyBatteryLabel(label));
    }
    static void observeResource(ImageView image) {
        Drawable drawable=image.getDrawable();
        if(drawable==null || drawable instanceof IosIconDrawable.Replacement
            || (signalOwner(image)==null && symbolOwner(image)==null))return;
        // XML inflation assigns src before the ImageView has a status-bar parent.
        int id=Reflect.getIntField(image,"mResource");
        if(id==0){observeLoaded(image);return;}
        String resource=resourceName(image.getResources(),id);
        if(!resource.startsWith("com.android.systemui:drawable/"))return;
        var symbol=IosStatusIcons.system(resource);
        if(symbol!=null)image.setImageDrawable(wrap(drawable,symbol,image));
    }
    static void observeLoaded(ImageView image) {
        Drawable nativeIcon=image.getDrawable();
        if(nativeIcon==null || nativeIcon instanceof IosIconDrawable.Replacement || signalOwner(image)!=null)return;
        // Legacy icons carry package identity through getIcon; do not bypass that resource guard.
        for(Class<?> type=image.getClass();type!=null;type=type.getSuperclass())
            if(type.getName().equals("com.android.systemui.statusbar.StatusBarIconView"))return;
        var symbol=IosStatusIcons.slot(symbolOwner(image),null);
        if(symbol!=null)image.setImageDrawable(wrap(nativeIcon,symbol,image));
    }
    private static Drawable wrap(Drawable nativeIcon,IosStatusIcons.Symbol symbol,ImageView image) {
        var replacement=new IosIconDrawable.Replacement(nativeIcon,symbol,
            Math.round(20*image.getResources().getDisplayMetrics().density));
        tracked.put(replacement,Boolean.TRUE);
        return replacement;
    }
    static Boolean signalOwner(View image) {
        View view=image;
        for(int depth=0;depth<6 && view!=null;depth++) {
            for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass()) {
                String name=type.getName();
                if(name.equals("com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView"))return true;
                if(name.equals("com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView"))return false;
            }
            view=view.getParent() instanceof View parent?parent:null;
        }
        return null;
    }
    static String symbolOwner(View image) {
        View view=image;
        for(int depth=0;depth<6 && view!=null;depth++) {
            for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass()) {
                String name=type.getName();
                if(name.equals("com.android.systemui.statusbar.StatusBarIconView"))
                    return Reflect.getObjectField(view,"mNotification")==null?(String)Reflect.getObjectField(view,"mSlot"):null;
                if(name.equals("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView")
                        || name.equals("com.oplus.systemui.statusbar.phone.dynamic.icon.OplusStatusBarIconView"))
                    return (String)Reflect.callMethod(view,"getSlot");
            }
            view=view.getParent() instanceof View parent?parent:null;
        }
        return null;
    }
    static String resourceName(Resources resources,int id) {
        synchronized(names) {
            Map<Integer,String> cache=names.computeIfAbsent(resources,key->new java.util.HashMap<>());
            return cache.computeIfAbsent(id,key->{try{return resources.getResourceName(key);}catch(Resources.NotFoundException e){return "";}});
        }
    }
    static void refresh() {
        refreshBatteryStyles();
        IosStatusBarMetrics.refresh();
        IosSignalStackHook.refresh();
        refreshBatteryLabels();
        ArrayList<Drawable> drawables;
        synchronized(tracked) { drawables=new ArrayList<>(tracked.keySet()); }
        for(Drawable drawable:drawables)drawable.invalidateSelf();
    }
    private IosStatusBarHook() { }
}
