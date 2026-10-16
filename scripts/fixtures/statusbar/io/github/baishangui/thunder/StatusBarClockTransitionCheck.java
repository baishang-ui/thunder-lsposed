package io.github.baishangui.thunder;

import android.os.Bundle;
import android.widget.TextView;
import com.android.systemui.statusbar.phone.PhoneStatusBarView;
import com.android.systemui.statusbar.policy.Clock;
import com.oplus.systemui.separate.OplusSimpleQSFakeController;

public final class StatusBarClockTransitionCheck {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static boolean near(float a,float b){return Math.abs(a-b)<.001f;}
    static void after(Object owner,String method,Object...args)throws Throwable {
        var p=StatusBarIconCheck.param(owner,args);
        Api102Runtime.callbacks.get(OplusSimpleQSFakeController.class.getName()+"#"+method).after(p);
    }
    static void geometry(OplusSimpleQSFakeController c)throws Throwable {
        c.updateClockPivot$1();after(c,"updateClockPivot$1");
        OplusSimpleQSFakeController.access$updateFakeToStatusBarTranslation(c);
        after(null,"access$updateFakeToStatusBarTranslation",c);
    }
    static void frame(OplusSimpleQSFakeController c,float fraction)throws Throwable {
        c.updateFakeClockScale(fraction);after(c,"updateFakeClockScale",fraction);
        c.updateQSClockScale(fraction);after(c,"updateQSClockScale",fraction);
        OplusSimpleQSFakeController.access$updateFakeSimpleQSTranslation(c,fraction);after(null,"access$updateFakeSimpleQSTranslation",c,fraction);
        OplusSimpleQSFakeController.access$updateSimpleQSTranslation(c,fraction);after(null,"access$updateSimpleQSTranslation",c,fraction);
    }
    static void transfer(OplusSimpleQSFakeController c,Clock s,TextView q,float sourceAnchor,float targetAnchor)throws Throwable {
        geometry(c);
        check(near(c.fakeClockViewContainer.getPivotX(),sourceAnchor) && near(q.getPivotX(),targetAnchor),"pivots anchor actual HH:mm rather than date prefix or native padding");
        check(near(c.fakeClockViewContainer.getPivotY(),(c.fakeClockViewContainer.getHeight()-s.getHeight())/2f+s.getBaseline()),"fake clock pivots on its own baseline");
        for(float f:new float[]{0,.25f,.5f,.9f,1,.5f,0}) {
            frame(c,f);
            var fake=c.fakeClockViewContainer;
            check(near(s.getTextSize()*fake.getScaleY(),q.getTextSize()*q.getScaleY())
                && near(s.getTextSize()*s.getTextScaleX()*fake.getScaleX(),q.getTextSize()*q.getTextScaleX()*q.getScaleX()),
                "custom and native glyph dimensions agree throughout expansion and return");
            float fakeX=c.sourceX+sourceAnchor+fake.getTranslationX();
            float shadeX=c.targetX+targetAnchor+q.getTranslationX();
            float fakeY=c.sourceCenterY+s.getBaseline()-s.getHeight()/2f+fake.getTranslationY();
            float shadeY=c.targetCenterY+q.getBaseline()-q.getHeight()/2f+q.getTranslationY();
            check(near(fakeX,shadeX) && near(fakeY,shadeY),"time anchor and baseline follow the same path on both views");
            check(near(c.notificationTranslationY,(c.targetCenterY-c.sourceCenterY)*f),"clock baseline correction leaves notification icon motion native");
            if(f==0)check(near(fake.getScaleX(),1)&&near(fake.getScaleY(),1),"collapsed endpoint preserves original custom clock");
            if(f==1)check(near(q.getScaleX(),3)&&near(q.getScaleY(),3),"expanded endpoint preserves native shade clock scale");
        }
    }
    public static void main(String[] args)throws Throwable {
        StatusBarClockTransitionHook.install(StatusBarClockTransitionCheck.class.getClassLoader());
        StatusBarClockHook.install(StatusBarClockTransitionCheck.class.getClassLoader());
        var b=new Bundle();b.putBoolean("enabled",true);b.putBoolean("status_bar_clock",true);b.putBoolean("status_bar_compress_clock",true);StatusBarLayoutCheck.config(b);
        var root=new PhoneStatusBarView();var s=new Clock();root.addView(s);
        s.setText("2026/10/16 Fri 9:07:05 PM");s.setTextSize(0,20);s.setTextScaleX(.5f);s.actualWidth=220;s.actualHeight=32;s.baseline=23;
        var q=new TextView();q.setText("9:07");q.setTextSize(0,14);q.actualWidth=80;q.actualHeight=22;q.baseline=15;
        var c=new OplusSimpleQSFakeController();c.oplusQSSimpleHeader.clock=q;
        var fake=new OplusSimpleQSFakeController.Fake();fake.clock=s;fake.top=4;c.fakeClockViewContainer.addView(fake);c.fakeClockViewContainer.actualWidth=s.actualWidth;c.fakeClockViewContainer.actualHeight=40;
        float source=2+"2026/10/16 Fri ".length()*20*.6f*.5f;
        transfer(c,s,q,source,2);
        // C17 binds clock_for_fake (a FrameLayout), not its StatClock child.
        var wrapper=new android.view.ViewGroup();root.addView(wrapper);wrapper.addView(s);
        wrapper.actualWidth=s.actualWidth;wrapper.actualHeight=s.actualHeight;
        fake.clock=wrapper;
        transfer(c,s,q,source,2);
        // Repeat native recomputation to ensure offsets never accumulate.
        float dx=c.fakeToClockTranslationX,dy=c.fakeToClockTranslationY;geometry(c);
        check(near(dx,c.fakeToClockTranslationX)&&near(dy,c.fakeToClockTranslationY),"repeated geometry refresh does not accumulate offsets");
        s.layoutDirection=q.layoutDirection=1;
        c.isLargeDisplay=true;c.clockScaleFactor=5;
        transfer(c,s,q,source+4*20*.6f*.5f,2+4*14*.6f);
        s.left=6;s.top=3;wrapper.actualHeight=38;c.fakeClockViewContainer.actualHeight=46;
        geometry(c);frame(c,.4f);
        float wrappedX=source+4*20*.6f*.5f+6,wrappedBaseline=4+3+s.getBaseline();
        check(near(c.fakeClockViewContainer.getPivotX(),wrappedX)
            && near(c.fakeClockViewContainer.getPivotY(),wrappedBaseline),"nested clock offsets reach the fake drawing coordinates");
        check(near(c.sourceX+wrappedX+c.fakeClockViewContainer.getTranslationX(),
            c.targetX+2+4*14*.6f+q.getTranslationX()),"wrapped RTL time stays aligned horizontally");
        check(near(c.sourceCenterY+wrappedBaseline-23+c.fakeClockViewContainer.getTranslationY(),
            c.targetCenterY+q.getBaseline()-q.getHeight()/2f+q.getTranslationY()),"wrapper height and child top preserve baseline alignment");
        s.setText("٢٠٢٦/١٠/١٦ الجمعة ٩:٠٧:٠٥ م");
        check(near(StatusBarClockTransitionHook.timeAnchor(s,false),2+s.getText().toString().indexOf('٩')*20*.6f*.5f),"localized digits locate time after a date prefix");
        c.isUpdateFakeViewLocation=false;StatusBarClockTransitionHook.invalidate();
        check(c.isUpdateFakeViewLocation,"live layout and format changes invalidate native transition geometry");
        c.isUpdateFakeViewLocation=false;
        var stat=new com.oplus.systemui.statusbar.widget.StatClock();root.addView(stat);
        var measure=Api102Runtime.callbacks.get(stat.getClass().getName()+"#onMeasure");
        var p=StatusBarIconCheck.param(stat,90,40);measure.before(p);measure.after(p);
        check(!c.isUpdateFakeViewLocation,"ordinary clock remeasurement cannot trigger native gesture interruption");
        b.putBoolean("status_bar_clock",false);StatusBarLayoutCheck.config(b);frame(c,0);
        check(!near(q.getScaleX(),1),"compression-only mode still aligns the two clocks");
        b.putBoolean("status_bar_compress_clock",false);StatusBarLayoutCheck.config(b);geometry(c);frame(c,0);
        check(near(q.getScaleX(),1)&&near(c.fakeClockViewContainer.getScaleX(),1)&&near(c.fakeToClockTranslationY,100),"all options off restores native animation equations");
        StatusBarClockTransitionHook.invalidate();c.isUpdateFakeViewLocation=false;StatusBarClockTransitionHook.invalidate();
        check(!c.isUpdateFakeViewLocation,"unrelated live settings cannot invalidate clock animation while both options remain off");
        b.putBoolean("status_bar_clock",true);b.putBoolean("enabled",false);StatusBarLayoutCheck.config(b);frame(c,.5f);
        check(near(q.getScaleY(),3)&&near(c.fakeClockViewContainer.getScaleY(),3),"module off retains native capped display scaling");
        b.putBoolean("enabled",true);StatusBarLayoutCheck.config(b);fake.clock=new Clock();geometry(c);frame(c,0);
        check(near(q.getScaleX(),1),"clocks outside PhoneStatusBarView are excluded");
        System.out.println("Verified "+checks+" notification clock transition checks; device rendering remains unverified.");
    }
}
