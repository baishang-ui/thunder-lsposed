package io.github.baishangui.thunder;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;
import android.widget.Chronometer;

/** Production interception and clocks on Android stand-ins; device probe owns pixel verification. */
public final class CloudTextRendererCheck {
    static int checks,invalidations;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static TextView text(ViewGroup root,int id,String value){TextView text=new TextView();text.id=id;text.setText(value);root.addView(text);return text;}
    public enum Mode { PLAIN_TEXT, COUNT_UP, COUNT_DOWN }
    public static class SmartTimer extends Chronometer {
        Mode mode=Mode.COUNT_UP;
        public Mode getCurrentMode(){return mode;}
    }
    static SmartTimer timer(ViewGroup root,int id,String value){SmartTimer text=new SmartTimer();text.id=id;text.setText(value);root.addView(text);return text;}
    static CloudTextRenderer renderer(){return new CloudTextRenderer(()->invalidations++,()->300,()->12f);}
    static class Param extends HookCallback.Param {
        boolean failed;Param(TextView text,Canvas canvas){super(text,new Object[]{canvas},null);}
        public Object getResult(){return null;}public void setResult(Object v){}public boolean hasThrowable(){return failed;}
    }
    static RenderNode recording(){return RenderNode.all.stream().filter(n->n.recording).findFirst().orElseThrow();}
    static RenderNode draw(HookCallback hook,TextView text,Canvas canvas)throws Throwable {
        Param p=new Param(text,canvas);hook.before(p);RenderNode nativeNode=recording();
        check(p.args[0]!=canvas&&nativeNode.recording,"active text receives a native recording canvas");
        check(CloudTextRenderer.beginDraw(text,canvas)==null,"recursive text recording is excluded");
        hook.after(p);check(p.args[0]==canvas&&!nativeNode.recording,"after callback closes recording and restores arguments");return nativeNode;
    }
    public static void main(String[] args)throws Throwable {
        SystemClock.now=0;CloudTextRenderer.install();HookCallback hook=Api102Runtime.callback;
        CloudTextRenderer.install();check(Api102Runtime.callback==hook,"framework onDraw is installed once");
        ViewGroup root=new ViewGroup();TextView title=text(root,1,"fixed");SmartTimer timer=timer(root,2,"00:00");
        CloudTextRenderer first=renderer();first.bind(root,false);
        check(CloudTextRenderer.owns(timer)&&!CloudTextRenderer.owns(new View()),"ownership is scoped to bound content");
        check(!first.running(),"initial populated tree stays sharp");
        Canvas hardware=new Canvas(true);Param idle=new Param(title,hardware);hook.before(idle);
        check(idle.args[0]==hardware&&RenderNode.count==0,"idle onDraw avoids nodes and effects");
        title.setText("第一句歌词");title.setText("第二句歌词");
        check(!first.running()&&CloudTextRenderer.beginDraw(title,hardware)==null,"lyrics update without character blur or roll");
        title.setText("12:34");title.setText("12:35");
        check(!first.running(),"time-shaped plain text cannot opt into character animation");
        timer.mode=Mode.PLAIN_TEXT;timer.setText("普通文案");timer.setText("新的文案");
        check(!first.running(),"host SmartChronometer in plain text mode stays native");
        timer.mode=Mode.COUNT_DOWN;timer.setText("00:00");timer.tick();
        check(first.running(),"host countdown mode enables local time animation");
        timer.mode=Mode.PLAIN_TEXT;timer.setText("00:00");timer.tick();timer.mode=Mode.COUNT_UP;
        check(!first.running(),"switching a running timer back to plain text cancels character clocks");
        SystemClock.now=10;timer.setText("00:01");
        check(first.running()&&timer.watchers.size()==1,"direct timer tick starts a local transition");
        check(CloudTextRenderer.beginDraw(title,hardware)==null,"unchanged sibling title stays native");
        RenderNode nativeNode=draw(hook,timer,hardware),digit=RenderNode.last;
        check(nativeNode.alpha==1&&nativeNode.effect==null,"unchanged digits use sharp native pixels");
        check(RenderNode.all.stream().anyMatch(n->n.name.equals("Cloud text transition")&&n.canvas.events.contains("out:4:5")&&!n.canvas.events.contains("out:0:1")),"only changed digit is removed from the stable draw");
        check(digit.canvas.events.contains("clip:4:5")&&digit.alpha==0&&digit.effect.radius>0&&digit.translationY>0,
            "changed digit alone starts transparent, blurred and below its final baseline");
        check(timer.effect==null&&timer.alpha==1,"native View effect and alpha are untouched");
        SystemClock.now=110;timer.tick();float phase=digit.alpha;
        check(phase>0&&phase<1&&digit.translationY>0&&digit.translationY<13,"character fades and rises on its own clock");
        timer.setText("00:02");timer.tick();check(digit.alpha==0,"a second change of that digit gets a fresh edit clock");
        Canvas software=new Canvas();software.events.add("background");Param soft=new Param(timer,software);
        hook.before(soft);software.events.add("stable-native-text");hook.after(soft);software.events.add("sibling");
        check(software.events.contains("out:4:5")&&software.events.contains("clip:4:5"),"software fallback keeps unchanged characters outside the edit layer");
        check(software.events.contains("translate:0.0:13.0")&&software.lastAlpha==0,"software fallback rolls changed characters from below");
        check(software.events.get(0).equals("background")&&software.events.get(software.events.size()-1).equals("sibling"),"background and sibling remain outside text fallback");
        CloudTextRenderer second=renderer();second.bind(root,false);first.clear();
        check(timer.watchers.size()==1&&CloudTextRenderer.owns(timer),"shared owner release preserves watcher and active clocks");
        SystemClock.now=210;
        ViewGroup replacement=new ViewGroup();TextView title2=text(replacement,1,"fixed"),timer2=timer(replacement,2,"00:02");second.bind(replacement,true);
        check(second.running()&&timer.watchers.isEmpty()&&timer2.watchers.size()==1,"source replacement transfers in-flight clocks by structural slot");
        check(CloudTextRenderer.beginDraw(title2,hardware)==null,"source replacement cannot animate a new ordinary label character by character");
        Canvas rebound=new Canvas(true);draw(hook,timer2,rebound);RenderNode reboundDigit=RenderNode.last;
        check(reboundDigit.alpha>0&&reboundDigit.alpha<1,"source replacement preserves progress instead of restarting or snapping");
        SystemClock.now=410;timer2.tick();check(!second.running()&&reboundDigit.alpha==1&&reboundDigit.effect==null&&reboundDigit.translationY==0,"completion restores cached character nodes fully");
        check(CloudTextRenderer.beginDraw(timer2,hardware)==null,"settled characters return to native drawing");
        SystemClock.now=500;Object scene=new Object();second.reveal(scene);
        check(second.running(),"scene change animates even identical values");
        Canvas sceneCanvas=new Canvas(true);RenderNode sceneNode=draw(hook,title2,sceneCanvas);
        check(sceneCanvas.nodeDraws==1&&sceneNode.alpha==0&&sceneNode.effect.radius>0&&sceneNode.translationY==0,"whole-scene text blurs in without horizontal or vertical roll");
        SystemClock.now=600;second.reveal(scene);title2.tick();float scenePhase=sceneNode.alpha;
        check(scenePhase>0&&scenePhase<1,"repeated scene token cannot restart text reveal");
        title2.setText("other");title2.tick();check(sceneNode.alpha==scenePhase,"text tick during scene uses its common reveal clock");
        SystemClock.now=800;title2.tick();timer2.tick();check(!second.running(),"scene resolves at original endpoint despite text ticks");
        second.reveal(new Object());second.clear();
        check(!CloudTextRenderer.owns(replacement)&&sceneNode.alpha==1&&sceneNode.effect==null&&!sceneNode.discarded,"last owner clear leaves cached parent recordings sharp");
        check(title2.watchers.isEmpty()&&timer2.watchers.isEmpty()&&invalidations>0,"cleanup detaches watchers and active frames invalidate independent surface");
        CloudTextRenderer zero=new CloudTextRenderer(()->{},()->0,()->12f);ViewGroup zr=new ViewGroup();TextView z=text(zr,5,"a");zero.bind(zr,false);z.setText("b");zero.reveal(new Object());
        check(!zero.running()&&CloudTextRenderer.beginDraw(z,hardware)==null,"zero duration always uses native text");zero.clear();
        CloudTextRenderer failed=renderer();ViewGroup failRoot=new ViewGroup();TextView failText=timer(failRoot,7,"old");failed.bind(failRoot,false);SystemClock.now=2000;failText.setText("new");
        Canvas broken=new Canvas(true);Param backend=new Param(failText,broken);hook.before(backend);broken.failDraw=true;int replays=Api102Runtime.replays;hook.after(backend);
        check(backend.args[0]==broken&&broken.events.contains("native-text:new")&&Api102Runtime.replays==replays+1,"backend failure replays exact native text once");
        check(CloudTextRenderer.beginDraw(failText,broken)==null&&!failed.running(),"backend failure disables repeated effects until rebind");
        failed.revision();SystemClock.now=3000;failText.setText("latest");backend=new Param(failText,broken);hook.before(backend);RenderNode failedNode=recording();failedNode.failEnd=true;replays=Api102Runtime.replays;hook.after(backend);
        check(!failedNode.recording&&broken.events.contains("native-text:latest")&&Api102Runtime.replays==replays+1,"endRecording failure closes and falls back to native once");failed.clear();
        System.out.println("Verified "+checks+" character/scene renderer checks.");
    }
}
