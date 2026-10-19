package io.github.baishangui.thunder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NumberPicker
import top.yukonga.miuix.kmp.basic.SpinnerDefaults
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.SpinnerItemImpl
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape

internal data class SettingOption(val title: String, val summary: String = "")

/** A preference row and the library's own dialog-mode spinner items. */
@Composable internal fun SettingsChoice(title: String, options: List<SettingOption>, selected: Int,
    change: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val history = LocalControlHistory.current
    val key = LocalControlScope.current + ":choice:" + title
    SideEffect { history.register(key, selected) }
    Column(Modifier.fillMaxWidth()) {
        InputPreference(title, options[selected].title) { open = true }
        UndoFooter(title, history.original(key, selected)?.let { { change(it) } })
    }
    if (open) SettingsDialog(title, { open = false }, footer = {
        LiquidAction("取消", Modifier.fillMaxWidth()) { open = false }
    }) {
        Column(Modifier.fillMaxWidth().semantics { selectableGroup() },
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, option ->
                val choose: () -> Unit = {
                    history.record(key, selected, index)
                    if (index != selected) change(index)
                    open = false
                }
                Box(Modifier.clip(miuixShape(12.dp)).clearAndSetSemantics {
                    role = Role.RadioButton
                    this.selected = selected == index
                    contentDescription = listOf(option.title, option.summary).filter { it.isNotBlank() }.joinToString("，")
                    onClick { choose(); true }
                }) {
                    SpinnerItemImpl(entry = SpinnerEntry(title = option.title,
                        summary = option.summary.takeIf { it.isNotBlank() }),
                        entryCount = options.size, isSelected = selected == index, index = index,
                        spinnerColors = SpinnerDefaults.dialogSpinnerColors(), dialogMode = true,
                        onSelectedIndexChange = { choose() })
                }
            }
        }
    }
}

@Composable internal fun InputPreference(title: String, summary: String, inlineValue: Boolean = false,
    onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val inline = inlineValue && maxWidth >= 240.dp && LocalDensity.current.fontScale <= 1.3f
        BasicComponent(title = title, summary = summary.takeUnless { inline },
            modifier = Modifier.clip(miuixShape(12.dp)).semantics {
                role = Role.Button
                if (inline) stateDescription = summary
            }, insideMargin = PaddingValues(8.dp),
            endActions = {
                if (inline) UiText(summary, 14, muted = true)
                Icon(MiuixIcons.Regular.ChevronForward, contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp).size(18.dp), tint = LocalGlass.current.muted)
            }, onClick = onClick)
    }
}

/** Small integer ranges use a wheel instead of a long slider for a handful of steps. */
@Composable internal fun NumberSetting(title: String, value: Int, range: IntRange, unit: String,
    change: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val history = LocalControlHistory.current
    val key = LocalControlScope.current + ":number:" + title
    SideEffect { history.register(key, value) }
    Column(Modifier.fillMaxWidth()) {
        InputPreference(title, "$value$unit", inlineValue = true) { open = true }
        UndoFooter(title, history.original(key, value)?.let { { change(it) } })
    }
    if (open) NumberInputDialog(title, value, range, unit, wheel = true, dismiss = { open = false }) {
        history.record(key, value, it)
        if (it != value) change(it)
        open = false
    }
}

@Composable internal fun NumberInputDialog(title: String, initial: Int, range: IntRange, unit: String,
    wheel: Boolean = false, dismiss: () -> Unit, confirm: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial.toString()) }
    val parsed = parseSettingNumber(input, range)
    val errorText = "请输入 ${range.first}–${range.last}$unit 的整数"
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { parsed?.let { keyboard?.hide(); confirm(it) } }
    SettingsDialog(title, { keyboard?.hide(); dismiss() }, footer = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidAction("取消", Modifier.weight(1f)) { keyboard?.hide(); dismiss() }
            LiquidAction("确定", Modifier.weight(1f), selected = true, enabled = parsed != null) { submit() }
        }
    }) {
        if (wheel) {
            NumberPicker(value = parsed ?: initial, onValueChange = { input = it.toString() },
                range = range, label = { "$it$unit" }, visibleItemCount = 3,
                itemHeight = maxOf(48.dp, with(LocalDensity.current) { 40.sp.toDp() }),
                modifier = Modifier.semantics {
                    contentDescription = title
                    progressBarRangeInfo = ProgressBarRangeInfo((parsed ?: initial).toFloat(),
                        range.first.toFloat()..range.last.toFloat(), range.last - range.first - 1)
                    setProgress {
                        if (it.isFinite()) input = it.toInt().coerceIn(range).toString()
                        true
                    }
                })
        } else {
            TextField(value = input, onValueChange = { input = it },
                label = title + unit, singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { if (parsed == null) error(errorText) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }))
            UiText(if (parsed == null) errorText else "范围：${range.first}–${range.last}$unit",
                12, muted = parsed != null, color = if (parsed == null) MiuixTheme.colorScheme.error else null)
        }
    }
}
