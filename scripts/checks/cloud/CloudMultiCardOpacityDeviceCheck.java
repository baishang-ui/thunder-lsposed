package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;

/** Actual Surface opacity writes and Android compositing, isolated from plugin/window setup. */
public final class CloudMultiCardOpacityDeviceCheck {
    static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(float expected,float actual,String message){check(Math.abs(expected-actual)<.00001f,message+": "+expected+" != "+actual);}
    private static void set(Object target,String name,Object value)throws Exception{
        Field field=CloudMorphSurface.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private record Surface(CloudMorphSurface owner,FrameLayout body,View shape,CloudVisibilityState visibility) { }
    private static Surface surface(Context context,CloudMorphSurface parent)throws Exception{
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");
        Field singleton=unsafeType.getDeclaredField("theUnsafe");singleton.setAccessible(true);
        Object unsafe=singleton.get(null);
        CloudMorphSurface owner=(CloudMorphSurface)unsafeType.getMethod("allocateInstance",Class.class).invoke(unsafe,CloudMorphSurface.class);
        Class<?> bodyType=Class.forName(CloudMorphSurface.class.getName()+"$Body");
        Constructor<?> bodyConstructor=bodyType.getDeclaredConstructor(CloudMorphSurface.class,Context.class);bodyConstructor.setAccessible(true);
        FrameLayout body=(FrameLayout)bodyConstructor.newInstance(owner,context);
        Class<?> shapeType=Class.forName(CloudMorphSurface.class.getName()+"$Shape");
        Constructor<?> shapeConstructor=shapeType.getDeclaredConstructor(CloudMorphSurface.class,Context.class);shapeConstructor.setAccessible(true);
        View shape=(View)shapeConstructor.newInstance(owner,context);
        body.addView(shape,new FrameLayout.LayoutParams(400,240));
        CloudVisibilityState visibility=new CloudVisibilityState();
        CloudFrameBatch batch=new CloudFrameBatch();batch.begin();
        set(owner,"body",body);set(owner,"shape",shape);set(owner,"visibility",visibility);set(owner,"parent",parent);
        set(owner,"inputBatch",batch);set(owner,"layers",new LinkedHashMap<>());
        set(owner,"lifetimeVisible",true);set(owner,"hostOpacityLimit",1f);
        body.layout(0,0,1272,2800);shape.layout(0,0,400,240);
        return new Surface(owner,body,shape,visibility);
    }
    private static Bitmap pixels(FrameLayout parent){
        Bitmap result=Bitmap.createBitmap(parent.getWidth(),parent.getHeight(),Bitmap.Config.ARGB_8888);
        parent.draw(new Canvas(result));return result;
    }
    public static void run(Context context)throws Exception{
        Surface lead=surface(context,null),follower=surface(context,lead.owner);
        for(float phase:new float[]{.5f,0,.01f,.25f,.75f,.99f,1,.6f,.2f,0,1}){
            follower.owner.opacity(phase);
            close(1,follower.body.getAlpha(),"a follower must not fade its display-sized envelope");
            close(phase,follower.shape.getAlpha(),"follower content and material share the card's local alpha");
            close(phase,follower.owner.presentationOpacity(),"reversal snapshot must retain the follower's actual opacity");
            check(follower.body.getVisibility()==(phase>0?View.VISIBLE:View.INVISIBLE),"zero-opacity follower input visibility changed");
        }
        for(float phase:new float[]{1,.5f,0,.75f,1}){
            lead.owner.opacity(phase);
            close(phase,lead.body.getAlpha(),"primary visibility still fades the entire card group");
            close(1,lead.shape.getAlpha(),"primary material cannot apply group alpha twice");
            close(phase,lead.owner.presentationOpacity(),"primary snapshot changed");
        }
        follower.visibility.host(.5f);follower.owner.opacity(.6f);
        close(.6f,follower.shape.getAlpha(),"follower must use its own unchanged host opacity mapping");
        follower.visibility.host(.1f);follower.owner.opacity(.6f);
        close(.24f,follower.shape.getAlpha(),"host gate opacity is composed once");
        set(follower.owner,"capsuleHideOwned",true);follower.owner.opacity(.6f);
        close(.6f,follower.shape.getAlpha(),"terminal deletion bypasses the already-snapshotted host gate");
        set(follower.owner,"capsuleHideOwned",false);follower.visibility.host(1);

        // Compare two real Android trees. The reference reproduces the former
        // full-envelope group alpha; actual uses the production opacity method.
        FrameLayout actual=new FrameLayout(context),reference=new FrameLayout(context);
        actual.layout(0,0,500,400);reference.layout(0,0,500,400);
        for(int index=0;index<3;index++){
            Surface card=surface(context,lead.owner);
            int color=new int[]{Color.RED,Color.GREEN,Color.BLUE}[index];
            card.shape.setBackgroundColor(color);card.owner.opacity(.25f+index*.25f);
            actual.addView(card.body,new FrameLayout.LayoutParams(500,400));
            card.body.layout(0,0,500,400);card.shape.layout(30+index*70,40+index*50,230+index*70,180+index*50);
            FrameLayout old=new FrameLayout(context);View oldShape=new View(context);oldShape.setBackgroundColor(color);
            old.addView(oldShape,new FrameLayout.LayoutParams(200,140));reference.addView(old,new FrameLayout.LayoutParams(500,400));
            old.layout(0,0,500,400);oldShape.layout(30+index*70,40+index*50,230+index*70,180+index*50);old.setAlpha(.25f+index*.25f);
        }
        Bitmap a=pixels(actual),b=pixels(reference);
        try{
            for(int y=0;y<400;y+=7)for(int x=0;x<500;x+=7){
                int expected=b.getPixel(x,y),value=a.getPixel(x,y);
                for(int shift:new int[]{0,8,16,24})check(Math.abs((expected>>>shift&255)-(value>>>shift&255))<=2,"local opacity changed overlapping-card pixels at "+x+","+y);
            }
        }finally{a.recycle();b.recycle();}
        long displayArea=1272L*2800,cardArea=400L*240;
        check(follower.body.getWidth()*follower.body.getHeight()==displayArea,"test must retain a full-display input envelope");
        check(follower.shape.getWidth()*follower.shape.getHeight()==cardArea,"test must use the tight card content bounds");
        for(int count:new int[]{1,2,3,5,8}){
            long fadingArea=0;
            for(int index=1;index<count;index++){
                Surface card=surface(context,lead.owner);card.owner.opacity(.5f);
                check(card.body.hasOverlappingRendering(),"test must cover overlapping ViewGroup alpha compositing");
                if(card.body.getAlpha()<1)fadingArea+=(long)card.body.getWidth()*card.body.getHeight();
                if(card.shape.getAlpha()<1)fadingArea+=(long)card.shape.getWidth()*card.shape.getHeight();
            }
            check(fadingArea==(count-1)*cardArea,"multi-card fade area must grow with card bounds instead of display envelopes: "+count);
        }
        System.out.println("Multi-card alpha bounds: "+displayArea+" -> "+cardArea+" pixels per follower; this is layer area, not measured FPS.");
    }
}
