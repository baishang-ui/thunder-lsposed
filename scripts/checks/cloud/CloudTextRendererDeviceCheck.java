package io.github.baishangui.thunder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RenderNode;
import android.graphics.Typeface;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Chronometer;

/** Android app-process probe: native TextView pixels and hardware recording, no mounted window. */
public final class CloudTextRendererDeviceCheck {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    enum Mode { PLAIN_TEXT, COUNT_UP }
    static final class Label extends Chronometer {
        int effectWrites;
        Mode mode=Mode.PLAIN_TEXT;
        public Mode getCurrentMode(){return mode;}
        Label(Context context){super(context);setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24);setTextColor(Color.WHITE);setTypeface(Typeface.MONOSPACE);setPadding(0,0,0,0);setIncludeFontPadding(false);}
        @Override public void setRenderEffect(android.graphics.RenderEffect effect){effectWrites++;super.setRenderEffect(effect);}
        @Override protected void onDraw(Canvas canvas) {
            CloudTextRenderer.Draw draw=CloudTextRenderer.beginDraw(this,canvas);
            if(draw==null){super.onDraw(canvas);return;}
            boolean complete=false;
            try{super.onDraw(draw.canvas);complete=true;}
            finally{try{CloudTextRenderer.endDraw(draw,complete);}catch(RuntimeException|Error error){throw error;}catch(Throwable error){throw new IllegalStateException(error);}}
        }
    }
    static int different(Bitmap a,Bitmap b,int left,int top,int right,int bottom) {
        int count=0;for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(a.getPixel(x,y)!=b.getPixel(x,y))count++;return count;
    }
    static Bitmap draw(View view) {
        Bitmap pixels=Bitmap.createBitmap(240,100,Bitmap.Config.ARGB_8888);view.draw(new Canvas(pixels));return pixels;
    }
    public static void main(String[] args)throws Throwable {
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> activityThread=Class.forName("android.app.ActivityThread");
        Context context=(Context)activityThread.getMethod("currentApplication").invoke(null);
        if(context==null)throw new IllegalStateException("Run this rendering probe in an Android app process");
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Color.BLUE);
        Label stable=new Label(context),timer=new Label(context);stable.setText("TITLE");timer.setText("00:00");
        timer.mode=Mode.COUNT_UP;
        stable.setId(101);timer.setId(102);timer.setBackgroundColor(Color.GREEN);
        root.addView(stable,new FrameLayout.LayoutParams(240,45));
        FrameLayout.LayoutParams timerParams=new FrameLayout.LayoutParams(240,45);timerParams.topMargin=50;root.addView(timer,timerParams);
        View icon=new View(context);icon.setBackgroundColor(Color.RED);
        FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(20,20);iconParams.leftMargin=210;iconParams.topMargin=60;root.addView(icon,iconParams);
        root.measure(View.MeasureSpec.makeMeasureSpec(240,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));root.layout(0,0,240,100);
        CloudTextRenderer renderer=new CloudTextRenderer(()->{},()->1000000,()->12f);renderer.bind(root,false);
        Bitmap original=draw(root);int effectWrites=timer.effectWrites;timer.setText("00:01");Bitmap active=draw(root);
        check(different(original,active,0,0,240,45)==0,"unchanged native TextView pixels must remain exact");
        int prefixRight=(int)timer.getLayout().getPrimaryHorizontal(4);
        check(different(original,active,0,50,prefixRight,95)==0,"unchanged timer digits retain exact native pixels during the local edit");
        check(different(original,active,prefixRight,50,200,95)>0,"only changed digit must fade and rise on software Canvas");
        check(active.getPixel(220,70)==Color.RED,"overlapping sibling icon cannot be erased by a text rectangle cutout");
        check(active.getPixel(200,85)==Color.GREEN,"TextView background must remain outside onDraw opacity layer");
        check(active.getPixel(200,48)==Color.BLUE,"parent background stays unchanged");
        check(timer.getAlpha()==1&&timer.effectWrites==effectWrites,"native TextView alpha and RenderEffect remain untouched");
        Canvas hardware;RenderNode target=new RenderNode("Cloud text probe target");target.setPosition(0,0,240,100);hardware=target.beginRecording(240,100);
        CloudTextRenderer.Draw recording=CloudTextRenderer.beginDraw(timer,hardware);
        check(recording!=null&&recording.canvas!=hardware&&recording.canvas.isHardwareAccelerated(),"real hardware path records only native text into an independent node");
        Paint paint=new Paint();paint.setColor(Color.WHITE);recording.canvas.drawRect(0,0,30,30,paint);
        CloudTextRenderer.endDraw(recording,true);target.endRecording();
        check(target.hasDisplayList(),"hardware recording produces a composite display list");
        renderer.reveal(new Object());Bitmap scene=draw(root);
        check(different(original,scene,0,0,200,45)>0,"whole-scene reveal also fades the unchanged title");
        check(scene.getPixel(220,70)==Color.RED&&scene.getPixel(200,85)==Color.GREEN,"whole-scene reveal cannot fade icons or backgrounds");
        CloudTextRenderer extra=new CloudTextRenderer(()->{});extra.bind(root,false);renderer.clear();
        check(CloudTextRenderer.owns(timer)&&extra.running(),"multiple real owners retain the active text lease when one releases");
        extra.clear();check(!CloudTextRenderer.owns(timer),"last real owner releases TextWatcher scope");
        CloudTextRenderer immediate=new CloudTextRenderer(()->{},()->0,()->12f);immediate.bind(root,false);timer.setText("00:02");
        check(!immediate.running()&&CloudTextRenderer.beginDraw(timer,new Canvas(active))==null,"zero duration returns immediately to native sharp text");immediate.clear();
        System.out.println("CloudTextRendererDeviceCheck: "+checks+" native software pixels/hardware recording checks passed at "+SystemClock.uptimeMillis());
    }
}
