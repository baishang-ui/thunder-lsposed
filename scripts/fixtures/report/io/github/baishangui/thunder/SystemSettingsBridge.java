package io.github.baishangui.thunder;
import android.content.Context;
import android.os.Bundle;
/** JVM provider-routing probe. Real Kotlin patch validation is covered by Gradle tests. */
final class SystemSettingsBridge {
    static int calls;
    static Bundle call(Context context, String method, Bundle extras) {
        calls++;
        return new Bundle();
    }
}
