package io.github.baishangui.thunder;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.TextView;

/** Executes production renderer, TextWatcher and onDraw callback against isolated Android stand-ins. */
public final class CloudTextRendererCheck {
    static int checks,invalidations;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static TextView text(ViewGroup root,int id,String value){TextView text=new TextView();text.id=id;text.setText(value);root.addView(text);return text;}
    static CloudTextRenderer renderer(){return new CloudTextRenderer(()->invalidations++,()->300,()->12f);}
    static class Param extends HookCallback.Param {
        boolean failed;Param(TextView text,Canvas canvas){super(text,new Object[]{canvas},null);}
        public Object getResult(){return null;}public void setResult(Object v){}public boolean hasThrowable(){return failed;}
    }
    public static void main(String[] args)throws Throwable {
        SystemClock.now=0;CloudTextRenderer.install();HookCallback hook=Api102Runtime.callback;
        CloudTextRenderer.install();check(Api102Runtime.callback==hook,"framework onDraw is installed once");
        ViewGroup root=new ViewGroup();TextView title=text(root,1,"fixed"),timer=text(root,2,"00:00");
        CloudTextRenderer first=renderer();first.bind(root,false);
        check(CloudTextRenderer.owns(timer)&&CloudTextRenderer.owns(root),"bound content tree owns descendants and root");
        check(!CloudTextRenderer.owns(new View()),"unbound trees preserve framework drawing");
        check(!first.running(),"initial populated tree stays sharp");
        Canvas hardware=new Canvas(true);Param idle=new Param(title,hardware);hook.before(idle);
        check(idle.args[0]==hardware&&RenderNode.count==0,"unchanged onDraw avoids recording and effect allocation");
        SystemClock.now=10;timer.setText("00:01");
        check(first.running()&&timer.watchers.size()==1,"direct Chronometer-like setText starts one local watcher clock");
        check(CloudTextRenderer.beginDraw(title,hardware)==null,"unmodified title does not blur with timer");
        Param active=new Param(timer,hardware);hook.before(active);RenderNode node=RenderNode.last;
        check(active.args[0]!=hardware&&node.recording,"active text receives independent recording canvas");
        check(CloudTextRenderer.beginDraw(timer,hardware)==null,"nested same TextView draw cannot record twice");
        hook.after(active);check(active.args[0]==hardware&&!node.recording&&hardware.nodeDraws==1,"after ends recording and restores original canvas argument");
        check(node.effect.radius>0&&node.alpha==0,"new text starts blurred and transparent in its own node");
        check(timer.effect==null&&timer.alpha==1,"native View effect and alpha are untouched");
        SystemClock.now=110;Canvas software=new Canvas();software.events.add("background");
        Param soft=new Param(timer,software);hook.before(soft);software.events.add("text");hook.after(soft);software.events.add("sibling");
        check(software.events.get(0).equals("background")&&software.events.get(1).startsWith("layer:"),"background is drawn before the text-only fallback layer");
        check(software.lastAlpha>0&&software.lastAlpha<255&&software.restores==1,"software fallback fades only onDraw and balances layer");
        check(software.events.get(software.events.size()-1).equals("sibling"),"sibling drawing stays outside the text effect");
        Param failure=new Param(timer,hardware);hook.before(failure);failure.failed=true;int draws=hardware.nodeDraws;hook.after(failure);
        check(failure.args[0]==hardware&&!node.recording&&hardware.nodeDraws==draws,"throwing native draw still closes recording and never composites incomplete text");
        Param softFailure=new Param(timer,software);hook.before(softFailure);softFailure.failed=true;hook.after(softFailure);
        check(software.restores==2,"software exception restores canvas layer");
        CloudTextRenderer second=renderer();second.bind(root,false);
        check(timer.watchers.size()==1,"multiple card owners share one watcher and phase");
        first.clear();check(timer.watchers.size()==1&&CloudTextRenderer.owns(timer),"old layer disposal preserves remaining owner lease");
        timer.setText("00:02");Param burst=new Param(timer,software);hook.before(burst);int alpha=software.lastAlpha;hook.after(burst);
        check(alpha>0,"burst update does not reset alpha to zero");
        SystemClock.now=310;timer.tick();check(!second.running(),"clock finishes and invalidates owner at sharp endpoint");
        check(node.alpha==1&&node.effect==null,"cached parent node becomes sharp before native rerecord");
        Param sharp=new Param(timer,hardware);hook.before(sharp);check(sharp.args[0]==hardware,"finished text returns to direct native draw");
        check(invalidations>0,"text ticks invalidate independent owner as well as live source");
        ViewGroup replacement=new ViewGroup();TextView title2=text(replacement,1,"fixed"),timer2=text(replacement,2,"00:02");
        second.bind(replacement,true);
        check(!second.running(),"new source objects with matching structural slots/text stay sharp");
        check(timer.watchers.isEmpty()&&timer2.watchers.size()==1,"source replacement releases old watch and registers new tree once");
        SystemClock.now=320;timer2.setText("00:03");second.revision();
        check(CloudTextRenderer.beginDraw(title2,hardware)==null,"revision diff leaves unchanged text region alone");
        check(second.running(),"text change and explicit revision coalesce without losing local phase");
        ViewGroup changedRoot=new ViewGroup();text(changedRoot,1,"fixed");text(changedRoot,2,"00:04");SystemClock.now=1000;
        second.bind(changedRoot,false);check(second.running(),"replaced TextView at same slot with changed text animates");
        second.clear();check(!CloudTextRenderer.owns(changedRoot),"last owner clear removes root ownership");
        check(!node.discarded&&node.alpha==1&&node.effect==null,"disposing a lease preserves sharp display lists cached by a parent");
        check(timer2.watchers.isEmpty(),"last source clear detaches watchers");
        CloudTextRenderer zero=new CloudTextRenderer(()->{},()->0,()->12f);ViewGroup zr=new ViewGroup();TextView z=text(zr,5,"a");zero.bind(zr,false);z.setText("b");
        check(!zero.running()&&CloudTextRenderer.beginDraw(z,hardware)==null,"zero duration never records a render effect");zero.clear();
        CloudTextRenderer failed=renderer();ViewGroup failRoot=new ViewGroup();TextView failText=text(failRoot,7,"old");failed.bind(failRoot,false);SystemClock.now=2000;failText.setText("new");
        Canvas broken=new Canvas(true);Param backend=new Param(failText,broken);hook.before(backend);broken.failDraw=true;hook.after(backend);
        check(backend.args[0]==broken&&broken.events.contains("native-text:new")&&Api102Runtime.replays==1,"drawRenderNode failure replays exact native text onto original canvas");
        check(CloudTextRenderer.beginDraw(failText,broken)==null&&!failed.running(),"backend failure disables repeated effects until rebind");
        failed.revision();SystemClock.now=3000;failText.setText("latest");backend=new Param(failText,broken);hook.before(backend);RenderNode failedNode=RenderNode.last;failedNode.failEnd=true;hook.after(backend);
        check(!failedNode.recording&&broken.events.contains("native-text:latest")&&Api102Runtime.replays==2,"endRecording failure is closed and native original replayed once");
        failed.clear();
        System.out.println("Verified "+checks+" actual scoped text renderer checks.");
    }
}
