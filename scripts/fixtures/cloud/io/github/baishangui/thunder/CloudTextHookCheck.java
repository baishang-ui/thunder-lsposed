package io.github.baishangui.thunder;

import android.view.View;
import android.widget.TextView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Executes the real callbacks around native-shaped direct text update branches. */
public final class CloudTextHookCheck {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    public static final class Module {
        public boolean a=true;
        public View d=new View();
        int commits,slides;String text;
        public void p(String text){commits++;if(a)slides++;this.text=text;}
    }
    public static final class MediaText extends TextView {
        int writes;float marqueePosition;
        @Override public void setText(CharSequence value){super.setText(value);writes++;marqueePosition=0;}
    }
    public static final class Media {
        public MediaText b=new MediaText(),c=new MediaText(),f=b;
        public boolean animating;public String e;public android.animation.Animator d;int marqueeStarts;
        public void a(){marqueeStarts++;}
        int commits,fades;String text;
        public Media(){b.setText("");c.setVisibility(4);}
        public void c(String text,boolean direct){
            commits++;
            if(!direct&&animating){e=text.contentEquals(f.getText())?null:text;return;}
            // The host checks equality before scheduling a fade; its direct
            // branch always calls setText, which resets TextView's marquee.
            if(!direct&&f.getVisibility()==View.VISIBLE&&text.contentEquals(f.getText()))return;
            if(!direct)fades++;else{animating=false;e=null;}
            this.text=text;f.setText(text);f.setVisibility(View.VISIBLE);
        }
    }
    static HookCallback.Param param(Object owner,Object...args){
        var p=CloudCheck.param(owner,null,args);p.returnEarly=false;return p;
    }
    public static void main(String[] args)throws Throwable {
        CloudTextHook.install(CloudTextHookCheck.class.getClassLoader());
        check(Api102Runtime.callbacks.containsKey("com.oplus.systemui.plugins.shared.template.component.module.i#p"),"native ContentModule text boundary is registered");
        check(Api102Runtime.callbacks.containsKey("com.oplus.systemui.plugins.shared.template.component.media.view.r#c"),"native media text boundary is registered");
        Set<View> owned=Collections.newSetFromMap(new IdentityHashMap<>());
        HookCallback module=CloudTextHook.moduleCallback(owned::contains),media=CloudTextHook.mediaCallback(owned::contains);
        Module item=new Module();owned.add(item.d);
        var p=param(item,"new title");module.before(p);
        check(!item.a&&!p.returnEarly,"owned text uses native direct commit without intercepting the original method");
        item.p("new title");module.after(p);
        check(item.a&&item.commits==1&&item.slides==0&&"new title".equals(item.text),"native text commit runs and animation permission is restored");
        p=param(item,"outer");module.before(p);
        var nested=param(item,"inner");module.before(nested);item.p("inner");module.after(nested);
        check(!item.a,"nested text commit restores the outer call's temporary disabled flag");
        item.p("outer");module.after(p);check(item.a,"outer commit restores the original enabled flag");
        p=param(item,"throwing");module.before(p);p.failure=new IllegalStateException("native failure");module.after(p);
        check(item.a&&p.hasThrowable(),"throwing native updates restore animation permission without consuming the failure");
        item.a=false;p=param(item,"already direct");module.before(p);item.p("already direct");module.after(p);
        check(!item.a,"an originally disabled native flag stays disabled");
        item.a=true;owned.remove(item.d);p=param(item,"outside");module.before(p);item.p("outside");module.after(p);
        check(item.a&&item.slides==1,"unowned template retains native animation and update behavior");
        owned.add(item.d);p=param(item,"detached during call");module.before(p);owned.remove(item.d);module.after(p);
        check(item.a,"ownership changing during native commit cannot prevent restoration");
        Media pair=new Media();owned.add(pair.c);
        p=param(pair,"title",false);media.before(p);
        check(Boolean.TRUE.equals(p.args[1])&&!p.returnEarly,"changed media keeps the native direct commit");
        if(!p.returnEarly)pair.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(pair.commits==1&&pair.fades==0&&"title".equals(pair.text)&&Boolean.FALSE.equals(p.args[1]),"changed media commits once and restores its argument");
        check(pair.marqueeStarts==1,"a changed line resumes native marquee after its direct commit");
        for(boolean force:new boolean[]{false,true}) {
            int writes=pair.f.writes;pair.f.marqueePosition=84;
            p=param(pair,"title",force);media.before(p);
            if(!p.returnEarly)pair.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
            check(p.returnEarly&&pair.f.writes==writes&&pair.f.marqueePosition==84,"ordinary and force duplicate delivery both preserve marquee");
        }
        p=param(pair,"failure",false);media.before(p);p.failure=new IllegalStateException("media failure");media.after(p);
        check(Boolean.FALSE.equals(p.args[1])&&p.hasThrowable(),"native media failure restores its argument");
        owned.clear();p=param(pair,"native",false);media.before(p);pair.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(pair.fades==1&&!p.returnEarly&&Boolean.FALSE.equals(p.args[1]),"unowned media preserves native fade");
        Module early=new Module();p=param(early,"appearing");module.before(p);early.p("appearing");module.after(p);
        check(early.slides==1,"before acquisition the host may already have started its inner slide");
        CloudTextHook.adopt(early.d);
        check(early.commits==2&&early.slides==1&&early.a&&"appearing".equals(early.text),"acquisition settles the pending inner slide through native direct commit and restores its permission");
        CloudTextHook.adopt(early.d);check(early.commits==2,"repeated bind cannot replay an already adopted update");
        Media earlyMedia=new Media();p=param(earlyMedia,"media appears",false);media.before(p);earlyMedia.c("media appears",false);media.after(p);
        CloudTextHook.adopt(earlyMedia.b);
        check(earlyMedia.commits==2&&earlyMedia.fades==1&&"media appears".equals(earlyMedia.text),"media pending animation settles through its own direct commit on acquisition");
        Module broken=new Module();p=param(broken,"failed");module.before(p);p.failure=new IllegalStateException();module.after(p);CloudTextHook.adopt(broken.d);
        check(broken.commits==0,"failed unowned updates must never be replayed on acquisition");
        Media lyrics=new Media();owned.add(lyrics.b);
        p=param(lyrics,"A long scrolling lyric line",false);media.before(p);if(!p.returnEarly)lyrics.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        int writes=lyrics.f.writes;
        for(int tick=1;tick<=20;tick++) {
            lyrics.f.marqueePosition=tick*12f;
            p=param(lyrics,new String("A long scrolling lyric line"),false);
            media.before(p);if(!p.returnEarly)lyrics.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
            check(lyrics.f.writes==writes&&lyrics.f.marqueePosition==tick*12f,
                "repeated media progress/lyric delivery must preserve the running marquee at tick "+tick);
        }
        p=param(lyrics,"The next scrolling lyric line",false);media.before(p);if(!p.returnEarly)lyrics.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(lyrics.f.writes==writes+1&&lyrics.f.marqueePosition==0&&lyrics.fades==0,
            "a genuinely new lyric commits once without replaying the native fade");
        lyrics.f=captureOtherText(lyrics);owned.clear();owned.add(lyrics.c);writes=lyrics.f.writes;lyrics.f.marqueePosition=84;
        p=param(lyrics,"The alternate scrolling lyric",false);media.before(p);if(!p.returnEarly)lyrics.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(lyrics.f.writes==writes&&lyrics.f.marqueePosition==84,
            "the host's active second TextView keeps its scroll when the pair switches");
        lyrics.e="obsolete pending lyric";
        var fade=android.animation.ValueAnimator.ofFloat(0,1);lyrics.d=fade;
        fade.addUpdateListener(value->{throw new AssertionError("obsolete fade updated a lyric");});
        fade.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator animation){throw new AssertionError("obsolete fade listener replaced a lyric");}});
        p=param(lyrics,"The alternate scrolling lyric",true);media.before(p);media.after(p);
        check(p.returnEarly&&lyrics.d==null&&lyrics.e==null&&fade.cancelled,"force duplicate clears stale fade and pending line without callbacks");
        check(lyrics.f.writes==writes&&lyrics.f.marqueePosition==84,"force duplicate retains active text and scroll");
        check(lyrics.c.getVisibility()==View.VISIBLE&&lyrics.b.getVisibility()==4&&lyrics.c.getAlpha()==1,"duplicate normalizes the pair without switching the current view");
        System.out.println("Verified "+checks+" scoped native text update checks.");
    }
    static MediaText captureOtherText(Media pair){pair.b.setVisibility(4);pair.c.setVisibility(View.VISIBLE);pair.c.setText("The alternate scrolling lyric");return pair.c;}
}
