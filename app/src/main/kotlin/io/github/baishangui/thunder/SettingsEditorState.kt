package io.github.baishangui.thunder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Tracks displayed edits separately from writes already requested from SettingsWriter. */
internal class SettingsEditorState(saved: Settings, draft: Settings = saved) {
    var saved by mutableStateOf(saved)
        private set
    var draft by mutableStateOf(draft)
    private var lastRequested = draft

    fun pendingWrite(): Settings? {
        if (lastRequested == draft) return null
        lastRequested = draft
        return draft
    }

    fun acceptSaved(settings: Settings, latest: Settings?) {
        saved = settings
        // A completed external write becomes the displayed baseline. Never echo it
        // back as a fresh App edit, or replace newer local input with a late result.
        if (settings == latest && draft == lastRequested) {
            lastRequested = settings
            draft = settings
        }
    }
}
