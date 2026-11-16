package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormBuilding
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormPowerInfo
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtServiceRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.bolt_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import kotlin.math.round

@KoinViewModel
class CumtDormPowerViewModel(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository
) : ViewModel() {

    val profile: StateFlow<CumtAccountProfile> = localRepo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAccountProfile()
    )

    val powerInfo: StateFlow<CumtDormPowerInfo> = localRepo.dormPowerFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtDormPowerInfo()
    )

    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying.asStateFlow()

    fun query(building: CumtDormBuilding, roomId: String, studentIdOverride: String) {
        if (_isQuerying.value) return
        val sid = studentIdOverride.ifBlank { profile.value.username }.ifBlank { powerInfo.value.studentIdUsed }
        viewModelScope.launch {
            _isQuerying.value = true
            serviceRepo.queryDormPower(sid, building, roomId).fold(
                onSuccess = { info ->
                    val maxKeep = maxOf(powerInfo.value.maxPower, info.remainingPower, 100.0)
                    localRepo.saveDormPower(info.copy(maxPower = maxKeep))
                    ToastManager.show("${building.displayName} ${roomId.trim()} 剩余电量 ${formatPower(info.remainingPower)} 度")
                },
                onFailure = { e ->
                    ToastManager.show(e.message ?: "查询宿舍电量失败")
                }
            )
            _isQuerying.value = false
        }
    }

    fun refreshSaved() {
        val saved = powerInfo.value
        if (saved.roomId.isBlank()) {
            ToastManager.show("请先设置并查询宿舍号")
            return
        }
        val b = CumtDormBuilding.fromId(saved.buildingId) ?: CumtDormBuilding.YANMEI
        query(b, saved.roomId, saved.studentIdUsed)
    }
}

private fun formatPower(value: Double): String {
    val scaled = round(value * 100.0).toLong()
    val whole = scaled / 100
    val frac = kotlin.math.abs(scaled % 100).toString().padStart(2, '0')
    return "$whole.$frac"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CumtDormPowerScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtDormPowerViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val powerInfo by viewModel.powerInfo.collectAsState()
    val isQuerying by viewModel.isQuerying.collectAsState()

    var selectedBuilding by remember { mutableStateOf(CumtDormBuilding.YANMEI) }
    var roomId by remember { mutableStateOf("") }
    var studentIdInput by remember { mutableStateOf("") }

    LaunchedEffect(powerInfo, profile.username) {
        CumtDormBuilding.fromId(powerInfo.buildingId)?.let { selectedBuilding = it }
        if (roomId.isEmpty() && powerInfo.roomId.isNotBlank()) roomId = powerInfo.roomId
        if (studentIdInput.isEmpty()) {
            studentIdInput = powerInfo.studentIdUsed.ifBlank { profile.username }
        }
    }

    val ratio = if (powerInfo.maxPower > 0.0) {
        (powerInfo.remainingPower / powerInfo.maxPower).toFloat().coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("宿舍电量") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshSaved) {
                        Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 电量环形仪表盘卡片
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val primaryColor = if (powerInfo.remainingPower < 15.0 && powerInfo.roomId.isNotBlank()) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 14.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = primaryColor,
                                startAngle = 135f,
                                sweepAngle = 270f * ratio,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                vectorResource(Res.drawable.bolt_24px),
                                contentDescription = null,
                                tint = primaryColor
                            )
                            Text(
                                text = if (powerInfo.roomId.isBlank()) "--" else formatPower(powerInfo.remainingPower),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryColor
                            )
                            Text(
                                text = "剩余度数 (kWh)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (powerInfo.roomId.isNotBlank()) {
                            "已绑定宿舍：${powerInfo.buildingName} ${powerInfo.roomId}"
                        } else {
                            "尚未绑定宿舍，请在下方选择楼栋并填写房间号"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                onNavigate(Destination.WebView(CumtConstants.YKT_RECHARGE_URL, null))
                            }
                        ) { Text("一卡通购电大厅") }
                    }
                }
            }

            // 绑定配置表单
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("绑定或切换宿舍（南湖校区）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CumtDormBuilding.entries.forEach { b ->
                            FilterChip(
                                selected = selectedBuilding == b,
                                onClick = { selectedBuilding = b },
                                label = { Text(b.displayName) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("宿舍房间号（如 M1A301 / A101）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = studentIdInput,
                        onValueChange = { studentIdInput = it },
                        label = { Text("关联学号（用于定位校园卡账户）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.query(selectedBuilding, roomId, studentIdInput) },
                        enabled = !isQuerying && roomId.isNotBlank() && studentIdInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isQuerying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在查询电量...")
                        } else {
                            Text("查询并保存宿舍绑定")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
