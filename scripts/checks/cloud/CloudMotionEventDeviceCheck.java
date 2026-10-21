package io.github.baishangui.thunder;

import android.graphics.Matrix;
import android.view.MotionEvent;

/** Run with app_process: verifies platform raw coordinates survive the production transform API. */
public final class CloudMotionEventDeviceCheck {
    private static void close(float expected,float actual) {
        if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        MotionEvent original=MotionEvent.obtain(10,20,MotionEvent.ACTION_DOWN,100,200,0);
        original.setSource(0x1002); // SOURCE_TOUCHSCREEN, matching real window input.
        original.offsetLocation(20,30);
        MotionEvent forwarded=MotionEvent.obtain(original);
        Matrix map=new Matrix();map.setValues(new float[]{.5f,0,10,0,.5f,20,0,0,1});
        forwarded.transform(map);
        close(70,forwarded.getX());close(135,forwarded.getY());
        close(original.getRawX(),forwarded.getRawX());close(original.getRawY(),forwarded.getRawY());
        if(original.getDownTime()!=forwarded.getDownTime()||original.getEventTime()!=forwarded.getEventTime())
            throw new AssertionError("gesture times changed");
        forwarded.recycle();original.recycle();
        MotionEvent.PointerProperties[] properties={new MotionEvent.PointerProperties(),new MotionEvent.PointerProperties()};
        properties[0].id=3;properties[1].id=8;properties[0].toolType=1;properties[1].toolType=1;
        MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        coords[0].x=10;coords[0].y=20;coords[1].x=30;coords[1].y=40;
        MotionEvent multi=MotionEvent.obtain(10,20,MotionEvent.ACTION_MOVE,2,properties,coords,0,0,1,1,0,0,0x1002,0);
        coords[0].x=20;coords[1].y=60;multi.addBatch(30,coords,0);
        MotionEvent transformed=MotionEvent.obtain(multi);transformed.transform(map);
        if(transformed.getPointerId(0)!=3||transformed.getPointerId(1)!=8||transformed.getHistorySize()!=1)
            throw new AssertionError("pointer IDs or gesture history changed");
        for(int i=0;i<2;i++){close(multi.getRawX(i),transformed.getRawX(i));close(multi.getRawY(i),transformed.getRawY(i));}
        close(15,transformed.getHistoricalX(0,0));close(30,transformed.getHistoricalY(0,0));
        close(20,transformed.getX(0));close(50,transformed.getY(1));
        transformed.recycle();multi.recycle();
        System.out.println("CloudMotionEventDeviceCheck: coordinate/raw/time checks passed");
    }
}
