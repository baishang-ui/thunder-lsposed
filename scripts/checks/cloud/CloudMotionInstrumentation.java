package io.github.baishangui.thunder;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** A temporary test process supplies Android's normal application initialization. */
public final class CloudMotionInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args){start();}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
            runOnMainSync(()->{
                try{
                    CloudCapsuleContentDeviceCheck.main(new String[0]);
                    CloudCardFeedbackDeviceCheck.run(getTargetContext());
                    CloudInputBridgeDeviceCheck.main(new String[0]);
                }
                catch(Throwable error){failure.set(error);}
            });
            if(failure.get()!=null)throw new RuntimeException(failure.get());
            result.putString("stream","Native capsule pixels and alpha-layer checks: PASS ("+CloudCapsuleContentDeviceCheck.checks+"); card ancestor/input checks: PASS ("+CloudCardFeedbackDeviceCheck.checks+")\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error) {
            result.putString("stream",android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED,result);
        }
    }
}
