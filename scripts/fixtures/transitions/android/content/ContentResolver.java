package android.content;
public class ContentResolver {
 public static android.os.IBinder endpoint;public static int calls;public static android.os.Bundle response=android.os.Bundle.EMPTY,last;
 public android.os.Bundle call(android.net.Uri uri,String method,String arg,android.os.Bundle data){if(method.equals("overlay-register")){endpoint=data.getBinder("endpoint");return android.os.Bundle.EMPTY;}if(method.equals("overlay-endpoint")){var b=new android.os.Bundle();b.putBinder("endpoint",endpoint);return b;}calls++;last=data;return response;}
}
