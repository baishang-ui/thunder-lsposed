package com.oplus.systemui.notification.lockscreen.capsule.stroke;
import java.util.ArrayList;
import java.util.List;
/** C17 updateViewSize unconditionally resets every animated stroke to height / 2. */
public class StrokeView extends android.view.View {
    public float desiredDrawWidth,desiredDrawHeight;
    public List<StrokeLayer> layers=new ArrayList<>();
    public void updateViewSize(int width,int height){
        desiredDrawWidth=width;desiredDrawHeight=height;
        if(width>0&&height>0)for(StrokeLayer layer:layers)layer.setCornerRadius(height/2f);
    }
    public void onDraw(android.graphics.Canvas canvas){for(StrokeLayer layer:layers)layer.draw();}
}
