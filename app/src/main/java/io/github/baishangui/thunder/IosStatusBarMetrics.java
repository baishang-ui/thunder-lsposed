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
    private record Spacing(int left,int top,int right,int bottom,int width,int height,
        int marginLeft,int marginTop,int marginRight,int marginBottom,int start,int end) { }
    private static final Map<View,Integer> overlayOffsets=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View,Spacing> spacing=Collections.synchronizedMap(new WeakHashMap<>());
    private record Metadata(float pixels,Typeface face,int gravity,int layoutGravity) { }
    private static final Typeface LABEL_TYPE=Typeface.create("sans-serif",Typeface.BOLD);
    private static final Map<TextView,Metadata> metadata=Collections.synchronizedMap(new WeakHashMap<>());
    private record Font(float pixels,Typeface typeface,boolean applied) { }
    private record ClockMeasure(int width,int height,float density,float pixels,float textWidth,boolean seconds) { }
    private static final Map<TextView,ClockMeasure> measurements=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ImageView,Size> images=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TextView,Font> clocks=Collections.synchronizedMap(new WeakHashMap<>());
    static void install(ClassLoader loader) {
        Api102Runtime.findAndHookMethod(ImageView.class,"setImageDrawable",Drawable.class,new HookCallback() {
            @Override protected void afterHookedMethod(Param p) {
                if(!p.hasThrowable()) {
                    ImageView image=(ImageView)p.thisObject;
                    IosStatusBarHook.observeLoaded(image);observe(image);
                }
            }
        });
        Class<?> legacy=Reflect.findClass("com.android.systemui.statusbar.StatusBarIconView",loader);
        Api102Runtime.findAndHookMethod(legacy,"onDraw",android.graphics.Canvas.class,legacyScale("mIconScale"));
        Api102Runtime.findAndHookMethod(legacy,"onMeasure",int.class,int.class,legacyScale("mScaleToFitNewIconSize"));
        Api102Runtime.findAndHookMethod(Reflect.findClass("com.android.systemui.statusbar.phone.StatusIconContainer",loader),
            "onMeasure",int.class,int.class,new HookCallback() {
                @Override protected void beforeHookedMethod(Param p) { prepareChildren((ViewGroup)p.thisObject,0); }
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
    private static void prepareChildren(ViewGroup group,int depth) {
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child instanceof ImageView image){IosStatusBarHook.observeResource(image);observe(image);}
            else if(child instanceof ViewGroup nested && depth<6)prepareChildren(nested,depth+1);
            else prepareAccessory(child);
        }
        if(IosStatusIcons.active(ShadeHook.snapshot()))arrangeOverlays(group);
    }
    private static void prepareAccessory(View view) {
        if(!IosStatusIcons.active(ShadeHook.snapshot()) || IosStatusBarHook.signalOwner(view)==null)return;
        String resource=IosStatusBarHook.resourceName(view.getResources(),view.getId());
        String name=resource.substring(resource.lastIndexOf('/')+1);
        if(name.equals("mobile_roaming_space") || name.equals("wifi_signal_spacer") || name.equals("wifi_airplane_spacer")) {
            normalizeSpacing(view,false);
            ViewGroup.LayoutParams layout=view.getLayoutParams();
            if(layout!=null && layout.width!=0){layout.width=0;view.setLayoutParams(layout);}return;
        }
        if(!(view instanceof TextView label) || !(name.equals("wifi_left_tv") || name.equals("mobile_type_text") || name.equals("mobile_roaming_text")))return;
        ViewGroup.LayoutParams layout=label.getLayoutParams();if(layout==null)return;
        if(!metadata.containsKey(label))metadata.put(label,new Metadata(label.getTextSize(),label.getTypeface(),label.getGravity(),
            layout instanceof FrameLayout.LayoutParams frame?frame.gravity:0));
        normalizeSpacing(label,false);
        float density=label.getResources().getDisplayMetrics().density;
        if(Math.abs(label.getTextSize()-10*density)>.1f)label.setTextSize(TypedValue.COMPLEX_UNIT_PX,10*density);
        if(label.getTypeface()!=LABEL_TYPE)label.setTypeface(LABEL_TYPE);
        if(label.getGravity()!=Gravity.CENTER)label.setGravity(Gravity.CENTER);
        int width=(int)Math.ceil(Layout.getDesiredWidth(label.getText(),label.getPaint())+3*density),height=Math.round(20*density);
        if(layout.width!=width || layout.height!=height){layout.width=width;layout.height=height;label.setLayoutParams(layout);}
    }
    private static void arrangeOverlays(ViewGroup group) {
        if(!(group instanceof FrameLayout) || IosStatusBarHook.signalOwner(group)==null)return;
        ArrayList<View> participants=new ArrayList<>();
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if(child instanceof ImageView image && image.getVisibility()==View.VISIBLE
                && image.getDrawable() instanceof IosIconDrawable.Replacement replacement && replacement.symbol().row()==0)
                participants.add(image);
            else if(child instanceof TextView label && metadata.containsKey(label) && label.getVisibility()==View.VISIBLE && label.getText().length()>0)
                participants.add(label);
        }
        participants.sort(java.util.Comparator.comparingInt(view->{
            if(!(view instanceof ImageView image))return 1;
            var kind=((IosIconDrawable.Replacement)image.getDrawable()).symbol().kind();
            return switch(kind){case WIFI,CELLULAR,NO_SERVICE -> 0;case LABEL -> 1;default -> 2;};
        }));
        int offset=0;
        for(View image:participants) {
            if(!(image.getLayoutParams() instanceof FrameLayout.LayoutParams frame))continue;
            overlayOffsets.put(image,offset);
            int gravity=Gravity.START|Gravity.CENTER_VERTICAL;
            if(frame.getMarginStart()!=offset || frame.getMarginEnd()!=0 || frame.gravity!=gravity) {
                frame.setMarginStart(offset);frame.setMarginEnd(0);frame.gravity=gravity;image.setLayoutParams(frame);
            }
            offset+=frame.width;
        }
    }
    private static HookCallback legacyScale(String field) {
        return new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                ImageView image=(ImageView)p.thisObject;
                if(!IosStatusIcons.active(ShadeHook.snapshot()) || !(image.getDrawable() instanceof IosIconDrawable.Replacement)
                        || Reflect.getObjectField(image,"mNotification")!=null)return;
                observe(image);
                p.setObjectExtra(field,Reflect.getObjectField(image,field));
                Reflect.setObjectField(image,field,1f);
            }
            @Override protected void afterHookedMethod(Param p) {
                Object original=p.getObjectExtra(field);
                if(original!=null)Reflect.setObjectField(p.thisObject,field,original);
            }
        };
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
        normalizeSpacing(image,false);
        View root=statusRoot(image);
        if(root!=null && root!=image) {
            View ancestor=image.getParent() instanceof View parent?parent:null;
            for(int depth=0;depth<6 && ancestor!=null;depth++) {
                normalizeSpacing(ancestor,true);
                if(ancestor==root)break;
                ancestor=ancestor.getParent() instanceof View parent?parent:null;
            }
        }
        ImageView.ScaleType scale=(battery||dynamicSignal)?ImageView.ScaleType.FIT_XY:ImageView.ScaleType.FIT_CENTER;
        if(image.getScaleType()!=scale)image.setScaleType(scale);
        boolean gravityChanged=false;
        if(layout instanceof FrameLayout.LayoutParams frame) {
            int gravity=overlayOffsets.containsKey(image)?Gravity.START|Gravity.CENTER_VERTICAL:
                (frame.gravity&~Gravity.VERTICAL_GRAVITY_MASK)|Gravity.CENTER_VERTICAL;
            gravityChanged=frame.gravity!=gravity;frame.gravity=gravity;
        }
        float density=image.getResources().getDisplayMetrics().density;
        if(drawable instanceof IosIconDrawable.Replacement replacement && replacement.density(density)) {
            image.setImageDrawable(null);image.setImageDrawable(drawable);
        }
        float cell=drawable instanceof IosIconDrawable.Replacement replacement?IosIconDrawable.cellWidth(replacement.symbol()):battery?30:21;
        int width=Math.round(cell*density),height=Math.round(IosIconDrawable.cellHeight()*density);
        if(layout.width!=width || layout.height!=height || gravityChanged) {
            layout.width=width;layout.height=height;image.setLayoutParams(layout);
        }
    }
    private static View statusRoot(View image) {
        View view=image;
        for(int depth=0;depth<6 && view!=null;depth++) {
            for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass()) {
                String name=type.getName();
                if(name.equals("com.android.systemui.statusbar.StatusBarIconView")
                    || name.equals("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView")
                    || name.equals("com.android.systemui.statusbar.pipeline.wifi.ui.view.ModernStatusBarWifiView")
                    || name.equals("com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView")
                    || name.equals("com.oplus.systemui.statusbar.phone.dynamic.icon.OplusStatusBarIconView")
                    || name.equals("com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView"))return view;
            }
            view=view.getParent() instanceof View parent?parent:null;
        }
        return null;
    }
    private static void normalizeSpacing(View view,boolean container) {
        ViewGroup.LayoutParams layout=view.getLayoutParams();if(layout==null)return;
        ViewGroup.MarginLayoutParams margin=layout instanceof ViewGroup.MarginLayoutParams m?m:null;
        spacing.putIfAbsent(view,new Spacing(view.getPaddingLeft(),view.getPaddingTop(),view.getPaddingRight(),view.getPaddingBottom(),
            layout.width,layout.height,margin==null?0:margin.leftMargin,margin==null?0:margin.topMargin,
            margin==null?0:margin.rightMargin,margin==null?0:margin.bottomMargin,
            margin==null?0:margin.getMarginStart(),margin==null?0:margin.getMarginEnd()));
        if(view.getPaddingLeft()!=0 || view.getPaddingTop()!=0 || view.getPaddingRight()!=0 || view.getPaddingBottom()!=0)view.setPadding(0,0,0,0);
        boolean changed=false;
        if(margin!=null && !overlayOffsets.containsKey(view)) {
            changed=margin.leftMargin!=0 || margin.rightMargin!=0 || margin.topMargin!=0 || margin.bottomMargin!=0 || margin.getMarginStart()!=0 || margin.getMarginEnd()!=0;
            margin.leftMargin=margin.rightMargin=margin.topMargin=margin.bottomMargin=0;
            margin.setMarginStart(0);margin.setMarginEnd(0);
        }
        // Keep native match-parent constraints; fixed wrappers must follow their resized content.
        if(container) {
            if(layout.width>=0){layout.width=ViewGroup.LayoutParams.WRAP_CONTENT;changed=true;}
            int minimum=Math.round(IosIconDrawable.cellHeight()*view.getResources().getDisplayMetrics().density);
            if(layout.height>=0 && layout.height<minimum){layout.height=minimum;changed=true;}
        }
        if(changed)view.setLayoutParams(layout);
    }
    private static void restoreSpacing(View view) {
        overlayOffsets.remove(view);
        Spacing state=spacing.remove(view);if(state==null)return;
        view.setPadding(state.left,state.top,state.right,state.bottom);
        ViewGroup.LayoutParams layout=view.getLayoutParams();if(layout==null)return;
        layout.width=state.width;layout.height=state.height;
        if(layout instanceof ViewGroup.MarginLayoutParams m) {
            m.leftMargin=state.marginLeft;m.topMargin=state.marginTop;m.rightMargin=state.marginRight;m.bottomMargin=state.marginBottom;
            m.setMarginStart(state.start);m.setMarginEnd(state.end);
        }
        view.setLayoutParams(layout);
    }
    private static void restore(ImageView image) {
        if(!images.containsKey(image) && !spacing.containsKey(image))return;
        restoreSpacing(image);
        View root=statusRoot(image);
        View ancestor=image.getParent() instanceof View parent?parent:null;
        for(int depth=0;root!=null && root!=image && depth<6 && ancestor!=null;depth++) {
            restoreSpacing(ancestor);if(ancestor==root)break;
            ancestor=ancestor.getParent() instanceof View parent?parent:null;
        }
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
    static void reset(ImageView image) { restore(image);images.remove(image); }
    static void refresh() {
        if(!IosStatusIcons.active(ShadeHook.snapshot())) {
            ArrayList<View> styled;
            synchronized(spacing){styled=new ArrayList<>(spacing.keySet());}
            for(View view:styled)view.post(()->restoreSpacing(view));
            ArrayList<TextView> labels;
            synchronized(metadata){labels=new ArrayList<>(metadata.keySet());}
            for(TextView label:labels)label.post(()->{
                Metadata state=metadata.remove(label);if(state==null)return;
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX,state.pixels);label.setTypeface(state.face);label.setGravity(state.gravity);
                if(label.getLayoutParams() instanceof FrameLayout.LayoutParams frame){frame.gravity=state.layoutGravity;label.setLayoutParams(frame);}
            });
        }
        ArrayList<ImageView> imageViews;ArrayList<TextView> clockViews;
        synchronized(images){imageViews=new ArrayList<>(images.keySet());}
        synchronized(clocks){clockViews=new ArrayList<>(clocks.keySet());}
        for(ImageView image:imageViews)image.post(()->{
            observe(image);
            // ImageView caches the drawable's intrinsic dimensions. Refresh that cache on style changes.
            Drawable drawable=image.getDrawable();
            if(drawable instanceof IosIconDrawable.Replacement) {
                image.setImageDrawable(null);image.setImageDrawable(drawable);
            }
        });
        for(TextView clock:clockViews)clock.post(()->{applyClock(clock);clock.requestLayout();});
    }
    private IosStatusBarMetrics() { }
}
