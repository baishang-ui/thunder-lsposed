package io.github.baishangui.thunder;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Path;
import android.text.Layout;
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
    private Map<String,CloudTextTransition> snapshots=new HashMap<>();
    private View root;
    private Object sceneRevision;
    private long sceneStarted;
    private int sceneDuration;

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
        CloudTextHook.adopt(next);
        Map<TextView,Entry> nextLeases=new IdentityHashMap<>();
        Map<String,CloudTextTransition> nextSnapshots=new HashMap<>();
        Map<TextView,String> nextSlots=new IdentityHashMap<>();
        Map<Integer,Integer> ids=new HashMap<>();
        if(next!=null)collect(next,"",animateNew,nextLeases,nextSnapshots,nextSlots,ids);
        for(var lease:new ArrayList<>(leases.entrySet()))
            if(!nextLeases.containsKey(lease.getKey()))release(lease.getKey(),lease.getValue());
        leases.clear();leases.putAll(nextLeases);snapshots=nextSnapshots;slots=nextSlots;
    }
    void revision(){if(root!=null)bind(root,true);}
    /** Scene changes reveal all text, including unchanged values; model revisions only diff characters. */
    void reveal(Object revision) {
        if(java.util.Objects.equals(sceneRevision,revision))return;
        sceneRevision=revision;sceneStarted=SystemClock.uptimeMillis();sceneDuration=Math.max(0,duration.getAsInt());
        if(root!=null)bind(root,false);
        for(Entry entry:leases.values()){entry.transition.reveal(sceneStarted,sceneDuration);entry.invalidateAndSchedule();}
    }
    boolean running() {
        long now=SystemClock.uptimeMillis();
        for(Entry entry:leases.values())if(entry.transition.running(now))return true;
        return false;
    }
    void clear() {
        for(var lease:new ArrayList<>(leases.entrySet()))release(lease.getKey(),lease.getValue());
        leases.clear();snapshots.clear();slots.clear();releaseRoot();root=null;sceneRevision=null;sceneDuration=0;
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
            Map<String,CloudTextTransition> nextSnapshots,Map<TextView,String> nextSlots,Map<Integer,Integer> ids) {
        if(view instanceof TextView text) {
            int id=text.getId(),ordinal=ids.getOrDefault(id,0);ids.put(id,ordinal+1);
            String slot=id==View.NO_ID?"path:"+path:"id:"+id+":"+ordinal;
            String value=text.getText().toString();
            Entry entry=texts.get(text);
            if(entry==null) {
                entry=new Entry(text);texts.put(text,entry);
                CloudTextTransition previous=snapshots.get(slot);
                if(previous!=null)entry.transition.copyFrom(previous);
                entry.transition.update(value,SystemClock.uptimeMillis(),duration.getAsInt(),animateNew||!snapshots.isEmpty());
                if(sceneRevision!=null&&SystemClock.uptimeMillis()-sceneStarted<sceneDuration)
                    entry.transition.reveal(sceneStarted,sceneDuration);
                text.addTextChangedListener(entry);
            } else entry.transition.update(value,SystemClock.uptimeMillis(),duration.getAsInt(),false);
            entry.failed=false;entry.owners.put(this,true);next.put(text,entry);nextSnapshots.put(slot,entry.transition);nextSlots.put(text,slot);
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
        RenderNode node,composite;
        Bitmap bitmap;
        Canvas bitmapCanvas;
        final Map<Integer,RenderNode> glyphNodes=new HashMap<>();
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
                String slot=current.slots.get(text);if(slot!=null)current.snapshots.put(slot,transition);
            }
            if(transition.update(value,SystemClock.uptimeMillis(),owner.duration.getAsInt(),false))
                if(failed)transition.finish();else invalidateAndSchedule();
        }
        CloudTextRenderer owner(){return owners.isEmpty()?null:owners.keySet().iterator().next();}
        void invalidateAndSchedule() {
            TextView text=source.get();if(text==null)return;
            try{updateNodes(SystemClock.uptimeMillis());}
            catch(Throwable error){disable(error);}
            text.invalidate();for(CloudTextRenderer owner:new ArrayList<>(owners.keySet()))owner.invalidate.run();
            if(!queued&&transition.running(SystemClock.uptimeMillis())){queued=true;text.postOnAnimation(this);}
        }
        void disable(Throwable error) {
            transition.finish();
            if(!failed)ShadeHook.frameworkFailure("cloud-text-render",error);
            failed=true;
            try{updateNodes(SystemClock.uptimeMillis());}catch(Throwable ignored){}
        }
        @Override public void run(){queued=false;invalidateAndSchedule();}
        float radius() {
            TextView text=source.get();CloudTextRenderer owner=owner();if(text==null||owner==null)return 0;
            Float configured=owner.blur.get();return configured==null?
                Math.min(32f,10f*text.getResources().getDisplayMetrics().density):Math.max(0,configured);
        }
        void effect(RenderNode target,float phase,float roll) {
            float radius=radius();
            if(effectRadius!=radius){java.util.Arrays.fill(effects,null);effectRadius=radius;}
            int step=Math.max(0,Math.min(STEPS,Math.round((1f-phase)*STEPS)));
            if(step>0&&radius>0&&effects[step]==null) {
                float value=Math.max(.01f,radius*step/STEPS);
                effects[step]=RenderEffect.createBlurEffect(value,value,Shader.TileMode.DECAL);
            }
            TextView text=source.get();
            target.setRenderEffect(step==0||radius==0?null:effects[step]);target.setAlpha(phase);
            target.setTranslationY(text==null?0:roll*text.getTextSize()*.65f);
        }
        void updateNodes(long now) {
            if(node!=null)effect(node,transition.revealing(now)&&transition.count()>0?transition.sample(0,now):1,0);
            for(var item:glyphNodes.entrySet()) {
                int i=item.getKey();effect(item.getValue(),i<transition.count()?transition.sample(i,now):1,
                    i<transition.count()?transition.roll(i,now):0);
            }
        }
        void clear(){
            transition.finish();try{updateNodes(SystemClock.uptimeMillis());}catch(Throwable error){disable(error);}
            // Cached native parent display lists may still reference these nodes.
            node=composite=null;bitmap=null;bitmapCanvas=null;glyphNodes.clear();java.util.Arrays.fill(effects,null);queued=false;
        }
    }
    private record Part(int index,Path clip,float phase,float roll) { }
    private static ArrayList<Part> parts(TextView text,Entry entry,long now) {
        Layout layout=text.getLayout();if(layout==null)return null;
        ArrayList<Part> parts=new ArrayList<>();
        for(int i=0;i<entry.transition.count();i++) {
            float phase=entry.transition.sample(i,now);if(phase>=1)continue;
            int start=entry.transition.start(i),end=entry.transition.end(i);
            if(end>layout.getText().length())continue;
            Path clip=new Path();layout.getSelectionPath(start,end,clip);
            clip.offset(text.getTotalPaddingLeft(),text.getTotalPaddingTop());
            parts.add(new Part(i,clip,phase,entry.transition.roll(i,now)));
        }
        return parts;
    }

    static final class Draw {
        final Entry entry;
        final Canvas original,canvas;
        final int save;
        final boolean reveal;
        final long time;
        ArrayList<Part> parts;
        Draw(Entry entry,Canvas original,Canvas canvas,int save,boolean reveal,long time){
            this.entry=entry;this.original=original;this.canvas=canvas;this.save=save;this.reveal=reveal;this.time=time;
        }
    }
    /** Package visibility lets the Android probe exercise the actual interception without Xposed. */
    static Draw beginDraw(TextView text,Canvas original) {
        Entry entry=texts.get(text);long now=SystemClock.uptimeMillis();
        if(entry==null||entry.failed||entry.recording||!entry.transition.running(now))return null;
        if(text.getWidth()<=0||text.getHeight()<=0)return null;
        try {
            boolean reveal=entry.transition.revealing(now);
            if(!original.isHardwareAccelerated()) {
                if(entry.bitmap==null||entry.bitmap.getWidth()!=text.getWidth()||entry.bitmap.getHeight()!=text.getHeight()) {
                    entry.bitmap=Bitmap.createBitmap(text.getWidth(),text.getHeight(),Bitmap.Config.ARGB_8888);
                    entry.bitmapCanvas=new Canvas(entry.bitmap);
                }
                entry.bitmap.eraseColor(Color.TRANSPARENT);entry.recording=true;
                return new Draw(entry,original,entry.bitmapCanvas,0,reveal,now);
            }
            if(entry.node==null){entry.node=new RenderNode("Cloud native text");entry.node.setClipToBounds(false);}
            entry.node.setPosition(0,0,text.getWidth(),text.getHeight());entry.updateNodes(now);
            Canvas canvas=entry.node.beginRecording(text.getWidth(),text.getHeight());entry.recording=true;
            return new Draw(entry,original,canvas,-1,reveal,now);
        } catch(Throwable error) {
            if(entry.node!=null){try{entry.node.endRecording();}catch(Throwable ignored){}}
            entry.disable(error);entry.recording=false;return null;
        }
    }
    private static void drawCharacters(Draw draw)throws Throwable {
        Entry entry=draw.entry;TextView text=entry.source.get();if(text==null)return;
        Canvas canvas=draw.original;
        if(draw.save>=0) {
            int stable=canvas.save();
            try{for(Part part:draw.parts)canvas.clipOutPath(part.clip);canvas.drawBitmap(entry.bitmap,0,0,null);}
            finally{canvas.restoreToCount(stable);}
            for(Part part:draw.parts) {
                int save=canvas.saveLayerAlpha(null,Math.round(255*part.phase));
                try {
                    canvas.translate(0,part.roll*text.getTextSize()*.65f);canvas.clipPath(part.clip);
                    canvas.drawBitmap(entry.bitmap,0,0,null);
                }finally{canvas.restoreToCount(save);}
            }
            return;
        }
        if(entry.composite==null){entry.composite=new RenderNode("Cloud text transition");entry.composite.setClipToBounds(false);}
        entry.composite.setPosition(0,0,text.getWidth(),text.getHeight());
        canvas=entry.composite.beginRecording(text.getWidth(),text.getHeight());
        try {
        int save=canvas.save();
        try{for(Part part:draw.parts)canvas.clipOutPath(part.clip);canvas.drawRenderNode(entry.node);}
        finally{canvas.restoreToCount(save);}
        for(Part part:draw.parts) {
            RenderNode glyph=entry.glyphNodes.computeIfAbsent(part.index,index->{
                RenderNode node=new RenderNode("Cloud character");node.setClipToBounds(false);return node;
            });
            glyph.setPosition(0,0,text.getWidth(),text.getHeight());
            Canvas recording=glyph.beginRecording(text.getWidth(),text.getHeight());
            try{recording.clipPath(part.clip);recording.drawRenderNode(entry.node);}
            finally{glyph.endRecording();}
            entry.effect(glyph,part.phase,part.roll);canvas.drawRenderNode(glyph);
        }
        }finally{entry.composite.endRecording();}
        draw.original.drawRenderNode(entry.composite);
    }
    static void endDraw(Draw draw,boolean success)throws Throwable {
        try {
            if(draw.save<0)draw.entry.node.endRecording();
            if(success) {
                // TextView.onDraw can build a fresh Layout after setText. Read its
                // glyph geometry now, so the first changed frame cannot flash sharp.
                TextView text=draw.entry.source.get();
                if(!draw.reveal&&text!=null)draw.parts=parts(text,draw.entry,draw.time);
                if(draw.parts!=null)drawCharacters(draw);
                else if(draw.save<0)draw.original.drawRenderNode(draw.entry.node);
                else {
                    int save=draw.original.saveLayerAlpha(null,Math.round(255*(draw.reveal?draw.entry.transition.sample(0,draw.time):1)));
                    try{draw.original.drawBitmap(draw.entry.bitmap,0,0,null);}finally{draw.original.restoreToCount(save);}
                }
            }
        } catch(Throwable error) {
            if(draw.save<0&&draw.entry.node!=null){try{draw.entry.node.endRecording();}catch(Throwable ignored){}}
            draw.entry.disable(error);
            if(success&&draw.save<0) {
                TextView text=draw.entry.source.get();
                if(text!=null)Api102Runtime.invokeOriginal(nativeDraw(),text,draw.original);
            }
        } finally {draw.entry.recording=false;}
    }
}
