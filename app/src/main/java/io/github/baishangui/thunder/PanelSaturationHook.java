package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import java.lang.ref.WeakReference;

/** Adjust captured background recordings on both native render paths, never foreground UI. */
final class PanelSaturationHook {
    private static final String STATE="thunder.panel-saturation-state";
    private static final String FILTER="thunder.panel-saturation-filter";

    static void install(ClassLoader loader) throws Throwable {
        Class<?> blur=Reflect.findClass("com.oplusos.systemui.common.blurability.BlurConfig",loader);
        Api102Runtime.findAndHookMethod("com.oplusos.systemui.common.blurability.platformblur.PlatformBlurDrawable",loader,
            "applyBlurConfig",blur,float.class,new HookCallback() {
                @Override protected void afterHookedMethod(Param p) {
                    if(p.hasThrowable()||p.getResult()==null)return;
                    track(p.getResult(),p.args[0],((Number)p.args[1]).floatValue());
                }
            });
        Class<?> base=Reflect.findClass("com.oplus.posteffect.drawable.BlendDrawable",loader);
        Api102Runtime.findAndHookMethod(base,"onDrawContent",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Tracking tracking=(Tracking)Reflect.getAdditionalInstanceField(p.thisObject,STATE);
                if(tracking==null)return;
                float percent=amount(tracking.source.get(),tracking.progress);
                if(percent==100)return;
                Paint paint=(Paint)Reflect.callMethod(p.thisObject,"getDrawableShaderPaint");
                ColorFilter original=paint.getColorFilter();
                Paint bitmap=(Paint)Reflect.callMethod(p.thisObject,"getPaint");
                ColorFilter bitmapOriginal=bitmap.getColorFilter();
                if(tracking.filter==null)tracking.filter=new FilterState();
                FilterState state=tracking.filter;
                state.update(percent);
                if(original==null&&bitmapOriginal==null) {
                    p.setObjectExtra(FILTER,new Object[]{paint,original,bitmap,bitmapOriginal});
                    paint.setColorFilter(state.filter);
                    bitmap.setColorFilter(state.filter);
                } else {
                    // Preserve an arbitrary native filter; saturation runs after its output.
                    Canvas canvas=(Canvas)p.args[0];
                    p.setObjectExtra(FILTER,new Object[]{canvas,canvas.saveLayer(null,state.layerPaint)});
                }
                HookConfig setting=ShadeHook.snapshot();
                if(tracking.reported!=setting) {
                    tracking.reported=setting;
                    ShadeHook.observed("panel-saturation",setting.panelSaturation+"%; follows blur amount");
                }
            }
            @Override protected void afterHookedMethod(Param p) {
                Object saved=p.getObjectExtra(FILTER);
                if(saved instanceof Object[]) {
                    Object[] values=(Object[])saved;
                    if(values[0] instanceof Paint) {
                        ((Paint)values[0]).setColorFilter((ColorFilter)values[1]);
                        ((Paint)values[2]).setColorFilter((ColorFilter)values[3]);
                    }
                    else ((Canvas)values[0]).restoreToCount((Integer)values[1]);
                }
            }
        });
        // Cover both shader and temporary bitmap recordings, including hardware RenderNode.
        Api102Runtime.deoptimizeCallers(Reflect.findClass("com.oplus.posteffect.drawable.BaseDrawable",loader),"access$drawContent","draw");
    }

    static void track(Object drawable,Object blur,float progress) {
        Tracking tracking=(Tracking)Reflect.getAdditionalInstanceField(drawable,STATE);
        if(!ShadeHook.isPanelBackground(blur)) {
            if(tracking==null)return;
            Reflect.removeAdditionalInstanceField(drawable,STATE);
            if(tracking.amount==100f)return;
        } else {
            if(tracking==null) {
                tracking=new Tracking(blur);
                Reflect.setAdditionalInstanceField(drawable,STATE,tracking);
            } else if(tracking.source.get()!=blur)tracking.source=new WeakReference<>(blur);
            tracking.progress=progress;
            float percent=amount(blur,progress);
            if(tracking.amount==percent)return;
            tracking.amount=percent;
        }
        // Re-record even if only saturation changed and all native blur params stayed identical.
        Reflect.setObjectField(drawable,"contentDirty",true);
        ((Drawable)drawable).invalidateSelf();
    }

    private static float amount(Object blur,float progress) {
        HookConfig c=ShadeHook.snapshot();
        if(!c.enabled||!c.panelSaturationEnabled||!ShadeHook.isPanelBackground(blur))return 100f;
        float f=Float.isFinite(progress)?Math.max(0f,Math.min(1f,progress)):0f;
        // Native scaled captures remain opaque even at the first visible frame.
        // Start at identity and follow the same blur amount in either direction.
        f=f*f*(3f-2f*f);
        return 100f+(c.panelSaturation-100f)*f;
    }

    private static final class Tracking {
        WeakReference<Object> source;
        float progress,amount=Float.NaN;
        HookConfig reported;
        FilterState filter;
        Tracking(Object source){this.source=new WeakReference<>(source);}
    }

    private static final class FilterState {
        float percent=Float.NaN;
        ColorFilter filter;
        final ColorMatrix matrix=new ColorMatrix();
        final Paint layerPaint=new Paint();
        void update(float percent) {
            if(this.percent==percent)return;
            this.percent=percent;
            matrix.setSaturation(percent/100f);
            filter=new ColorMatrixColorFilter(matrix);
            layerPaint.setColorFilter(filter);
        }
    }
}
