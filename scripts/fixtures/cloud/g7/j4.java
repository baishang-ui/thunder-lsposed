package g7;
public class j4 {
    public static void f(android.content.res.Resources resources,android.view.View view,float radius,boolean software,int type,Float weight){
        view.setOutlineProvider(new com.oplus.systemui.plugins.common.util.j(null,new kotlin.Triple(type,radius*resources.getDisplayMetrics().density,weight)));
        view.setClipToOutline(true);
    }
}
