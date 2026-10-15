package io.github.baishangui.thunder;

import android.graphics.Typeface;
import android.text.Layout;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Density-independent status sizes; native layout and type are restored when the style is disabled. */
final class IosStatusBarMetrics {
    private static final Typeface CLOCK_TYPE=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private record Size(int width,int height,ImageView.ScaleType scaleType,int gravity) { }
    private record Font(float pixels,Typeface typeface,boolean applied) { }
    private record ClockMeasure(int width,int height,float density,float pixels,float textWidth,boolean seconds) { }
    private static final Map<TextView,ClockMeasure> measurements=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ImageView,Size> images=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TextView,Font> clocks=Collections.synchronizedMap(new WeakHashMap<>());
    static void install(ClassLoader loader) {
        Api102Runtime.findAndHookMethod(ImageView.class,"setImageDrawable",Drawable.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(!p.hasThrowable())observe((ImageView)p.thisObject);
            }
        });
        // Clock computes an EXACT seconds width before it calls TextView.onMeasure.
        // Apply the font and invalidate its cache at the outer measurement boundary.
        Api102Runtime.findAndHookMethod(Reflect.findClass("com.android.systemui.statusbar.policy.Clock",loader),
            "onMeasure",int.class,int.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) {
                    TextView clock=(TextView)p.thisObject;
                    measureClock(clock,(Integer)p.args[0],(Integer)p.args[1]);
                }
            });
    }
    static void observe(ImageView image) {
        Drawable drawable=image.getDrawable();
        if(drawable==null)return;
        boolean battery=drawable.getClass().getName().equals(
            "com.oplus.systemui.statusbar.pipeline.battery.ui.drawable.HorizontalBatteryContentDrawable");
        boolean dynamicSignal=drawable.getClass().getName().equals("com.android.settingslib.graph.SignalDrawable")
            && Boolean.FALSE.equals(IosStatusBarHook.signalOwner(image));
        if(!(drawable instanceof IosIconDrawable.Replacement) && !battery && !dynamicSignal) {
            restore(image);images.remove(image);return;
        }
        ViewGroup.LayoutParams layout=image.getLayoutParams();
        if(layout==null)return;
        images.putIfAbsent(image,new Size(layout.width,layout.height,image.getScaleType(),
            layout instanceof FrameLayout.LayoutParams frame?frame.gravity:0));
        if(!IosStatusIcons.active(ShadeHook.snapshot())) { restore(image);return; }
        if(image.getScaleType()!=ImageView.ScaleType.FIT_CENTER)image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        boolean gravityChanged=false;
        if(layout instanceof FrameLayout.LayoutParams frame) {
            int gravity=(frame.gravity&~Gravity.VERTICAL_GRAVITY_MASK)|Gravity.CENTER_VERTICAL;
            gravityChanged=frame.gravity!=gravity;frame.gravity=gravity;
        }
        float density=image.getResources().getDisplayMetrics().density;
        int width=Math.round((battery?30:20)*density),height=Math.round((battery?16:20)*density);
        if(layout.width!=width || layout.height!=height || gravityChanged) {
            layout.width=width;layout.height=height;image.setLayoutParams(layout);
        }
    }
    private static void restore(ImageView image) {
        Size nativeSize=images.get(image);ViewGroup.LayoutParams layout=image.getLayoutParams();
        if(nativeSize!=null) {
            if(image.getScaleType()!=nativeSize.scaleType)image.setScaleType(nativeSize.scaleType);
            if(layout!=null) {
                boolean changed=layout.width!=nativeSize.width || layout.height!=nativeSize.height;
                if(layout instanceof FrameLayout.LayoutParams frame) {
                    changed|=frame.gravity!=nativeSize.gravity;frame.gravity=nativeSize.gravity;
                }
                if(changed) { layout.width=nativeSize.width;layout.height=nativeSize.height;image.setLayoutParams(layout); }
            }
        }
    }
    private static void measureClock(TextView clock,int widthSpec,int heightSpec) {
        if(!IosStatusIcons.active(ShadeHook.snapshot())) { applyClock(clock);return; }
        float density=clock.getResources().getDisplayMetrics().density;
        boolean seconds=Reflect.getBooleanField(clock,"mShowSeconds");
        ClockMeasure last=measurements.get(clock);
        if(last!=null && last.width==widthSpec && last.height==heightSpec && last.density==density
                && last.seconds==seconds && clock.getTypeface()==CLOCK_TYPE
                && Math.abs(last.pixels-clock.getTextSize())<.1f
                && Math.abs(last.textWidth-Layout.getDesiredWidth(clock.getText(),clock.getPaint()))<.1f)return;
        applyClock(clock);fitClock(clock,widthSpec,heightSpec);
        measurements.put(clock,new ClockMeasure(widthSpec,heightSpec,density,clock.getTextSize(),
            Layout.getDesiredWidth(clock.getText(),clock.getPaint()),seconds));
    }
    private static void applyClock(TextView clock) {
        measurements.remove(clock);
        boolean active=IosStatusIcons.active(ShadeHook.snapshot());
        Font font=clocks.get(clock);
        if(active) {
            if(font==null || !font.applied)clocks.put(clock,new Font(clock.getTextSize(),clock.getTypeface(),true));
            float pixels=17*clock.getResources().getDisplayMetrics().density;
            setClockFont(clock,pixels,CLOCK_TYPE);
        } else {
            if(font!=null && font.applied) {
                setClockFont(clock,font.pixels,font.typeface);
            }
            clocks.put(clock,new Font(clock.getTextSize(),clock.getTypeface(),false));
        }
    }
    private static void setClockFont(TextView clock,float pixels,Typeface face) {
        boolean changed=Math.abs(clock.getTextSize()-pixels)>.1f || clock.getTypeface()!=face;
        if(!changed)return;
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX,pixels);
        clock.setTypeface(face);
        Reflect.setIntField(clock,"mSecondsFixedWidth",0);
    }
    private static void fitClock(TextView clock,int widthSpec,int heightSpec) {
        int availableWidth=View.MeasureSpec.getMode(widthSpec)==View.MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE:
            Math.max(1,View.MeasureSpec.getSize(widthSpec)-clock.getCompoundPaddingLeft()-clock.getCompoundPaddingRight());
        int availableHeight=View.MeasureSpec.getMode(heightSpec)==View.MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE:
            Math.max(1,View.MeasureSpec.getSize(heightSpec)-clock.getCompoundPaddingTop()-clock.getCompoundPaddingBottom());
        // Measure the actual styled/localized text; seconds mode also reserves its widest digits.
        for(int attempt=0;attempt<3;attempt++) {
            float width=Layout.getDesiredWidth(clock.getText(),clock.getPaint());
            if(Reflect.getBooleanField(clock,"mShowSeconds"))width=Math.max(width,
                (Integer)Reflect.callMethod(clock,"computeSecondsFixedLayoutWidth")
                    -clock.getCompoundPaddingLeft()-clock.getCompoundPaddingRight());
            var metrics=clock.getPaint().getFontMetricsInt();
            int height=clock.getIncludeFontPadding()?metrics.bottom-metrics.top:metrics.descent-metrics.ascent;
            float factor=Math.min(1f,Math.min(availableWidth/Math.max(1f,width),availableHeight/(float)Math.max(1,height)));
            if(factor>=1f)break;
            setClockFont(clock,Math.max(1f,clock.getTextSize()*factor-.25f),CLOCK_TYPE);
        }
    }
    static void refresh() {
        ArrayList<ImageView> imageViews;ArrayList<TextView> clockViews;
        synchronized(images){imageViews=new ArrayList<>(images.keySet());}
        synchronized(clocks){clockViews=new ArrayList<>(clocks.keySet());}
        for(ImageView image:imageViews)image.post(()->observe(image));
        for(TextView clock:clockViews)clock.post(()->{applyClock(clock);clock.requestLayout();});
    }
    private IosStatusBarMetrics() { }
}
