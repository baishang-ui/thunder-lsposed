package io.github.baishangui.thunder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.layout.DialogButtonBarAction
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.ListPreference
import io.github.suqi8.coui.kmp.preference.ListPreferenceEntry
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.window.WindowDialog
import kotlin.math.roundToInt

internal data class SettingOption(val title: String, val summary: String = "")

@Composable
internal fun SettingsChoice(title: String, options: List<SettingOption>, selected: Int,
    summary: String? = null,
    change: (Int) -> Unit) {
    val key = LocalControlScope.current + ":choice:" + title
    val edit = rememberControlEdit(key, selected, change)
    val previous = LocalControlHistory.current.previous(key, selected).filter { it in options.indices }
    val historyButton: (@Composable () -> Unit)? = if (previous.isEmpty()) null else {
        { ValueHistoryButton(title, previous, { options[it].title }, edit) }
    }
    if (options.all { it.summary.isBlank() } && options.size <= 4) {
        OverlayDropdownPreference(items = options.map { it.title }, title = title, summary = summary, selectedIndex = selected,
            startAction = historyButton, onSelectedIndexChange = edit)
    } else {
        ListPreference(entries = options.map { ListPreferenceEntry(it.title, it.summary.takeIf(String::isNotBlank)) },
            selectedIndex = selected, onSelectedIndexChange = edit, title = title, summary = summary,
            startAction = historyButton, cancelButtonText = "取消")
    }
}

@Composable
internal fun NumberSetting(title: String, value: Int, range: IntRange, unit: String,
    summary: String? = null, change: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val key = LocalControlScope.current + ":number:" + title
    val edit = rememberControlEdit(key, value, change)
    val previous = LocalControlHistory.current.previous(key, value).filter { it in range }
    ArrowPreference(title = title, summary = summary, onClick = { open = true }, endActions = {
        UiText("$value$unit", 13, muted = true)
        ValueHistoryButton(title, previous, { "$it$unit" }, edit)
    })
    if (open) NumberInputDialog(title, value, range, unit, stepper = true, dismiss = { open = false }) {
        edit(it)
        open = false
    }
}

