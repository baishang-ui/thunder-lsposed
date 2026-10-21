package io.github.baishangui.thunder;

import android.graphics.Matrix;
import android.graphics.Region;
import android.view.MotionEvent;

/** app_process only: real Android event semantics, with no window, input injection or business UI. */
public final class CloudInputBridgeDeviceCheck {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void close(float expected,float actual){check(Math.abs(expected-actual)<.001f,expected+" != "+actual);}
    private static MotionEvent event(int action,int pointers) {
        return event(action,pointers,null);
    }
    private static MotionEvent event(int action,int pointers,Matrix sourceToBody) {
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[pointers];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[pointers];
        for(int i=0;i<pointers;i++) {
            properties[i]=new MotionEvent.PointerProperties();properties[i].id=3+i*5;
            properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            float[] point={100+i*40,200+i*60};if(sourceToBody!=null)sourceToBody.mapPoints(point);
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=point[0];coords[i].y=point[1];
            coords[i].pressure=.7f;coords[i].size=.4f;
        }
        return MotionEvent.obtain(1000,1500,action,pointers,properties,coords,0x41,1,1.5f,2.5f,7,0,0x1002,0);
    }
    private static void metadata(MotionEvent original,MotionEvent copy) {
        check(copy.getDownTime()==original.getDownTime(),"long-press downTime changed");
        check(copy.getEventTime()==original.getEventTime(),"eventTime changed");
        check(copy.getSource()==original.getSource()&&copy.getDeviceId()==original.getDeviceId(),"input source changed");
        check(copy.getMetaState()==original.getMetaState()&&copy.getButtonState()==original.getButtonState(),"modifiers changed");
        check(copy.getPointerCount()==original.getPointerCount(),"pointer count changed");
        check(copy.getHistorySize()==original.getHistorySize(),"history dropped");
        for(int i=0;i<original.getPointerCount();i++) {
            check(copy.getPointerId(i)==original.getPointerId(i),"pointer ID changed");
            check(copy.getToolType(i)==original.getToolType(i),"tool type changed");
            close(original.getRawX(i),copy.getRawX(i));close(original.getRawY(i),copy.getRawY(i));
            close(original.getPressure(i),copy.getPressure(i));
        }
    }
    public static void main(String[] args) {
        // A closing body may reopen only where the visible body already covers
        // its capsule destination. It must never regain the large card region.
        CloudMorphHook.Frame capsule=new CloudMorphHook.Frame(100,20,240,80,40,0);
        Region visible=new Region(200,20,500,160);
        Region capsuleOnly=CloudInputBridge.capsuleRegion(visible,capsule,0,0);
        check(capsuleOnly.contains(210,60),"visible capsule overlap cannot reopen immediately");
        check(!capsuleOnly.contains(160,60),"invisible capsule target intercepted DOWN");
        check(!capsuleOnly.contains(400,60),"closing card area intercepted DOWN outside the capsule");
        check(!capsuleOnly.contains(338,21),"capsule bounding-box corner intercepted DOWN");
        check(CloudInputBridge.capsuleRegion(new Region(0,200,500,400),capsule,0,0).isEmpty(),
            "disjoint destination must leave the entire display transparent");
        check(CloudInputBridge.capsuleRegion(visible,null,0,0).isEmpty(),"unknown capsule destination captured input");
        Region published=new Region();int traversals=0;
        if(CloudInputBridge.updateRegion(published,capsuleOnly))traversals++;
        for(int i=0;i<120;i++)if(CloudInputBridge.updateRegion(published,new Region(capsuleOnly)))traversals++;
        check(traversals==1,"stable capsule input requested a layout on every native pre-draw");
        Region hole=new Region(capsuleOnly);hole.op(220,40,230,50,Region.Op.DIFFERENCE);
        check(hole.getBounds().equals(capsuleOnly.getBounds()),"test must retain the region's enclosing bounds");
        check(CloudInputBridge.updateRegion(published,hole),"changed contour with identical bounds was not submitted");
        hole.setEmpty();check(!published.isEmpty(),"submitted region retained the caller's mutable instance");
        check(CloudInputBridge.updateRegion(published,hole)&&published.isEmpty(),"outside dismissal did not immediately clear input");
        check(!CloudInputBridge.updateRegion(published,hole),"empty closing input repeatedly requested traversal");
        Matrix windowMap=new Matrix();windowMap.setValues(new float[]{1,0,-24,0,1,-88,0,0,1});
        MotionEvent down=event(MotionEvent.ACTION_DOWN,1);
        MotionEvent outside=CloudInputBridge.outsideToRoot(down,windowMap);
        check(outside.getActionMasked()==MotionEvent.ACTION_OUTSIDE,"outside did not reach native dismissal action");
        check(down.getActionMasked()==MotionEvent.ACTION_DOWN,"caller event mutated");
        close(76,outside.getX());close(112,outside.getY());metadata(down,outside);
        outside.recycle();down.recycle();

        MotionEvent sentinel=MotionEvent.obtain(1000,1500,MotionEvent.ACTION_OUTSIDE,0,0,0);
        outside=CloudInputBridge.outsideToRoot(sentinel,windowMap);
        close(0,outside.getX());close(0,outside.getY());
        check(outside.getToolType(0)==MotionEvent.TOOL_TYPE_UNKNOWN,"outside privacy sentinel changed");
        outside.recycle();sentinel.recycle();

        MotionEvent multi=event(MotionEvent.ACTION_MOVE,2);
        MotionEvent.PointerCoords[] coords={new MotionEvent.PointerCoords(),new MotionEvent.PointerCoords()};
        for(int i=0;i<2;i++){multi.getPointerCoords(i,coords[i]);coords[i].x+=12;coords[i].y-=8;}
        multi.addBatch(1700,coords,0x41);
        outside=CloudInputBridge.outsideToRoot(multi,windowMap);metadata(multi,outside);
        close(76,outside.getHistoricalX(0,0));close(112,outside.getHistoricalY(0,0));
        outside.recycle();multi.recycle();

        // Production content fit plus the existing whole-event transform. Exercise
        // positive/negative dp offsets at three densities, content scaling and press scale.
        for(float density:new float[]{1f,2.625f,3.5f})for(int dx:new int[]{-32,32})for(int dy:new int[]{-48,48}) {
            float frameX=100+dx*density,frameY=300+dy*density;
            CloudContentTransform fit=CloudContentTransform.fit(400,200,0,0,200,160);
            float press=.96f;
            Matrix sourceToBody=new Matrix();sourceToBody.setValues(new float[]{fit.scale()*press,0,frameX+(200-200*press)/2+fit.x()*press,
                0,fit.scale()*press,frameY+(160-160*press)/2+fit.y()*press,0,0,1});
            Matrix inverse=new Matrix();check(sourceToBody.invert(inverse),"content mapping not invertible");
            Matrix sourceToRoot=new Matrix();sourceToRoot.setTranslate(24,88);
            Matrix bodyToRoot=new Matrix();bodyToRoot.setConcat(sourceToRoot,inverse);
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL}) {
                int count=(action&MotionEvent.ACTION_MASK)==MotionEvent.ACTION_POINTER_DOWN||
                    (action&MotionEvent.ACTION_MASK)==MotionEvent.ACTION_POINTER_UP?2:1;
                MotionEvent original=event(action,count,sourceToBody);
                MotionEvent forwarded=MotionEvent.obtain(original);forwarded.transform(bodyToRoot);
                metadata(original,forwarded);
                check(forwarded.getAction()==action,"pointer action/index changed");
                for(int i=0;i<count;i++){close(124+i*40,forwarded.getX(i));close(288+i*60,forwarded.getY(i));}
                forwarded.recycle();original.recycle();
            }
        }
        System.out.println("CloudInputBridgeDeviceCheck: "+checks+" real Android mapping/outside/gesture checks passed");
    }
}
