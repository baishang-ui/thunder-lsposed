package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import java.util.IdentityHashMap;

/** app_process: real View matrices, alpha layers and exterior moon pixels, without mounting a window. */
public final class CloudCapsuleContentDeviceCheck {
    static final class LayerCanvas extends Canvas {
        int alphaLayers;
        LayerCanvas(Bitmap bitmap){super(bitmap);}
        @Override public int saveLayerAlpha(RectF bounds,int alpha){alphaLayers++;return super.saveLayerAlpha(bounds,alpha);}
    }
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static FrameLayout frame(Context context,int left,int top,int width,int height) {
        FrameLayout view=new FrameLayout(context);view.layout(left,top,left+width,top+height);return view;
    }
    static View fill(Context context,int color,int left,int top,int width,int height) {
        View view=new View(context);view.setBackgroundColor(color);view.layout(left,top,left+width,top+height);return view;
    }
    public static void main(String[] args)throws Exception {
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        var constructor=activityThread.getDeclaredConstructor();constructor.setAccessible(true);
        Object thread=constructor.newInstance();
        Context context=(Context)activityThread.getMethod("getSystemContext").invoke(thread);
        FrameLayout container=frame(context,0,0,300,60);
        FrameLayout outgoing=frame(context,30,0,200,40),incoming=frame(context,30,0,200,40);
        View green=fill(context,Color.GREEN,0,0,20,40),red=fill(context,Color.RED,0,0,20,40);
        outgoing.addView(green);incoming.addView(red);green.layout(0,0,20,40);red.layout(0,0,20,40);
        outgoing.setTranslationX(20);outgoing.setAlpha(.5f);green.setTranslationX(10);
        incoming.setTranslationX(60);red.setTranslationX(5);
        View leftMoon=fill(context,Color.BLUE,20,8,4,24),rightMoon=fill(context,Color.YELLOW,236,8,4,24);
        container.addView(leftMoon);container.addView(outgoing);container.addView(incoming);container.addView(rightMoon);
        outgoing.layout(30,0,230,40);incoming.layout(30,0,230,40);
        leftMoon.layout(20,8,24,32);rightMoon.layout(236,8,240,32);
        IdentityHashMap<View,View> content=new IdentityHashMap<>();content.put(outgoing,green);content.put(incoming,red);
        Bitmap pixels=Bitmap.createBitmap(260,50,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(pixels);
        canvas.translate(20,0);
        // Match Surface: content uses the capsule outline clip, accessories are
        // drawn by the outer Body after restoring that clip.
        int clip=canvas.save();canvas.clipRect(0,0,200,40);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,false,content::containsKey,content::get);
        canvas.restoreToCount(clip);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,true,content::containsKey,content::get);
        int old=pixels.getPixel(55,20),current=pixels.getPixel(90,20);
        check(Color.green(old)>240&&Color.alpha(old)>=126&&Color.alpha(old)<=129,"outgoing content matrix/alpha lost");
        check(Color.red(current)>240&&Color.alpha(current)==255,"incoming content matrix lost");
        check(pixels.getPixel(11,20)==Color.BLUE,"left native moon clipped at the mini-view boundary");
        check(pixels.getPixel(228,20)==Color.YELLOW,"right native moon clipped at the mini-view boundary");
        check(Color.alpha(pixels.getPixel(25,20))==0,"content positions must preserve native translation rather than recenter each item");
        outgoing.setVisibility(View.GONE);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,false,content::containsKey,content::get);
        check(Color.alpha(pixels.getPixel(55,20))==0,"hidden outgoing native view must disappear");
        incoming.setAlpha(.5f);red.setAlpha(.5f);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,false,content::containsKey,content::get);
        int combined=Color.alpha(pixels.getPixel(90,20));
        check(combined>=62&&combined<=65,"wrapper and content alpha must both survive native switching");
        check(content.get(incoming)==red&&red.getParent()==incoming,"drawing must preserve the native parent and source object");
        pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,false,content::containsKey,content::get,true);
        check(pixels.getPixel(25,20)==Color.RED,"owned app entry must suppress native wrapper/item entry alpha and matrix");
        check(Color.alpha(pixels.getPixel(90,20))==0,"owned entry must not draw a second native-shifted capsule");
        outgoing.setVisibility(View.VISIBLE);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,false,content::containsKey,content::get,false,true);
        check(pixels.getPixel(25,20)==Color.RED,"owned content revision must draw incoming content at its centered anchor");
        check(Color.alpha(pixels.getPixel(55,20))==0&&Color.alpha(pixels.getPixel(90,20))==0,
            "owned revision must omit outgoing/native-right-slide duplicates");
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,true,content::containsKey,content::get,false,true);
        check(pixels.getPixel(11,20)==Color.BLUE&&pixels.getPixel(228,20)==Color.YELLOW,
            "centered content ownership must preserve exterior native moons");
        check(incoming.getTranslationX()==60&&red.getTranslationX()==5&&incoming.getAlpha()==.5f&&red.getAlpha()==.5f,
            "centered blur transition must not mutate native source animation properties");
        pixels.eraseColor(Color.TRANSPARENT);LayerCanvas direct=new LayerCanvas(pixels);direct.translate(20,0);
        CloudCapsuleContent.draw(direct,container,incoming,200,40,1,false,content::containsKey,content::get,false,true);
        check(direct.alphaLayers==0&&pixels.getPixel(25,20)==Color.RED,
            "opaque owned content keeps native pixels without an offscreen alpha layer");
        pixels.eraseColor(Color.TRANSPARENT);direct.alphaLayers=0;
        CloudCapsuleContent.draw(direct,container,incoming,200,40,.5f,false,content::containsKey,content::get,false,true);
        check(direct.alphaLayers==1&&Color.alpha(pixels.getPixel(25,20))>=126&&Color.alpha(pixels.getPixel(25,20))<=129,
            "a real crossfade retains exactly one group-alpha layer and correct native pixels");
        pixels.eraseColor(Color.TRANSPARENT);direct.alphaLayers=0;
        CloudCapsuleContent.draw(direct,container,incoming,200,40,.001f,false,content::containsKey,content::get,false,true);
        check(direct.alphaLayers==0&&Color.alpha(pixels.getPixel(25,20))==0,
            "subpixel alpha skips invisible content without allocating an offscreen layer");
        FrameLayout oldMini=frame(context,30,0,200,40);oldMini.setBackgroundColor(Color.MAGENTA);
        View staleMiniItem=fill(context,Color.CYAN,60,0,20,40);oldMini.addView(staleMiniItem);
        container.addView(oldMini);oldMini.layout(30,0,230,40);staleMiniItem.layout(60,0,80,40);
        // The native Expand listener hides mini only at graph end. It remains
        // visible during the new capsule's independent content transition.
        pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,false,content::containsKey,content::get,false,true,oldMini);
        CloudCapsuleContent.draw(canvas,container,incoming,200,40,1,true,content::containsKey,content::get,false,true,oldMini);
        check(pixels.getPixel(25,20)==Color.RED&&Color.alpha(pixels.getPixel(85,20))==0&&Color.alpha(pixels.getPixel(120,20))==0,
            "visible outgoing mini must not be reclassified as an accessory or draw its old plate/content");
        check(pixels.getPixel(11,20)==Color.BLUE&&pixels.getPixel(228,20)==Color.YELLOW,
            "excluding outgoing mini must retain both native moon accessories");
        container.removeView(oldMini);
        FrameLayout miniContainer=frame(context,0,0,300,60),mini=frame(context,30,0,200,40);
        ColorDrawable nativeBackground=new ColorDrawable(Color.MAGENTA),nativeForeground=new ColorDrawable(Color.CYAN);
        mini.setBackground(nativeBackground);mini.setForeground(nativeForeground);
        View miniItem=fill(context,Color.GREEN,0,0,20,40);
        mini.addView(miniItem);miniItem.layout(0,0,20,40);miniItem.setTranslationX(10);miniItem.setAlpha(.5f);
        miniContainer.addView(mini);mini.layout(30,0,230,40);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.draw(canvas,miniContainer,mini,200,40,1,false,view->false,view->null,false,false,mini);
        int miniPixel=pixels.getPixel(35,20);
        check(Color.green(miniPixel)>240&&Color.alpha(miniPixel)>=126&&Color.alpha(miniPixel)<=129,
            "mini content must retain its native item matrix and alpha");
        check(Color.alpha(pixels.getPixel(100,20))==0,
            "mini fixed-radius background and foreground must not paint over the unified shape");
        check(mini.getBackground()==nativeBackground&&mini.getForeground()==nativeForeground,
            "drawing mini content must not mutate native source decoration");
        pixels.eraseColor(Color.TRANSPARENT);direct.alphaLayers=0;
        CloudCapsuleContent.drawOwned(direct,incoming,red,200,40,200,40,1,0);
        check(pixels.getPixel(115,20)==Color.RED&&Color.alpha(pixels.getPixel(25,20))==0,
            "stable owned coordinates ignore all native wrapper/item alpha and translation");
        check(direct.alphaLayers==0,"settled live content allocates no offscreen alpha layer");
        incoming.setTranslationX(-80);incoming.setScaleX(.5f);incoming.setAlpha(0);red.setAlpha(0);red.setTranslationX(-60);
        pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.drawOwned(direct,incoming,red,200,40,200,40,1,0);
        check(pixels.getPixel(115,20)==Color.RED,"native cancellation/reset cannot pull owned content to another position or hide it");
        incoming.removeView(red);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.drawOwned(direct,incoming,red,200,40,200,40,.5f,-4);
        check(Color.red(pixels.getPixel(111,20))>240&&Color.alpha(pixels.getPixel(111,20))>=126,
            "native graph detaching outgoing content must not erase its independent fade: "+Integer.toHexString(pixels.getPixel(111,20))+";left="+red.getLeft()+";width="+red.getWidth());
        // The host repositions its content root while changing drawable width.
        // Owned placement must follow our envelope, never that layout offset.
        red.layout(75,0,95,40);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.drawOwned(direct,incoming,red,200,40,200,40,1,0);
        check(pixels.getPixel(115,20)==Color.RED&&Color.alpha(pixels.getPixel(195,20))==0,
            "native root relayout must not add a fixed-direction text slide");
        // A native width spring is still running after the independent body
        // has settled. It must not rescale the live icon/text a second time.
        pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.drawOwned(direct,incoming,red,200,40,200,40,1,0);
        Bitmap stable=pixels.copy(Bitmap.Config.ARGB_8888,false);
        for(int nativeWidth:new int[]{204,198,202,199,200}) {
            incoming.layout(30,0,30+nativeWidth,40);
            pixels.eraseColor(Color.TRANSPARENT);
            CloudCapsuleContent.drawOwned(direct,incoming,red,nativeWidth,40,200,40,1,0);
            check(pixels.sameAs(stable),"native shell width rebound must not shake or shrink owned content: "+nativeWidth);
        }
        stable.recycle();
        RectF moonBounds=new RectF();
        for(int bodyWidth:new int[]{100,200,320,160}) {
            CloudCapsuleContent.moonBounds(rightMoon,bodyWidth,40,6,true,moonBounds);
            check(moonBounds.left==bodyWidth+6&&moonBounds.centerY()==20,"right crescent follows the presented width exactly");
            CloudCapsuleContent.moonBounds(leftMoon,bodyWidth,40,6,false,moonBounds);
            check(moonBounds.right==-6&&moonBounds.centerY()==20,"left crescent stays attached to the left edge");
        }
        rightMoon.setTranslationX(-100);rightMoon.setTranslationY(15);rightMoon.setScaleX(.2f);rightMoon.setAlpha(0);
        CloudCapsuleContent.moonBounds(rightMoon,200,40,6,true,moonBounds);pixels.eraseColor(Color.TRANSPARENT);
        CloudCapsuleContent.drawMoon(canvas,rightMoon,moonBounds,1);
        check(pixels.getPixel(228,20)==Color.YELLOW,"crescent artwork ignores the native width fragment's conflicting matrix/alpha");
        // Native zero G2 weight must preserve the circular capsule endpoint.
        android.graphics.Path circle=new android.graphics.Path(),continuous=new android.graphics.Path();
        circle.addRoundRect(0,0,200,100,50,50,android.graphics.Path.Direction.CW);
        Object adapter=Class.forName("com.oplus.graphics.OplusPathAdapter").getConstructor(android.graphics.Path.class,int.class).newInstance(continuous,1);
        adapter.getClass().getMethod("addSmoothRoundRect",RectF.class,float[].class,android.graphics.Path.Direction.class,float.class)
            .invoke(adapter,new RectF(0,0,200,100),new float[]{50,50,50,50,50,50,50,50},android.graphics.Path.Direction.CW,0f);
        android.graphics.Region aRegion=new android.graphics.Region(),bRegion=new android.graphics.Region(),clipRegion=new android.graphics.Region(0,0,200,100);
        aRegion.setPath(circle,clipRegion);bRegion.setPath(continuous,clipRegion);
        bRegion.op(aRegion,android.graphics.Region.Op.XOR);
        android.graphics.RegionIterator iterator=new android.graphics.RegionIterator(bRegion);android.graphics.Rect difference=new android.graphics.Rect();int area=0;
        while(iterator.next(difference))area+=difference.width()*difference.height();
        check(area<200,"zero-weight G2 endpoint must agree with the native circular capsule; differing pixels="+area);
        System.out.println("CloudCapsuleContentDeviceCheck: "+checks+" real Android switch/moon/alpha checks passed");
    }
}
