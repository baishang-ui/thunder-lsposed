package io.github.baishangui.thunder;
/** Android rendering boundary; real text layers have a separate Android probe. */
final class CloudTextRenderer { static void install(){} static boolean owns(android.view.View view){return false;} }
