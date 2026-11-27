package com.xingheyuzhuan.shiguangschedule.ui.settings.additional

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.data.model.StartScreen
import com.xingheyuzhuan.shiguangschedule.tool.PlatformUpdateStrategy
import com.xingheyuzhuan.shiguangschedule.tool.UpdateDownloadState
import com.xingheyuzhuan.shiguangschedule.tool.UpdatePlatform
import com.xingheyuzhuan.shiguangschedule.tool.UpdateStatus
import org.jetbrains.compose.resources.stringResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.action_cancel
import shiguangschedule.shared.generated.resources.action_confirm
import shiguangschedule.shared.generated.resources.btn_download_update
import shiguangschedule.shared.generated.resources.dialog_checking_update
import shiguangschedule.shared.generated.resources.dialog_current_version_latest
import shiguangschedule.shared.generated.resources.dialog_new_version_found
import shiguangschedule.shared.generated.resources.dialog_select_start_screen
import shiguangschedule.shared.generated.resources.dialog_select_update_channel
import shiguangschedule.shared.generated.resources.dialog_update_check_failed
import shiguangschedule.shared.generated.resources.label_error_message
import shiguangschedule.shared.generated.resources.label_version_prefix
import shiguangschedule.shared.generated.resources.tip_please_wait

/**
 * 启动页面选择弹窗
 */
@Composable
fun StartScreenSelectionDialog(
    showDialog: Boolean,
    currentSelected: StartScreen,
    onDismiss: () -> Unit,
    onConfirm: (StartScreen) -> Unit
) {
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_select_start_screen)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StartScreen.entries.forEach { screen ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(screen) },
                        headlineContent = { Text(stringResource(screen.labelRes)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            RadioButton(
                                selected = screen == currentSelected,
                                onClick = { onConfirm(screen) }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

/**
 * 更新渠道选择弹窗
 */
@Composable
fun ChannelSelectionDialog(
    showDialog: Boolean,
    currentSelected: UpdatePlatform,
    onDismiss: () -> Unit,
    onConfirm: (UpdatePlatform) -> Unit
) {
    if (!showDialog) return

    var selectedPlatform by remember(currentSelected) { mutableStateOf(currentSelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_select_update_channel)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                UpdatePlatform.entries.forEach { platform ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlatform = platform },
                        headlineContent = { Text(text = platform.title) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            RadioButton(
                                selected = platform == selectedPlatform,
                                onClick = { selectedPlatform = platform }
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedPlatform) }) {
                Text(stringResource(Res.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}

private fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0L) return "0.0 MB"
    val tenths = (bytes * 10L) / (1024L * 1024L)
    return "${tenths / 10}.${tenths % 10} MB"
}

/**
 * 更新检查结果弹窗（支持应用内下载进度、国内加速线路切换、自动安装与忽略版本）
 */
@Composable
fun UpdateResultDialog(
    showDialog: Boolean,
    updateStatus: UpdateStatus,
    downloadState: UpdateDownloadState = UpdateDownloadState.Idle,
    onDismiss: () -> Unit,
    onDownloadClick: (UpdateStatus.Found) -> Unit,
    onBrowserDownloadClick: ((String) -> Unit)? = null,
    onIgnoreVersion: ((String) -> Unit)? = null
) {
    if (!showDialog || updateStatus is UpdateStatus.Idle || updateStatus is UpdateStatus.NotSupported) return

    if (updateStatus is UpdateStatus.Checking) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(Res.string.dialog_checking_update)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(Res.string.tip_please_wait))
                }
            },
            confirmButton = {}
        )
        return
    }

    if (updateStatus is UpdateStatus.Found) {
        val isDownloading = downloadState is UpdateDownloadState.Downloading
        val isInstalling = downloadState is UpdateDownloadState.Installing
        val isBusy = isDownloading || isInstalling

        AlertDialog(
            onDismissRequest = { if (!isBusy) onDismiss() },
            title = { Text(stringResource(Res.string.dialog_new_version_found, updateStatus.versionName)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = updateStatus.changelog,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    when (downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (downloadState.progress >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = downloadState.sourceLabel.ifBlank { "正在下载安装包…" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val sizeLabel = if (downloadState.totalBytes > 0L) {
                                        val pct = (downloadState.progress * 100f).toInt().coerceIn(0, 100)
                                        "$pct% · ${formatBytesMb(downloadState.downloadedBytes)} / ${formatBytesMb(downloadState.totalBytes)}"
                                    } else {
                                        formatBytesMb(downloadState.downloadedBytes)
                                    }
                                    Text(
                                        text = sizeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        is UpdateDownloadState.Installing -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "下载完成，正在调起系统安装器…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Text(
                                text = "下载失败：${downloadState.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        UpdateDownloadState.Idle -> Unit
                    }
                }
            },
            confirmButton = {
                if (!isBusy) {
                    Button(onClick = { onDownloadClick(updateStatus) }) {
                        val label = when {
                            downloadState is UpdateDownloadState.Error -> "重试下载"
                            PlatformUpdateStrategy.supportsInAppInstall && updateStatus.isDirectDownload -> "立即更新"
                            else -> stringResource(Res.string.btn_download_update)
                        }
                        Text(label)
                    }
                }
            },
            dismissButton = {
                if (!isBusy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (onBrowserDownloadClick != null && PlatformUpdateStrategy.supportsInAppInstall && updateStatus.isDirectDownload) {
                            TextButton(onClick = { onBrowserDownloadClick(updateStatus.releasePageUrl) }) {
                                Text("浏览器下载")
                            }
                        }
                        if (onIgnoreVersion != null) {
                            TextButton(onClick = { onIgnoreVersion(updateStatus.versionName) }) {
                                Text("忽略此版本")
                            }
                        } else {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(Res.string.action_cancel))
                            }
                        }
                    }
                }
            }
        )
        return
    }

    val (title, text) = when (updateStatus) {
        is UpdateStatus.Latest -> Pair(
            stringResource(Res.string.dialog_current_version_latest),
            stringResource(Res.string.label_version_prefix, updateStatus.currentVersion)
        )
        is UpdateStatus.Error -> Pair(
            stringResource(Res.string.dialog_update_check_failed),
            stringResource(Res.string.label_error_message, updateStatus.message)
        )
        else -> return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_confirm))
            }
        }
    )
}