package com.oplus.graphics;

/** Firmware codec boundary: nonpositive smooth weights are rejected without changing state. */
public class OplusBlurParam {
    public static int rejectedWeights;
    public int smoothType,type,mode;
    public float smoothWeight=com.oplus.view.OplusSmoothRoundedManager.getNonWight();
    public float[] bottom,top;
    private float[] values=new float[18];
    public OplusBlurParam(){values[2]=1;}
    public void setSmoothCornerType(int value){smoothType=value;}
    public int getSmoothCornerType(){return smoothType;}
    public void setSmoothCornerWeight(float value){if(value<=0){rejectedWeights++;return;}smoothWeight=value;}
    public float getSmoothCornerWeight(){return smoothWeight;}
    public void setBlurType(int value){type=value;}
    public void setMaterialParams(int value,float[] below,float[] above){
        mode=value;bottom=below;top=above;
        System.arraycopy(below,0,values,4,4);System.arraycopy(above,0,values,8,4);
    }
    public void fromFloatArray(float[] source){
        if(source.length!=18)return;
        values=source.clone();type=Math.round(source[0]);mode=Math.round(source[3]);
        smoothWeight=source[16];smoothType=Math.round(source[17]);
    }
    public float[] toFloatArray(){
        float[] result=values.clone();result[0]=type;result[3]=mode;
        result[16]=smoothWeight;result[17]=smoothType;return result;
    }
}
