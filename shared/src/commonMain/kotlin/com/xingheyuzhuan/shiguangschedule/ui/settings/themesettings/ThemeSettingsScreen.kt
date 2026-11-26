package com.xingheyuzhuan.shiguangschedule.ui.settings.themesettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.data.model.AppThemeMode
import com.xingheyuzhuan.shiguangschedule.ui.components.AdvancedColorPicker
import com.xingheyuzhuan.shiguangschedule.ui.components.ColorPickerConfig
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtSurfaceCard
import com.xingheyuzhuan.shiguangschedule.ui.cumt.CumtTopBar
import com.xingheyuzhuan.shiguangschedule.ui.settings.SettingsViewModel
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import com.xingheyuzhuan.shiguangschedule.ui.theme.supportsDynamicColor
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.action_reset
import shiguangschedule.shared.generated.resources.custom_color_title
import shiguangschedule.shared.generated.resources.dark_primary_color
import shiguangschedule.shared.generated.resources.dynamic_color_desc
import shiguangschedule.shared.generated.resources.dynamic_color_title
import shiguangschedule.shared.generated.resources.light_primary_color
import shiguangschedule.shared.generated.resources.refresh_24px
import shiguangschedule.shared.generated.resources.theme_color_hint
import shiguangschedule.shared.generated.resources.theme_mode_label
import shiguangschedule.shared.generated.resources.theme_settings_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val settings = uiState.appSettings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CumtTopBar(
                title = stringResource(Res.string.theme_settings_title),
                subtitle = "支持浅色 / 深色 / 跟随系统切换与个性化主色",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 主题模式选择
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionHeader(stringResource(Res.string.theme_mode_label))
                    ThemeModeSelector(
                        selectedMode = settings.themeMode,
                        onModeSelected = { viewModel.onThemeModeChanged(it) }
                    )
                }
            }

            // 软件全局界面字号调节（独立于课表网格字体）
            AppFontScaleCard(
                currentScale = settings.appUiFontScale,
                onScaleChanged = viewModel::onAppUiFontScaleChanged
            )

            // 动态取色 (利用跨平台 supportsDynamicColor 统一处理)
            if (supportsDynamicColor) {
                DynamicColorToggle(
                    enabled = settings.useDynamicColor,
                    onEnabledChange = { viewModel.onUseDynamicColorChanged(it) }
                )
            }

            // 自定义主色调选择
            val showColorPicker = !supportsDynamicColor || !settings.useDynamicColor

            AnimatedVisibility(
                visible = showColorPicker,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SectionHeader(stringResource(Res.string.custom_color_title))

                    val isDark = LocalIsDarkTheme.current

                    if (isDark) {
                        ColorPickerItem(
                            label = stringResource(Res.string.dark_primary_color),
                            currentColor = Color(settings.customPrimaryColor.dark),
                            onColorChanged = { viewModel.onCustomDarkPrimaryChanged(it) },
                            onReset = { viewModel.onCustomDarkPrimaryChanged() }
                        )
                    } else {
                        ColorPickerItem(
                            label = stringResource(Res.string.light_primary_color),
                            currentColor = Color(settings.customPrimaryColor.light),
                            onColorChanged = { viewModel.onCustomLightPrimaryChanged(it) },
                            onReset = { viewModel.onCustomLightPrimaryChanged() }
                        )
                    }

                    Text(
                        text = stringResource(Res.string.theme_color_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerItem(
    label: String,
    currentColor: Color,
    onColorChanged: (Color) -> Unit,
    onReset: () -> Unit
) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    CumtSurfaceCard(
        onClick = { showSheet = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(currentColor)
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 40.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    TextButton(onClick = onReset) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.action_reset))
                    }
                }
                AdvancedColorPicker(
                    initialColor = currentColor,
                    onColorChanged = onColorChanged,
                    config = ColorPickerConfig(
                        showAlpha = false,
                        showInputMode = true
                    )
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selectedMode: AppThemeMode,
    onModeSelected: (AppThemeMode) -> Unit
) {
    val modes = AppThemeMode.entries

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
            ) {
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun DynamicColorToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    CumtSurfaceCard(
        onClick = { onEnabledChange(!enabled) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.dynamic_color_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.dynamic_color_desc), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun AppFontScaleCard(
    currentScale: Float,
    onScaleChanged: (Float) -> Unit
) {
    val percent = (currentScale * 100f).roundToInt()
    val presets = listOf(
        "紧凑" to 0.90f,
        "标准" to 1.00f,
        "舒适" to 1.10f,
        "大字" to 1.20f
    )

    CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader("软件界面字号")
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "仅调节菜单、设置、矿大校园与 AI 对话等界面字号，不影响周课表网格字体。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = currentScale.coerceIn(0.85f, 1.25f),
                onValueChange = { raw ->
                    val stepped = (raw * 20f).roundToInt() / 20f
                    onScaleChanged(stepped.coerceIn(0.85f, 1.25f))
                },
                valueRange = 0.85f..1.25f,
                steps = 7
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (label, value) ->
                    val selected = kotlin.math.abs(currentScale - value) < 0.02f
                    FilterChip(
                        selected = selected,
                        onClick = { onScaleChanged(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "实时预览 · 矿大时光课表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "拖动上方滑块可实时感受界面文字大小变化，周课表内的课程卡片字号保持独立不受影响。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}