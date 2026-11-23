package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.blur.MaterialBlurDefaults
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Recent
import io.github.suqi8.coui.kmp.layout.DialogButtonBar
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.popup.OverlayDropdownPopup
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun UiText(text: String, size: Int = 15, muted: Boolean = false, bold: Boolean = false,
    modifier: Modifier = Modifier) {
    val styles = COUITheme.textStyles
    val style = when {
        size >= 24 -> styles.headlineM
        bold || size >= 17 -> styles.headlineXS
        size >= 15 -> styles.bodyL
        else -> styles.bodyM
    }
    Text(text, modifier, style = style,
        color = if (muted) COUITheme.colorScheme.labelSecondary else COUITheme.colorScheme.labelPrimary)
}

@Composable
internal fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun SettingsMenuGroup(title: String?, content: CardListGroupScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) PreferenceCategory(title)
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            CardListGroup(content = content)
        }
    }
}

@Composable
internal fun SettingsMenuItem(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    ArrowPreference(title = title, summary = summary, startAction = { SettingsIcon(icon) }, onClick = onClick)
}

@Composable
internal fun SettingsAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    leadingIcon: ImageVector? = null, onClick: () -> Unit) {
    Button(onClick, modifier, enabled = enabled) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = COUITheme.textStyles.buttonL)
    }
}

internal class ControlHistory {
    private val entries = mutableStateMapOf<String, List<Any>>()
    fun <T : Any> register(key: String, value: T) { if (key !in entries) entries[key] = listOf(value) }
    fun <T : Any> record(key: String, before: T, after: T) {
        if (before != after) entries[key] = (listOf(before) + entries[key].orEmpty()).distinct().take(6)
    }
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> previous(key: String, value: T): List<T> = entries[key].orEmpty().filterNot { it == value } as List<T>
}

internal val LocalControlHistory = staticCompositionLocalOf { ControlHistory() }
internal val LocalControlScope = staticCompositionLocalOf { "" }

@Composable
internal fun <T : Any> rememberControlEdit(key: String, value: T, change: (T) -> Unit): (T) -> Unit {
    val history = LocalControlHistory.current
    SideEffect { history.register(key, value) }
    val current by rememberUpdatedState(value)
    val apply by rememberUpdatedState(change)
    return remember(history, key) {
        { next ->
            if (next != current) {
                history.record(key, current, next)
                apply(next)
            }
        }
    }
}

@Composable
internal fun <T : Any> ValueHistoryButton(title: String, values: List<T>, label: (T) -> String, restore: (T) -> Unit) {
    if (values.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val description = stringResource(R.string.coui_value_history, title)
    Box {
        IconButton(onClick = { expanded = true }, material = false, backgroundColor = Color.Transparent) {
            Icon(COUIIcons.Recent, description, Modifier.size(18.dp))
        }
        OverlayDropdownPopup(entry = DropdownEntry(values.map { value -> DropdownItem(label(value), onClick = { restore(value) }) },
            title = description), show = expanded, onDismiss = { expanded = false }, onDismissFinished = {},
            maxHeight = null, dropdownColors = DropdownDefaults.dropdownColors(), renderInRootScaffold = true)
    }
}

@Composable
internal fun SettingSwitch(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) {
    val key = LocalControlScope.current + ":switch:" + title
    val edit = rememberControlEdit(key, checked, change)
    val previous = LocalControlHistory.current.previous(key, checked)
    val on = stringResource(R.string.coui_history_on)
    val off = stringResource(R.string.coui_history_off)
    SwitchPreference(checked = checked, onCheckedChange = edit, title = title, summary = subtitle.takeIf(String::isNotBlank),
        endActions = { ValueHistoryButton(title, previous, { if (it) on else off }, edit) })
}

@Composable
internal fun SettingsDialog(title: String, dismiss: () -> Unit, positive: DialogButtonBarAction? = null,
    negative: DialogButtonBarAction? = null, neutral: DialogButtonBarAction? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val blur = settingsPanelBlur(MaterialBlurDefaults::dialog)
    OverlayDialog(show = true, title = title, onDismissRequest = dismiss,
        backgroundModifier = blur ?: Modifier,
        backgroundColor = if (blur == null) DialogDefaults.backgroundColor() else Color.Transparent,
        materialStroke = blur != null) {
        SettingsDialogBody(positive, negative, neutral, content)
    }
}

@Composable
internal fun SettingsDialogBody(positive: DialogButtonBarAction?, negative: DialogButtonBarAction?,
    neutral: DialogButtonBarAction? = null, content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .64f).dp
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        DialogButtonBar(negative = negative, positive = positive, neutral = neutral)
    }
}
