package io.github.baishangui.thunder

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.*
import io.github.suqi8.coui.kmp.menu.OverlayContextMenu
import io.github.suqi8.coui.kmp.preference.CardEntranceItem
import io.github.suqi8.coui.kmp.preference.CardEntrancePreference
import io.github.suqi8.coui.kmp.preference.CardEntranceType
import io.github.suqi8.coui.kmp.preference.ListPreferenceEntry
import io.github.suqi8.coui.kmp.preference.MultiSelectListPreference
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference

@Composable
internal fun FunctionWorkbench(settings: Settings, change: (Settings) -> Unit, open: (SettingsDestination) -> Unit) {
    val values = remember(settings) { settings.bundle() }
    val enabledGroups = StyleGroups.groups.count { values.getBoolean("style_${it.key}") }
    val compact = LocalDensity.current.fontScale <= 1.25f
    Column {
        PreferenceCategory(stringResource(R.string.workbench_system_effects))
        CardEntrancePreference(WorkbenchCatalog.pages.map { page ->
            CardEntranceItem(title = page.title, summary = page.description,
                type = if (page == SettingsPage.Panel || !compact) CardEntranceType.Large else CardEntranceType.Small,
                icon = { SettingsIcon(page.icon) }, onClick = { open(SettingsDestination(page)) })
        })
    }
    SettingsMenuGroup(null) {
        item {
            SwitchPreference(checked = settings.enabled, onCheckedChange = { change(settings.copy(enabled = it)) },
                title = stringResource(R.string.workbench_enable),
                summary = if (settings.enabled) stringResource(R.string.workbench_enabled_count, enabledGroups)
                    else stringResource(R.string.workbench_paused))
        }
    }
}

@Composable
internal fun WorkbenchTabs(sections: List<WorkbenchSection>, selected: WorkbenchSection, change: (WorkbenchSection) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TabRowWithContour(tabs = sections.map { stringResource(it.label) }, selectedTabIndex = sections.indexOf(selected),
            onTabSelected = { change(sections[it]) }, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp), minWidth = 60.dp)
    }
}

@Composable
internal fun WorkbenchEditor(section: WorkbenchSection, tile: String, selectTile: (String) -> Unit,
    target: String?, onTargetShown: () -> Unit, settings: Settings, change: (Settings) -> Unit,
    shortcuts: WorkbenchShortcuts, testNotification: () -> Unit) {
    val values = remember(settings) { workbenchValues(settings) }
    val style = WorkbenchCatalog.group(section, tile)
    val styleKey = "style_$style"
    val applied = values.getBoolean(styleKey)
    if (!settings.enabled) TopTips(stringResource(R.string.workbench_paused_editable))
    if (section == WorkbenchSection.Tiles) {
        SettingsMenuGroup(null) {
            item {
                OverlayDropdownPreference(title = stringResource(R.string.workbench_tile_type),
                    items = WorkbenchCatalog.tileGroups.map { StyleGroups.group(it).title },
                    selectedIndex = WorkbenchCatalog.tileGroups.indexOf(tile),
                    onSelectedIndexChange = { selectTile(WorkbenchCatalog.tileGroups[it]) })
            }
        }
    }
    val displayName = if (style == "panel") stringResource(R.string.workbench_apply_background) else StyleGroups.group(style).title
    SettingsMenuGroup(null) {
        item {
            SwitchPreference(checked = applied, title = displayName,
                summary = stringResource(if (applied) R.string.workbench_style_on else R.string.workbench_style_off),
                onCheckedChange = { change(SystemSettingsCatalog.patch(settings, styleKey, it, 0)) })
        }
    }
    if (section == WorkbenchSection.GestureSize || section == WorkbenchSection.GestureMaterial) {
        if (settings.hideGestureBar) TopTips(stringResource(R.string.workbench_gesture_hidden))
    }
    val sections = WorkbenchCatalog.parameters.filter { it.section == section && it.group == style }
        .filter { it.visible(values) || it.id == target }.groupBy { it.subsection }
    sections.forEach { (heading, parameters) ->
        SettingsMenuGroup(heading.ifBlank { stringResource(section.label) }) {
            parameters.forEach { parameter ->
                item(parameter.id) {
                    val focused = target == parameter.id
                    val requester = remember { BringIntoViewRequester() }
                    var placed by remember { mutableStateOf(false) }
                    if (focused) {
                        LaunchedEffect(placed) {
                            if (placed) requester.bringIntoView()
                        }
                        TopTips(stringResource(R.string.workbench_search_target, parameter.title),
                            onClose = onTargetShown)
                    }
                    Box(Modifier.fillMaxWidth().bringIntoViewRequester(requester).onGloballyPositioned { placed = true }) {
                        WorkbenchControl(parameter, settings, values, shortcuts, change)
                    }
                }
            }
        }
    }
    if (section == WorkbenchSection.Banner) {
        SettingsAction(stringResource(R.string.workbench_test_banner), Modifier.fillMaxWidth(),
            leadingIcon = COUIIcons.Messages, onClick = testNotification)
    }
    if (section == WorkbenchSection.StatusIcons && settings.hiddenStatusIcons != 0) {
        SettingsAction(stringResource(R.string.workbench_restore_icons), Modifier.fillMaxWidth()) {
            change(settings.copy(hiddenStatusIcons = 0))
        }
    }
}

