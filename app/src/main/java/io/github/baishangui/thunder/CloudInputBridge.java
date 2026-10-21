package io.github.baishangui.thunder;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Region;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.IdentityHashMap;
import java.util.Map;

/** Gives a persistent Body window pointer ownership without moving native business Views. */
final class CloudInputBridge {
    private static final Map<View,Window> active=new IdentityHashMap<>();
    private View window;
    /** A closing capsule can only acquire pixels shared with its visible silhouette. */
    static Region capsuleRegion(Region visible,CloudMorphHook.Frame target,float offsetX,float offsetY) {
        Region result=new Region();
        if(target==null||visible.isEmpty())return result;
        float radius=Math.max(0,Math.min(target.radius(),Math.min(target.width(),target.height())/2f));
        Path path=new Path();path.addRoundRect(target.x()-offsetX,target.y()-offsetY,
            target.x()-offsetX+target.width(),target.y()-offsetY+target.height(),radius,radius,Path.Direction.CW);
        result.setPath(path,visible);return result;
    }
    /** Region equality includes holes and contours, not merely its enclosing bounds. */
    static boolean updateRegion(Region published,Region next) {
        if(published.equals(next))return false;
        published.set(next);return true;
    }
    private static final class Window {
        final WindowManager manager;
        final CloudInputWindowPolicy flags=new CloudInputWindowPolicy();
        Window(WindowManager manager){this.manager=manager;}
    }

    /** Called on the UI thread after the Body has installed its expanded touch region. */
    void sync(View nativeRoot,WindowManager manager,boolean own) {
        View target=own&&nativeRoot!=null&&nativeRoot.isAttachedToWindow()?nativeRoot.getRootView():null;
        if(window!=target)release();
        if(target==null)return;
        if(!(target.getLayoutParams() instanceof WindowManager.LayoutParams params)){release();return;}
        // The seedling window is STATUS_BAR_SUB_PANEL. Never change an Activity or a
        // status-bar window if a future host version supplies an unexpected fallback.
        if(params.type!=2017){release();return;}
        Window state=active.computeIfAbsent(target,key->new Window(manager));window=target;
        apply(target,state,params,state.flags.acquire(this,params.flags,params));
    }
    void release() {
        View target=window;window=null;if(target==null)return;
        Window state=active.get(target);if(state==null)return;
        try {
            if(target.getLayoutParams() instanceof WindowManager.LayoutParams params)
                apply(target,state,params,state.flags.release(this,params.flags,params));
            else state.flags.release(this,0,null);
        } finally {if(!state.flags.owned())active.remove(target);}
    }
    private static void apply(View target,Window state,WindowManager.LayoutParams params,int flags) {
        if(params.flags==flags)return;
        params.flags=flags;
        // Detached windows have no input channel; retain restored params for any remount.
        if(target.isAttachedToWindow())state.manager.updateViewLayout(target,params);
    }

    /** Outside dismissal uses window coordinates, never the card-content inverse fit. */
    static MotionEvent outsideToRoot(MotionEvent event,View body,View nativeRoot) {
        Matrix from=new Matrix(),to=new Matrix(),inverse=new Matrix(),map=new Matrix();
        body.transformMatrixToGlobal(from);nativeRoot.transformMatrixToGlobal(to);
        if(!to.invert(inverse))throw new IllegalStateException("Noninvertible native outside root");
        map.setConcat(inverse,from);return outsideToRoot(event,map);
    }
    static MotionEvent outsideToRoot(MotionEvent event,Matrix bodyToRoot) {
        MotionEvent forwarded=MotionEvent.obtain(event);
        forwarded.setAction(MotionEvent.ACTION_OUTSIDE);
        // Android can hide outside coordinates for a different UID. Preserve the native
        // root's (0,0,TOOL_TYPE_UNKNOWN) sentinel instead of turning it into a fake touch.
        if(event.getX()!=0f||event.getY()!=0f||event.getToolType(0)!=MotionEvent.TOOL_TYPE_UNKNOWN)
            forwarded.transform(bodyToRoot);
        return forwarded;
    }
}
