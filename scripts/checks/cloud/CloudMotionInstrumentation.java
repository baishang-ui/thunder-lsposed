package io.github.baishangui.thunder;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** A temporary test process supplies Android's normal application initialization. */
public final class CloudMotionInstrumentation extends Instrumentation {
    private boolean renderOnly;
    @Override public void onCreate(Bundle args){renderOnly=args!=null&&"true".equals(args.getString("render_only"));start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(()->{
                try{
                    CloudCapsuleContentDeviceCheck.main(new String[0]);
                    CloudCardFeedbackDeviceCheck.run(getTargetContext());
                    CloudInputBridgeDeviceCheck.main(new String[0]);
                    CloudTextHookDeviceCheck.main(new String[0]);
                    CloudMultiCardOpacityDeviceCheck.run(getTargetContext());
                }
                catch(Throwable error){failure.set(error);}
            });
            if(failure.get()!=null)throw new RuntimeException(failure.get());
            if(!renderOnly)CloudDismissalWindowDeviceCheck.run(this);
            result.putString("stream","Native capsule pixels and alpha-layer checks: PASS ("+CloudCapsuleContentDeviceCheck.checks+"); card ancestor/input checks: PASS ("+CloudCardFeedbackDeviceCheck.checks+"); media text checks: PASS ("+CloudTextHookDeviceCheck.checks+"); multi-card opacity checks: PASS ("+CloudMultiCardOpacityDeviceCheck.checks+"); dismissal window checks: "+(!renderOnly?"PASS ("+CloudDismissalWindowDeviceCheck.checks+")":"SKIP (--render-only)")+"\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error) {
            result.putString("stream",android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
