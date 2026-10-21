package io.github.baishangui.thunder;

public final class CloudMaterialBlendCheck {
    static void close(float expected,float actual){if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) {
        float[] capsule=new float[18],card=new float[18],result=new float[18];
        for(int i=0;i<18;i++){capsule[i]=i;card[i]=i+10;}
        CloudMaterialBlend.mix(capsule,card,.25f,result);
        close(4.5f,result[2]);close(6.5f,result[4]);close(18.5f,result[16]);
        close(0,result[0]);close(1,result[1]);close(3,result[3]);close(17,result[17]);
        CloudMaterialBlend.mix(capsule,card,1,result);for(int i=0;i<18;i++)close(card[i],result[i]);
        CloudMaterialBlend.mix(capsule,card,-1,result);for(int i=0;i<18;i++)close(capsule[i],result[i]);
        System.out.println("CloudMaterialBlendCheck: 43 checks passed");
    }
}
