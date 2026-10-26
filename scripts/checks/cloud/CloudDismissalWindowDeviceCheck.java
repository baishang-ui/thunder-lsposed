package io.github.baishangui.thunder;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Real WindowManager/input-dispatch regression; no SystemUI hooks or business actions. */
public final class CloudDismissalWindowDeviceCheck {
    static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class TouchView extends View {
        int downs;
        WindowManager.LayoutParams nativeParams;
        TouchView(Context context){super(context);}
        @Override public android.view.ViewGroup.LayoutParams getLayoutParams(){return nativeParams==null?super.getLayoutParams():nativeParams;}
        WindowManager.LayoutParams actualParams(){return (WindowManager.LayoutParams)super.getLayoutParams();}
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)downs++;return true;}
    }
    private static void set(Object target,String name,Object value)throws Exception{
        Field field=CloudMorphSurface.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static CloudMorphSurface inputSurface(Context context,View nativeRoot,CloudInputOwnership ownership)throws Exception{
        // Isolate the production input lifetime from native plugin material setup.
        // Body is a real Android View; only the irrelevant renderer is unmounted.
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        Object unsafe=singleton.get(null);
        CloudMorphSurface surface=(CloudMorphSurface)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,CloudMorphSurface.class);
        Class<?> bodyType=Class.forName(CloudMorphSurface.class.getName()+"$Body");
        Constructor<?> constructor=bodyType.getDeclaredConstructor(CloudMorphSurface.class,Context.class);constructor.setAccessible(true);
        View body=(View)constructor.newInstance(surface,context);body.setVisibility(View.INVISIBLE);
        set(surface,"body",body);set(surface,"cardRoot",nativeRoot);set(surface,"inputOwnership",ownership);
        set(surface,"visibility",new CloudVisibilityState());set(surface,"attached",true);set(surface,"lifetimeVisible",true);
        set(surface,"current",new CloudMorphHook.Frame(100,200,400,200,40,1));
        return surface;
    }
    private static void ui(Instrumentation instrumentation,Throwing action)throws Throwable{
        Throwable[] failure={null};
        instrumentation.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});
        if(failure[0]!=null)throw failure[0];
        instrumentation.waitForIdleSync();
        instrumentation.getUiAutomation().getClass().getMethod("syncInputTransactions").invoke(instrumentation.getUiAutomation());
    }
    private interface Throwing {void run()throws Throwable;}
    private static void committed(Instrumentation instrumentation,View view)throws Throwable{
        java.util.concurrent.CountDownLatch frame=new java.util.concurrent.CountDownLatch(1);
        ui(instrumentation,()->{view.getViewTreeObserver().registerFrameCommitCallback(frame::countDown);view.invalidate();});
        check(frame.await(3,java.util.concurrent.TimeUnit.SECONDS),"test window frame was not committed");
        instrumentation.getUiAutomation().getClass().getMethod("syncInputTransactions").invoke(instrumentation.getUiAutomation());
    }
    private static void tap(Instrumentation instrumentation,float x,float y){
        long time=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);
        MotionEvent up=MotionEvent.obtain(time,time+16,MotionEvent.ACTION_UP,x,y,0);
        down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        try{
            check(instrumentation.getUiAutomation().injectInputEvent(down,true),"test DOWN injection failed");
            check(instrumentation.getUiAutomation().injectInputEvent(up,true),"test UP injection failed");
        }finally{down.recycle();up.recycle();}
        instrumentation.waitForIdleSync();
    }
    private static String inputDump(Instrumentation instrumentation)throws Exception{
        StringBuilder text=new StringBuilder();
        try(android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand("dumpsys input");
            java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(fd.getFileDescriptor())))){
            for(String line;(line=reader.readLine())!=null;)if(line.contains("name=")&&text.length()<6500)text.append(line.substring(0,Math.min(650,line.length()))).append('\n');
        }
        return text.toString();
    }
    public static void run(Instrumentation instrumentation)throws Throwable{
        Context target=instrumentation.getTargetContext();
        check(target.getSystemService(android.os.PowerManager.class).isInteractive()&&!target.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked(),
            "real window input test requires an awake, unlocked device");
        instrumentation.getUiAutomation().adoptShellPermissionIdentity("android.permission.INJECT_EVENTS");
        TouchView[] lower={null},nativeCard={null};WindowManager[] manager={null},bridgeManager={null};
        CloudInputOwnership ownership=new CloudInputOwnership();CloudInputBridge bridge=new CloudInputBridge();
        CloudMorphSurface[] surface={null};Method owns=CloudMorphSurface.class.getDeclaredMethod("ownsNativeInput");owns.setAccessible(true);
        int[] location=new int[2];
        try{
            ui(instrumentation,()->{
                Context application=instrumentation.getTargetContext();
                android.view.Display display=application.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
                Context context=application.createDisplayContext(display).createWindowContext(2038,null);
                manager[0]=context.getSystemService(WindowManager.class);
                // A temporary app cannot acquire SystemUI's signature-only 2017
                // window type. Translate only that permission boundary to an
                // allowed overlay; flags and real input dispatch remain intact.
                bridgeManager[0]=(WindowManager)Proxy.newProxyInstance(WindowManager.class.getClassLoader(),new Class<?>[]{WindowManager.class},(proxy,method,args)->{
                    if(method.getName().equals("updateViewLayout")){
                        WindowManager.LayoutParams allowed=new WindowManager.LayoutParams();allowed.copyFrom((WindowManager.LayoutParams)args[1]);allowed.type=2038;
                        manager[0].updateViewLayout((View)args[0],allowed);return null;
                    }
                    return method.invoke(manager[0],args);
                });
                lower[0]=new TouchView(context);nativeCard[0]=new TouchView(context);
                lower[0].setBackgroundColor(0xff203040);nativeCard[0].setBackgroundColor(0xff304050);
                WindowManager.LayoutParams params=new WindowManager.LayoutParams(240,160,2038,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
                params.gravity=Gravity.CENTER;params.setTitle("Thunder dismissal test receiver");
                manager[0].addView(lower[0],params);
                WindowManager.LayoutParams cardParams=new WindowManager.LayoutParams();cardParams.copyFrom(params);
                cardParams.setTitle("Thunder dismissal test native card");
                manager[0].addView(nativeCard[0],cardParams);
            });
            ui(instrumentation,()->{
                check(lower[0].isAttachedToWindow()&&nativeCard[0].isAttachedToWindow(),"test windows were not mounted");
                check(nativeCard[0].getWidth()>0&&nativeCard[0].getHeight()>0,"native window not laid out");
                WindowManager.LayoutParams model=new WindowManager.LayoutParams();model.copyFrom(nativeCard[0].actualParams());model.type=2017;nativeCard[0].nativeParams=model;
                nativeCard[0].getLocationOnScreen(location);
                ownership.bind(nativeCard[0]);ownership.begin(true,true);ownership.settle(true);ownership.dismiss();
                surface[0]=inputSurface(nativeCard[0].getContext(),nativeCard[0],ownership);
                check(!ownership.acceptsInput()&&!ownership.captureOutside()&&!ownership.acceptsCapsuleInput(),"terminal Body retained input");
                check((Boolean)owns.invoke(surface[0]),"invisible Body prematurely released the still-mounted native card input lease");
            });
            committed(instrumentation,nativeCard[0]);
            tap(instrumentation,location[0]+120,location[1]+80);
            if(nativeCard[0].downs!=1||lower[0].downs!=0)throw new AssertionError("control tap missed test windows: "+inputDump(instrumentation));
            ui(instrumentation,()->{
                check(nativeCard[0].downs==1&&lower[0].downs==0,"control tap must reach the upper native window: upper="+nativeCard[0].downs+", lower="+lower[0].downs+", point="+location[0]+","+location[1]);
                nativeCard[0].setAlpha(0);
                check((Boolean)owns.invoke(surface[0]),"invisible Body prematurely released the still-mounted native card input lease");
                bridge.sync(nativeCard[0],bridgeManager[0],(Boolean)owns.invoke(surface[0]));
                check((((WindowManager.LayoutParams)nativeCard[0].getLayoutParams()).flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0,"transparent native card remains touchable");
                check((nativeCard[0].actualParams().flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0,"real WindowManager did not receive the input lease");
            });
            tap(instrumentation,location[0]+120,location[1]+80);
            ui(instrumentation,()->{
                check(nativeCard[0].downs==1&&lower[0].downs==1,"closed card area did not pass touch to the underlying window");
                nativeCard[0].setVisibility(View.GONE);
                check(!(Boolean)owns.invoke(surface[0]),"native hide did not end its lease");bridge.release();
                check((((WindowManager.LayoutParams)nativeCard[0].getLayoutParams()).flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)==0,"native flags were not restored after real hide");
                manager[0].removeViewImmediate(nativeCard[0]);
            });
            tap(instrumentation,location[0]+120,location[1]+80);
            ui(instrumentation,()->check(lower[0].downs==2,"removed native window still intercepted the old card area"));
        }finally{
            try{ui(instrumentation,()->{
                bridge.release();
                if(nativeCard[0]!=null&&nativeCard[0].isAttachedToWindow())manager[0].removeViewImmediate(nativeCard[0]);
                if(lower[0]!=null&&lower[0].isAttachedToWindow())manager[0].removeViewImmediate(lower[0]);
            });}finally{instrumentation.getUiAutomation().dropShellPermissionIdentity();}
        }
    }
}
