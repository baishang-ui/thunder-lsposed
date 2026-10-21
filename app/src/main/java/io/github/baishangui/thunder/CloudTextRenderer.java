package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Text-only effects inside native TextView.onDraw; no source View alpha/effect mutation. */
final class CloudTextRenderer {
    private static final String DRAW="thunder.cloud-text-draw";
    private static final int STEPS=32;
    private static final Map<TextView,Entry> texts=new WeakHashMap<>();
    private static final Map<View,Integer> roots=new WeakHashMap<>();
    private static boolean installed;
    private static Method nativeDraw;
    private final Runnable invalidate;
    private final IntSupplier duration;
    private final Supplier<Float> blur;
    private final Map<TextView,Entry> leases=new IdentityHashMap<>();
    private Map<TextView,String> slots=new IdentityHashMap<>();
    private Map<String,String> snapshots=new HashMap<>();
    private View root;

    CloudTextRenderer(Runnable invalidate) {
        this(invalidate,()->ShadeHook.snapshot().cloudAnimationDuration,()->null);
    }
    CloudTextRenderer(Runnable invalidate,IntSupplier duration,Supplier<Float> blur) {
        this.invalidate=invalidate;this.duration=duration;this.blur=blur;
    }
    static void install() {
        if(installed)return;
        Api102Runtime.findAndHookMethod(TextView.class,"onDraw",Canvas.class,new HookCallback() {
            @Override protected void beforeHookedMethod(Param p) {
                Draw draw=beginDraw((TextView)p.thisObject,(Canvas)p.args[0]);
                if(draw!=null){p.setObjectExtra(DRAW,draw);p.args[0]=draw.canvas;}
            }
            @Override protected void afterHookedMethod(Param p)throws Throwable {
                Object saved=p.getObjectExtra(DRAW);
                if(!(saved instanceof Draw draw))return;
                p.args[0]=draw.original;
                endDraw(draw,!p.hasThrowable());
            }
        });
        installed=true;
    }
    private static Method nativeDraw()throws NoSuchMethodException {
        if(nativeDraw==null)nativeDraw=TextView.class.getDeclaredMethod("onDraw",Canvas.class);
        return nativeDraw;
    }
    static boolean owns(View source) {
        for(Object current=source;current instanceof View view;current=view.getParent())
            if(roots.containsKey(view))return true;
        return false;
    }
    void bind(View next,boolean animateNew) {
        if(root!=next) {
            releaseRoot();root=next;
            if(next!=null)roots.put(next,roots.getOrDefault(next,0)+1);
        }
        Map<TextView,Entry> nextLeases=new IdentityHashMap<>();
        Map<String,String> nextSnapshots=new HashMap<>();
        Map<TextView,String> nextSlots=new IdentityHashMap<>();
        Map<Integer,Integer> ids=new HashMap<>();
        if(next!=null)collect(next,"",animateNew,nextLeases,nextSnapshots,nextSlots,ids);
        for(var lease:new ArrayList<>(leases.entrySet()))
            if(!nextLeases.containsKey(lease.getKey()))release(lease.getKey(),lease.getValue());
        leases.clear();leases.putAll(nextLeases);snapshots=nextSnapshots;slots=nextSlots;
    }
    void revision(){if(root!=null)bind(root,true);}
    boolean running() {
        long now=SystemClock.uptimeMillis();
        for(Entry entry:leases.values())if(entry.transition.running(now))return true;
        return false;
    }
    void clear() {
        for(var lease:new ArrayList<>(leases.entrySet()))release(lease.getKey(),lease.getValue());
        leases.clear();snapshots.clear();slots.clear();releaseRoot();root=null;
    }
    private void releaseRoot() {
        if(root==null)return;
        int count=roots.getOrDefault(root,0);
        if(count<=1)roots.remove(root);else roots.put(root,count-1);
    }
    private void release(TextView text,Entry entry) {
        entry.owners.remove(this);
        if(!entry.owners.isEmpty())return;
        text.removeTextChangedListener(entry);text.removeCallbacks(entry);
        if(texts.get(text)==entry)texts.remove(text);
        entry.clear();text.invalidate();
    }
    private void collect(View view,String path,boolean animateNew,Map<TextView,Entry> next,
            Map<String,String> nextSnapshots,Map<TextView,String> nextSlots,Map<Integer,Integer> ids) {
        if(view instanceof TextView text) {
            int id=text.getId(),ordinal=ids.getOrDefault(id,0);ids.put(id,ordinal+1);
            String slot=id==View.NO_ID?"path:"+path:"id:"+id+":"+ordinal;
            String value=text.getText().toString();
            Entry entry=texts.get(text);
            if(entry==null) {
                entry=new Entry(text);texts.put(text,entry);
                String previous=snapshots.get(slot);
                if(previous!=null)entry.transition.update(previous,SystemClock.uptimeMillis(),duration.getAsInt(),false);
                entry.transition.update(value,SystemClock.uptimeMillis(),duration.getAsInt(),animateNew||!snapshots.isEmpty());
                text.addTextChangedListener(entry);
            } else entry.transition.update(value,SystemClock.uptimeMillis(),duration.getAsInt(),false);
            entry.failed=false;entry.owners.put(this,true);next.put(text,entry);nextSnapshots.put(slot,value);nextSlots.put(text,slot);
            if(entry.transition.running(SystemClock.uptimeMillis()))entry.invalidateAndSchedule();
        }
        if(view instanceof ViewGroup group)
            for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),path+"/"+i,animateNew,next,nextSnapshots,nextSlots,ids);
    }
    private static final class Entry implements TextWatcher,Runnable {
        final WeakReference<TextView> source;
        final Map<CloudTextRenderer,Boolean> owners=new WeakHashMap<>();
        final CloudTextTransition transition=new CloudTextTransition();
        final RenderEffect[] effects=new RenderEffect[STEPS+1];
        RenderNode node;
        float effectRadius=-1;
        boolean queued,recording,failed;
        Entry(TextView source){this.source=new WeakReference<>(source);}
        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
        @Override public void onTextChanged(CharSequence s,int start,int before,int count) { }
        @Override public void afterTextChanged(Editable ignored) {
            TextView text=source.get();CloudTextRenderer owner=owner();
            if(text==null||owner==null)return;
            String value=text.getText().toString();
            for(CloudTextRenderer current:new ArrayList<>(owners.keySet())) {
                String slot=current.slots.get(text);if(slot!=null)current.snapshots.put(slot,value);
            }
            if(transition.update(value,SystemClock.uptimeMillis(),owner.duration.getAsInt(),false))
                if(failed)transition.finish();else invalidateAndSchedule();
        }
        CloudTextRenderer owner(){return owners.isEmpty()?null:owners.keySet().iterator().next();}
        void invalidateAndSchedule() {
            TextView text=source.get();if(text==null)return;
            try{updateNode(transition.sample(SystemClock.uptimeMillis()));}
            catch(Throwable error){disable(error);}
            text.invalidate();for(CloudTextRenderer owner:new ArrayList<>(owners.keySet()))owner.invalidate.run();
            if(!queued&&transition.running(SystemClock.uptimeMillis())){queued=true;text.postOnAnimation(this);}
        }
        void disable(Throwable error) {
            transition.finish();
            if(!failed)ShadeHook.frameworkFailure("cloud-text-render",error);
            failed=true;
            if(node!=null){try{node.setRenderEffect(null);node.setAlpha(1);}catch(Throwable ignored){}}
        }
        @Override public void run(){queued=false;invalidateAndSchedule();}
        void updateNode(float phase) {
            if(node==null)return;
            if(phase>=1f){node.setRenderEffect(null);node.setAlpha(1);return;}
            TextView text=source.get();CloudTextRenderer owner=owner();if(text==null||owner==null)return;
            Float configured=owner.blur.get();float radius=configured==null?
                Math.min(32f,10f*text.getResources().getDisplayMetrics().density):Math.max(0,configured);
            if(effectRadius!=radius){java.util.Arrays.fill(effects,null);effectRadius=radius;}
            int step=Math.max(0,Math.min(STEPS,Math.round((1f-phase)*STEPS)));
            if(step>0&&radius>0&&effects[step]==null) {
                float value=Math.max(.01f,radius*step/STEPS);
                effects[step]=RenderEffect.createBlurEffect(value,value,Shader.TileMode.CLAMP);
            }
            node.setRenderEffect(step==0||radius==0?null:effects[step]);node.setAlpha(phase);
        }
        void clear(){
            transition.finish();try{updateNode(1);}catch(Throwable error){disable(error);}
            // A parent display list may still reference this node until its next
            // native traversal. Keep that recording sharp instead of discarding it.
            node=null;java.util.Arrays.fill(effects,null);queued=false;
        }
    }
    static final class Draw {
        final Entry entry;
        final Canvas original,canvas;
        final int save;
        Draw(Entry entry,Canvas original,Canvas canvas,int save){this.entry=entry;this.original=original;this.canvas=canvas;this.save=save;}
    }
    /** Package visibility lets the Android probe exercise the actual interception without Xposed. */
    static Draw beginDraw(TextView text,Canvas original) {
        Entry entry=texts.get(text);
        if(entry==null||entry.failed||entry.recording||!entry.transition.running(SystemClock.uptimeMillis()))return null;
        float phase=entry.transition.sample(SystemClock.uptimeMillis());
        if(text.getWidth()<=0||text.getHeight()<=0)return null;
        if(!original.isHardwareAccelerated()) {
            int save=original.saveLayerAlpha(null,Math.round(255*phase));entry.recording=true;
            return new Draw(entry,original,original,save);
        }
        try {
            if(entry.node==null){entry.node=new RenderNode("Cloud TextView");entry.node.setClipToBounds(false);}
            entry.node.setPosition(0,0,text.getWidth(),text.getHeight());
            entry.updateNode(phase);
            Canvas canvas=entry.node.beginRecording(text.getWidth(),text.getHeight());entry.recording=true;
            return new Draw(entry,original,canvas,-1);
        } catch(Throwable error) {
            // Fall back to the unmodified native draw if this renderer is unavailable.
            if(entry.node!=null){try{entry.node.endRecording();}catch(Throwable ignored){}}
            entry.disable(error);entry.recording=false;return null;
        }
    }
    static void endDraw(Draw draw,boolean success)throws Throwable {
        try {
            if(draw.save>=0)draw.original.restoreToCount(draw.save);
            else {draw.entry.node.endRecording();if(success)draw.original.drawRenderNode(draw.entry.node);}
        } catch(Throwable error) {
            if(draw.save<0&&draw.entry.node!=null){try{draw.entry.node.endRecording();}catch(Throwable ignored){}}
            draw.entry.disable(error);
            if(success&&draw.save<0) {
                // The native method already completed into our node. If its
                // backend cannot composite, replay that exact base onDraw once
                // on the original Canvas, without a subclass/hook recursion.
                TextView text=draw.entry.source.get();
                if(text!=null)Api102Runtime.invokeOriginal(nativeDraw(),text,draw.original);
            }
        } finally {draw.entry.recording=false;}
    }
}