private fun workbenchValues(settings: Settings) = settings.bundle().apply {
    putInt("cloud_app_color_strength", settings.cloudLock.maskStrength)
}

@Composable
private fun WorkbenchControl(parameter: WorkbenchParameter, settings: Settings, values: Bundle,
    shortcuts: WorkbenchShortcuts, change: (Settings) -> Unit, summary: String? = null) {
    val supportingText = listOfNotNull(summary, parameter.description.takeIf(String::isNotBlank),
        stringResource(R.string.workbench_prerequisite).takeIf { !parameter.visible(values) }).joinToString(" · ")
    val commit: (Any) -> Unit = { value ->
        val updated = parameter.edit(settings, value)
        if (updated != settings) {
            shortcuts.record(parameter.id)
            change(updated)
        }
    }
    PinnablePreference(parameter, shortcuts) {
        CompositionLocalProvider(LocalControlScope provides "workbench:${parameter.id}") {
            when {
                parameter.color -> SettingColor(parameter.title, parameter.number(values)) { commit(it) }
                parameter.choices != null -> SettingsChoice(parameter.title, parameter.choices.map { SettingOption(it) },
                    parameter.selected(values), summary = supportingText.takeIf(String::isNotBlank)) { commit(parameter.choiceValues[it]) }
                parameter.range != null -> {
                    val value = parameter.number(values).coerceIn(parameter.range)
                    if (parameter.range.last - parameter.range.first <= 16) {
                        NumberSetting(parameter.title, value, parameter.range, parameter.unit,
                            summary = supportingText.takeIf(String::isNotBlank)) { commit(it) }
                    } else SettingSlider(parameter.title, value, parameter.range, parameter.unit,
                        summary = supportingText.takeIf(String::isNotBlank)) { commit(it) }
                }
                else -> SettingSwitch(parameter.title, supportingText, parameter.checked(values)) { commit(it) }
            }
            }
    }
}

@Composable
private fun PinnablePreference(parameter: WorkbenchParameter, shortcuts: WorkbenchShortcuts, content: @Composable () -> Unit) {
    val pinned = parameter.id in shortcuts.pins
    val action = stringResource(if (pinned) R.string.workbench_unpin else R.string.workbench_pin)
    val update = { shortcuts.setPinned(parameter.id, !pinned) }
    val entries = if (parameter.pinAvailable) listOf(DropdownEntry(listOf(DropdownItem(action,
        onClick = update, icon = { modifier -> Icon(if (pinned) COUIIcons.Unpin else COUIIcons.Pin, null, modifier) }))))
        else emptyList()
    val semantics = if (parameter.pinAvailable) Modifier.semantics(mergeDescendants = true) {
        customActions = listOf(CustomAccessibilityAction(action) { update(); true })
    } else Modifier
    OverlayContextMenu(entries = entries, modifier = semantics, content = content)
}

