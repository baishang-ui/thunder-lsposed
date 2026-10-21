package io.github.baishangui.thunder;

import android.view.View;
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
        void p(String text){commits++;if(a)slides++;this.text=text;}
    }
    public static final class Media {
        public View b=new View(),c=new View();
        int commits,fades;String text;
        void c(String text,boolean direct){commits++;if(!direct)fades++;this.text=text;}
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
        check(Boolean.TRUE.equals(p.args[1])&&!p.returnEarly,"owned media chooses the verified true direct-update branch");
        pair.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(pair.commits==1&&pair.fades==0&&"title".equals(pair.text)&&Boolean.FALSE.equals(p.args[1]),"media native update runs once and original argument is restored");
        p=param(pair,"title",false);media.before(p);pair.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(pair.fades==0&&pair.commits==2,"same-value media commits cannot replay the native fade");
        p=param(pair,"failure",false);media.before(p);p.failure=new IllegalStateException("media failure");media.after(p);
        check(Boolean.FALSE.equals(p.args[1])&&p.hasThrowable(),"media failure restores arguments and propagates normally");
        p=param(pair,"outer",false);media.before(p);nested=param(pair,"inner",false);media.before(nested);media.after(nested);
        check(Boolean.TRUE.equals(p.args[1])&&Boolean.FALSE.equals(nested.args[1]),"nested media arguments are isolated by native invocation");
        media.after(p);check(Boolean.FALSE.equals(p.args[1]),"outer media argument restores independently");
        owned.clear();p=param(pair,"native",false);media.before(p);pair.c((String)p.args[0],(Boolean)p.args[1]);media.after(p);
        check(pair.fades==1&&Boolean.FALSE.equals(p.args[1]),"unowned media preserves native fade");
        System.out.println("Verified "+checks+" scoped native text update checks.");
    }
}
