package io.github.baishangui.thunder;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** App-process contract probe with real Android text; native APK branches are audited separately. */
public final class CloudTextHookDeviceCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static final class Module {public boolean a=true;public View d;public int commits;public void p(String text){commits++;((TextView)((FrameLayout)d).getChildAt(0)).setText(text);}}
    public static final class Media {public View b,c;}
    static final class Param extends HookCallback.Param {
        Object result;boolean returned;Throwable failure;
        Param(Object owner,Object...args){super(owner,args,null);}
        @Override public Object getResult(){return result;}
        @Override public void setResult(Object value){result=value;returned=true;}
        @Override public boolean hasThrowable(){return failure!=null;}
    }
    public static void main(String[] args)throws Throwable {
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Context context=(Context)activityThread.getMethod("currentApplication").invoke(null);
        if(context==null)throw new IllegalStateException("Run this native text probe in an Android app process");
        FrameLayout slot=new FrameLayout(context);TextView title=new TextView(context),time=new TextView(context);
        View icon=new View(context);slot.addView(title);slot.addView(time);slot.addView(icon);
        title.setText("before");time.setText("00:01");icon.setAlpha(.65f);icon.setTranslationX(11);
        Module owner=new Module();owner.d=slot;
        Set<View> owned=Collections.newSetFromMap(new IdentityHashMap<>());owned.add(slot);
        HookCallback module=CloudTextHook.moduleCallback(owned::contains);
        Param outer=new Param(owner,"after");module.before(outer);
        check(!owner.a&&!outer.returned,"owned native update must execute with slide permission disabled");
        title.setText("after");
        Param nested=new Param(owner,"nested");module.before(nested);title.setText("nested");module.after(nested);
        check(!owner.a,"nested Android text commit must preserve the outer direct-update scope");
        module.after(outer);
        check(owner.a&&"nested".contentEquals(title.getText()),"native Android setText completes before permission restoration");
        check("00:01".contentEquals(time.getText())&&icon.getAlpha()==.65f&&icon.getTranslationX()==11,
            "text routing must not mutate sibling text or icon properties");
        Param failure=new Param(owner,"failure");module.before(failure);failure.failure=new IllegalStateException();module.after(failure);
        check(owner.a&&failure.hasThrowable(),"Android native failure must not leak temporary animation permission");
        owned.clear();Param outside=new Param(owner,"outside");module.before(outside);module.after(outside);
        check(owner.a,"unowned source keeps native animation permission");
        Media mediaOwner=new Media();mediaOwner.b=title;mediaOwner.c=time;owned.add(time);
        HookCallback media=CloudTextHook.mediaCallback(owned::contains);
        Param mediaCall=new Param(mediaOwner,"next title",false);media.before(mediaCall);
        check(Boolean.TRUE.equals(mediaCall.args[1])&&!mediaCall.returned,"owned media uses native direct-update parameter");
        title.setText((String)mediaCall.args[0]);media.after(mediaCall);
        check("next title".contentEquals(title.getText())&&Boolean.FALSE.equals(mediaCall.args[1]),
            "native media text commit remains live and parameter restores");
        check(time.getTranslationX()==0&&title.getTranslationX()==0&&icon.getTranslationX()==11,
            "scoped callbacks never rewrite text or icon position");
        owned.clear();Param early=new Param(owner,"appearing");module.before(early);owner.p("appearing");module.after(early);
        CloudTextHook.adopt(slot);
        check(owner.commits==2&&owner.a&&"appearing".contentEquals(title.getText()),"acquisition settles a pre-existing native text update and restores its permission");
        CloudTextHook.adopt(slot);check(owner.commits==2,"repeated acquisition cannot repeat native text commit");
        System.out.println("Verified "+checks+" Android native text callback contracts.");
    }
}
