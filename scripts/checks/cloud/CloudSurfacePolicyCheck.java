package io.github.baishangui.thunder;

public final class CloudSurfacePolicyCheck {
    private static void check(boolean value){if(!value)throw new AssertionError("settled surface must release native ownership");}
    private static void close(float expected,float actual){if(Math.abs(expected-actual)>.001f)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args) {
        check(!CloudSurfacePolicy.keep(true,true,true,0));
        check(!CloudSurfacePolicy.keep(false,true,true,1));
        check(!CloudSurfacePolicy.keep(true,false,true,1));
        check(!CloudSurfacePolicy.keep(true,true,false,1));
        check(CloudSurfacePolicy.keep(true,true,true,.5f));
        close(60,CloudSurfacePolicy.radius(300,120,60,true,20,3));
        close(24,CloudSurfacePolicy.radius(300,120,60,true,8,3));
        close(60,CloudSurfacePolicy.radius(300,120,60,true,80,3));
        close(42,CloudSurfacePolicy.radius(300,120,42,false,8,3));
        close(30,CloudSurfacePolicy.radius(300,60,42,false,8,3));
        System.out.println("CloudSurfacePolicyCheck: 10 settled lifecycle/radius checks passed");
    }
}
