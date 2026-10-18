package com.coui.appcompat.pressfeedback;
public class COUIPressFeedbackHelper {
    public boolean pressed;public final com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout.Motion spring=new com.oplus.systemui.plugins.qs.customize.view.tile.OplusQSThreeStageLayout.Motion();
    public COUIPressFeedbackHelper(android.view.View view,int type){}
    public void executeFeedbackAnimator(boolean value){pressed=value;}
    public Object getSpringAnimation(){return spring;}
}
