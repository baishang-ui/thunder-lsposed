package io.github.baishangui.thunder;

/** Runs the production affine math without Android or the synthetic Surface fixture. */
public final class CloudContentTransformCheck {
    private static void close(float expected,float actual) {
        if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);
    }
    public static void main(String[] args) {
        CloudContentTransform fit=CloudContentTransform.fit(400,200,100,300,200,200);
        close(.5f,fit.scale());close(100,fit.x());close(350,fit.y());
        close(0,fit.sourceX(100));close(0,fit.sourceY(350));
        close(400,fit.sourceX(300));close(200,fit.sourceY(450));
        // Source larger on only one axis, unscaled small content, nonzero screen origin.
        CloudContentTransform tall=CloudContentTransform.fit(80,200,-20,15,100,50);
        close(.25f,tall.scale());close(20,tall.x());close(15,tall.y());
        CloudContentTransform small=CloudContentTransform.fit(20,10,40,60,100,40);
        close(1,small.scale());close(80,small.x());close(75,small.y());
        // A DOWN snapshot remains a fixed mapping across MOVE, extra pointers and UP.
        for(float[] point:new float[][]{{100,350},{120,370},{280,440},{300,450}}) {
            float sx=fit.sourceX(point[0]),sy=fit.sourceY(point[1]);
            close(point[0],fit.screenX(sx));close(point[1],fit.screenY(sy));
        }
        // Invalid geometry cannot inject NaN/Infinity into MotionEvent or Canvas.
        try {CloudContentTransform.fit(0,10,0,0,10,10);throw new AssertionError("invalid geometry accepted");}
        catch(IllegalArgumentException expected) { }
        System.out.println("CloudContentTransformCheck: 22 checks passed");
    }
}
