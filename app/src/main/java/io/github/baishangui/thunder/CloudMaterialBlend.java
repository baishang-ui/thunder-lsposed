package io.github.baishangui.thunder;

/** C17 OplusBlurParam codec: preserve discrete enum slots while blending scalar/color values. */
final class CloudMaterialBlend {
    private CloudMaterialBlend() { }
    static void mix(float[] capsule,float[] card,float progress,float[] result) {
        if(capsule.length!=18||card.length!=18||result.length!=18)throw new IllegalArgumentException("Unknown native blur codec");
        float p=Math.max(0,Math.min(1,progress));
        for(int i=0;i<result.length;i++) {
            boolean discrete=i==0||i==1||i==3||i==17;
            result[i]=discrete?(p<.5f?capsule[i]:card[i]):capsule[i]+(card[i]-capsule[i])*p;
        }
    }
}