@Composable
internal fun SettingSlider(title: String, value: Int, range: IntRange, unit: String = "", summary: String? = null,
    change: (Int) -> Unit) {
    var preview by remember(value) { mutableFloatStateOf(value.toFloat()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val key = LocalControlScope.current + ":slider:" + title
    val edit = rememberControlEdit(key, value, change)
    val previous = LocalControlHistory.current.previous(key, value).filter { it in range }
    fun commit(next: Int) {
        preview = next.toFloat()
        edit(next)
    }
    SliderPreference(value = preview, onValueChange = { preview = it }, title = title, summary = summary,
        endActions = { ValueHistoryButton(title, previous, { "$it$unit" }, ::commit) },
        valueText = "${preview.roundToInt()}$unit", onClick = { editing = true },
        valueRange = range.first.toFloat()..range.last.toFloat(), showKeyPoints = false,
        onValueChangeFinished = { commit(preview.roundToInt().coerceIn(range)) })
    if (editing) NumberInputDialog(title, preview.roundToInt(), range, unit, dismiss = { editing = false }) {
        commit(it)
        editing = false
    }
}

@Composable
private fun NumberInputDialog(title: String, initial: Int, range: IntRange, unit: String,
    stepper: Boolean = false, dismiss: () -> Unit, confirm: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial.toString()) }
    val parsed = parseSettingNumber(input, range)
    WindowDialog(show = true, title = title, onDismissRequest = dismiss) {
        val keyboard = LocalSoftwareKeyboardController.current
        val submit: () -> Unit = { parsed?.let { keyboard?.hide(); confirm(it) } }
        SettingsDialogBody(
            positive = DialogButtonBarAction("确定", enabled = parsed != null, recommended = true, onClick = submit),
            negative = DialogButtonBarAction("取消", onClick = dismiss),
        ) {
            InputView(value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
                label = "范围：${range.first}–${range.last}$unit",
                errorMessage = if (parsed == null) "请输入范围内的整数" else "",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
            if (stepper) Stepper(value = parsed ?: initial, onValueChange = { input = it.toString() },
                modifier = Modifier.align(Alignment.CenterHorizontally), minValue = range.first, maxValue = range.last)
        }
    }
}

@Composable
internal fun SettingColor(title: String, color: Int, opaque: Boolean = false, change: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val key = LocalControlScope.current + ":color:" + title
    val edit = rememberControlEdit(key, color, change)
    val previous = LocalControlHistory.current.previous(key, color)
    ArrowPreference(title = title, summary = if (opaque) colorHex(color, true)
        else "${colorHex(color, false)} · 不透明度 ${TintColor.percent(color)}%",
        startAction = { ColorPreview(color, Modifier.size(24.dp)) }, onClick = { open = true },
        endActions = { ValueHistoryButton(title, previous, { colorHex(it, opaque) }, edit) })
    if (open) ColorDialog(color, opaque, { open = false }) { edit(it); open = false }
}

private fun colorHex(color: Int, opaque: Boolean) =
    if (opaque) "#%06X".format(color and 0xffffff) else "#%08X".format(color)

@Composable
private fun ColorPreview(color: Int, modifier: Modifier = Modifier) {
    Surface(modifier, shape = CircleShape, color = Color(color),
        border = BorderStroke(1.dp, COUITheme.colorScheme.outline)) {}
}

@Composable
private fun ColorDialog(initial: Int, opaque: Boolean, dismiss: () -> Unit, confirm: (Int) -> Unit) {
    var selection by rememberSaveable(stateSaver = PickerColor.saver) { mutableStateOf(PickerColor.from(initial)) }
    var input by rememberSaveable { mutableStateOf(colorHex(initial, opaque)) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val accepted = if (mode == 1) parsePickerColor(input, opaque) else selection.argb
    fun update(next: PickerColor) {
        selection = if (opaque) next.copy(alpha = 1f) else next
        input = colorHex(selection.argb, opaque)
    }
    WindowDialog(show = true, title = "选择颜色", onDismissRequest = dismiss) {
        val keyboard = LocalSoftwareKeyboardController.current
        SettingsDialogBody(
            positive = DialogButtonBarAction("确定", enabled = accepted != null, recommended = true) {
                accepted?.let { keyboard?.hide(); confirm(it) }
            },
            negative = DialogButtonBarAction("取消", onClick = dismiss),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ColorPreview(selection.argb, Modifier.size(36.dp))
                UiText(colorHex(selection.argb, opaque), bold = true)
            }
            TabRowWithContour(tabs = listOf("调色", "HEX"), selectedTabIndex = mode,
                onTabSelected = { keyboard?.hide(); input = colorHex(selection.argb, opaque); mode = it },
                modifier = Modifier.fillMaxWidth())
            if (mode == 1) {
                InputView(value = input, onValueChange = { text ->
                    input = text
                    parsePickerColor(text, opaque)?.let { selection = PickerColor.from(it, selection) }
                }, modifier = Modifier.fillMaxWidth(), label = if (opaque) "RRGGBB" else "AARRGGBB / RRGGBB",
                    errorMessage = if (accepted == null) "请输入有效的 HEX 颜色" else "",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            } else {
                val swatches = listOf(0xff0080ff, 0xff2aaf68, 0xffffa21a, 0xffef5350, 0xffa772ee,
                    0xff000000, 0xffffffff).map { Color(it.toInt()) }
                val rgb = selection.argb or 0xff000000.toInt()
                ColorSwatchPicker(swatches, swatches.indexOfFirst { it.toArgb() == rgb }, { index ->
                    update(PickerColor.from(swatches[index].copy(alpha = selection.alpha).toArgb(), selection))
                }, Modifier.horizontalScroll(rememberScrollState()).semantics { contentDescription = "常用颜色" })
                if (opaque) {
                    UiText("色相", 13, muted = true)
                    HsvHueSlider(selection.hue, { update(selection.copy(hue = it * 360f)) })
                    UiText("饱和度", 13, muted = true)
                    HsvSaturationSlider(selection.hue, selection.saturation, { update(selection.copy(saturation = it)) })
                    UiText("亮度", 13, muted = true)
                    HsvValueSlider(selection.hue, selection.saturation, selection.brightness, { update(selection.copy(brightness = it)) })
                } else {
                    ColorPicker(Color(selection.argb), { update(PickerColor.from(it.toArgb(), selection)) },
                        Modifier.fillMaxWidth(), showPreview = false)
                }
            }
        }
    }
}
