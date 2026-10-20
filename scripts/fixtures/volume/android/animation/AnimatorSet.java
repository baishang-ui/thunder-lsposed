package android.animation;
import java.util.ArrayList;
public class AnimatorSet extends Animator {
    public final ArrayList<Animator> children=new ArrayList<>();
    public ArrayList<Animator> getChildAnimations(){return new ArrayList<>(children);}
}
