package android.graphics.drawable;
public class VectorDrawable extends Drawable {
    public static class VectorState { public android.content.res.ColorStateList mTint; }
    public final VectorState mVectorState=new VectorState();
    @Override public void setTintList(android.content.res.ColorStateList tint) { mVectorState.mTint=tint; }
}