@Composable
internal fun FavoritesWorkbench(settings: Settings, shortcuts: WorkbenchShortcuts, change: (Settings) -> Unit,
    open: (SettingsDestination) -> Unit, openFunctions: () -> Unit) {
    val values = remember(settings) { workbenchValues(settings) }
    val pinned = shortcuts.pins.mapNotNull(WorkbenchCatalog::parameter)
    if (pinned.isEmpty()) {
        SettingsGroup {
            UiText(stringResource(R.string.workbench_favorites_empty), 18, bold = true)
            UiText(stringResource(R.string.workbench_favorites_hint), muted = true)
            SettingsAction(stringResource(R.string.workbench_browse), Modifier.fillMaxWidth(), onClick = openFunctions)
        }
    } else {
        SettingsMenuGroup(stringResource(R.string.workbench_pinned)) {
            pinned.forEach { parameter ->
                item(parameter.id) {
                    val path = parameter.breadcrumb()
                    val styleApplies = values.getBoolean("style_${parameter.group}")
                    val summary = if (settings.enabled && styleApplies) path else
                        stringResource(R.string.workbench_saved_only, path)
                    WorkbenchControl(parameter, settings, values, shortcuts, change, summary = summary)
                }
            }
        }
        SettingsMenuGroup(null) {
            item {
                MultiSelectListPreference(entries = pinned.map { ListPreferenceEntry(it.title, it.breadcrumb()) },
                    selectedIndices = pinned.indices.toSet(),
                    onSelectedIndicesChange = { selected -> shortcuts.keepPins(pinned.filterIndexed { index, _ -> index in selected }.map { it.id }) },
                    title = stringResource(R.string.workbench_edit_pins), summary = stringResource(R.string.workbench_edit_pins_hint),
                    confirmButtonText = stringResource(R.string.workbench_done), cancelButtonText = stringResource(R.string.coui_cancel),
                    showValue = false)
            }
        }
    }
    val recent = shortcuts.recent.filterNot { it in shortcuts.pins }.mapNotNull(WorkbenchCatalog::parameter)
    if (recent.isNotEmpty()) SettingsMenuGroup(stringResource(R.string.workbench_recent)) {
        recent.forEach { parameter ->
            item(parameter.id) {
                SettingsMenuItem(parameter.title, "${parameter.breadcrumb()} · ${parameter.currentValue(values)}",
                    parameter.section.page.icon) { open(parameter.destination) }
            }
        }
    }
}

@Composable
internal fun WorkbenchSearchResults(query: String, settings: Settings, open: (SettingsDestination) -> Unit) {
    val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val values = remember(settings) { workbenchValues(settings) }
    val modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp)
    val pageResults = SettingsPage.entries.filter { matches(terms, it.title, it.description) }
    val parameterResults = if (terms.isEmpty()) emptyList() else WorkbenchCatalog.parameters.mapNotNull { parameter ->
        val breadcrumb = parameter.breadcrumb()
        val queryTarget = listOf(parameter.title, breadcrumb, parameter.description, parameter.key, parameter.key.replace("_", ""))
        if (matches(terms, *queryTarget.toTypedArray())) parameter to breadcrumb else null
    }.sortedByDescending { (parameter, _) -> terms.any { parameter.title.contains(it, ignoreCase = true) } }.take(24)
    if (pageResults.isEmpty() && parameterResults.isEmpty()) {
        val prompt = stringResource(R.string.coui_search_prompt)
        EmptyState(title = if (terms.isEmpty()) prompt else stringResource(R.string.coui_search_empty),
            subtitle = prompt.takeIf { terms.isNotEmpty() }, modifier = modifier.fillMaxHeight())
    } else {
        val scroll = rememberScrollState()
        val focus = LocalFocusManager.current
        LaunchedEffect(scroll.isScrollInProgress) { if (scroll.isScrollInProgress) focus.clearFocus() }
        Column(modifier.verticalScroll(scroll).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pageResults.isNotEmpty()) SettingsMenuGroup(stringResource(R.string.workbench_features)) {
                pageResults.forEach { page ->
                    item(page) { SettingsMenuItem(page.title, page.description, page.icon) { open(SettingsDestination(page)) } }
                }
            }
            if (parameterResults.isNotEmpty()) SettingsMenuGroup(stringResource(R.string.workbench_parameters)) {
                parameterResults.forEach { (parameter, path) ->
                    item(parameter.id) {
                        SettingsMenuItem(parameter.title, "$path · ${parameter.currentValue(values)}", parameter.section.page.icon) {
                            open(parameter.destination)
                        }
                    }
                }
            }
        }
    }
}

private fun matches(terms: List<String>, vararg text: String) =
    terms.isNotEmpty() && terms.all { term -> text.any { it.contains(term, ignoreCase = true) } }

@Composable
private fun WorkbenchParameter.breadcrumb(): String {
    val sectionLabel = stringResource(section.label)
    val target = if (section == WorkbenchSection.Tiles) " / ${StyleGroups.group(group).title}" else ""
    return "${section.page.title} / $sectionLabel$target"
}

@Composable
private fun WorkbenchParameter.currentValue(values: Bundle): String = when {
    color -> "#%08X".format(number(values))
    choices != null -> choices[selected(values)]
    range != null -> "${number(values)}$unit"
    else -> stringResource(if (checked(values)) R.string.workbench_on else R.string.workbench_off)
}
