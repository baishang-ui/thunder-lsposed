package io.github.baishangui.thunder

/** Invalid or out-of-range edits stay in the dialog and never reach saved settings. */
internal fun parseSettingNumber(input: String, range: IntRange): Int? =
    input.trim().toIntOrNull()?.takeIf { it in range }
