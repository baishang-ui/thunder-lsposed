package android.graphics;
public class RenderEffect { public final float radius;private RenderEffect(float r){radius=r;}public static RenderEffect createBlurEffect(float x,float y,Shader.TileMode mode){return new RenderEffect(x);} }
