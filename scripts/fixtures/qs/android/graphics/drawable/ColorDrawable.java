package android.graphics.drawable;
public class ColorDrawable extends Drawable {public int color;public ColorDrawable(int value){color=value;}public int getColor(){return color;}public void setColor(int value){if(color!=value){color=value;invalidateSelf();}}}
