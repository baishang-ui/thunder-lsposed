package io.github.baishangui.thunder

import org.junit.Assert.*
import org.junit.Test

class SettingsEditorStateTest {
    @Test fun systemSettingsSaveRefreshesTheOpenAppWithoutWritingBack() {
        val original = Settings()
        val editor = SettingsEditorState(original)
        val external = SystemSettingsCatalog.patch(original, "panel_radius", 137, 0)

        editor.acceptSaved(external, latest = external)

        assertEquals(external, editor.saved)
        assertEquals(external, editor.draft)
        assertNull(editor.pendingWrite())
        // The next App edit must retain the value changed in system Settings.
        editor.draft = editor.draft.copy(enabled = false)
        assertEquals(external.copy(enabled = false), editor.pendingWrite())
    }

    @Test fun externalImportAndResetReplaceTheAppDraft() {
        val original = Settings(panel = Area(blur = true, radius = 137))
        val editor = SettingsEditorState(original)
        val imported = Settings.parse(Settings(cloud = false, margin = 42).json())
        for (external in listOf(imported, Settings())) {
            editor.acceptSaved(external, latest = external)
            assertEquals(external, editor.draft)
            assertNull(editor.pendingWrite())
        }
    }

    @Test fun delayedSaveDoesNotReplaceANewerSubmittedEdit() {
        val original = Settings()
        val editor = SettingsEditorState(original)
        val older = original.copy(enabled = false)
        val newer = older.copy(margin = 42)
        editor.draft = older
        assertEquals(older, editor.pendingWrite())
        editor.draft = newer
        assertEquals(newer, editor.pendingWrite())

        editor.acceptSaved(older, latest = newer)

        assertEquals(older, editor.saved)
        assertEquals(newer, editor.draft)
        assertNull(editor.pendingWrite())
        editor.acceptSaved(newer, latest = newer)
        assertEquals(newer, editor.saved)
        assertNull(editor.pendingWrite())
    }

    @Test fun localInputBeforeSubmissionSurvivesAnExternalSave() {
        val original = Settings()
        val editor = SettingsEditorState(original)
        val local = original.copy(margin = 42)
        val external = original.copy(enabled = false)
        editor.draft = local

        editor.acceptSaved(external, latest = external)

        assertEquals(external, editor.saved)
        assertEquals(local, editor.draft)
        assertEquals(local, editor.pendingWrite())
    }

    @Test fun recreatedEditorUsesTheWritersLatestSnapshotWithoutResubmitting() {
        val persisted = Settings()
        val pending = persisted.copy(enabled = false)
        val editor = SettingsEditorState(persisted, pending)
        assertEquals(persisted, editor.saved)
        assertEquals(pending, editor.draft)
        assertNull(editor.pendingWrite())
        editor.acceptSaved(pending, latest = pending)
        assertEquals(pending, editor.saved)
    }
}
