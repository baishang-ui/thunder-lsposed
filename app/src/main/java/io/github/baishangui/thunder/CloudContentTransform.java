package io.github.baishangui.thunder;

/** Affine content placement shared by drawing, input and accessibility. */
record CloudContentTransform(float scale,float x,float y) {
    static CloudContentTransform fit(float sourceWidth,float sourceHeight,float x,float y,float width,float height) {
        if(!Float.isFinite(sourceWidth)||!Float.isFinite(sourceHeight)||!Float.isFinite(x)||!Float.isFinite(y)||
            !Float.isFinite(width)||!Float.isFinite(height)||sourceWidth<=0||sourceHeight<=0||width<=0||height<=0)
            throw new IllegalArgumentException("Content and frame geometry must be finite and positive");
        float scale=Math.min(1f,Math.min(width/sourceWidth,height/sourceHeight));
        return new CloudContentTransform(scale,x+(width-sourceWidth*scale)/2f,y+(height-sourceHeight*scale)/2f);
    }
    float sourceX(float screenX){return (screenX-x)/scale;}
    float sourceY(float screenY){return (screenY-y)/scale;}
    float screenX(float sourceX){return x+sourceX*scale;}
    float screenY(float sourceY){return y+sourceY*scale;}
}
