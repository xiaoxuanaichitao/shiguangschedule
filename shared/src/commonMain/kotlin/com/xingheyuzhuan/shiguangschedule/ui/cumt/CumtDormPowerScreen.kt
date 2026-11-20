package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormBuildingItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormCampus
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormCatalog
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

    fun query(
        campus: CumtDormCampus,
        building: CumtDormBuildingItem,
        roomId: String,
        studentIdOverride: String
    ) {
        if (_isQuerying.value) return
        viewModelScope.launch {
            val creds = localRepo.getSavedCredentials()
            if (creds == null) {
                ToastManager.show("请先绑定矿大统一身份认证账号")
                return@launch
            }
            val querySno = studentIdOverride.ifBlank { profile.value.username }.ifBlank { creds.first }
            _isQuerying.value = true
            serviceRepo.queryDormPower(
                casUsername = creds.first,
                casPassword = creds.second,
                campus = campus,
                building = building,
                roomId = roomId,
                queryStudentId = querySno
            ).fold(
                onSuccess = { info ->
                    val maxKeep = maxOf(powerInfo.value.maxPower, info.remainingPower, 100.0)
                    localRepo.saveDormPower(info.copy(maxPower = maxKeep))
                    ToastManager.show("${campus.label} · ${building.name} ${roomId.trim()} 剩余电量 ${formatPower(info.remainingPower)} 度")
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
        val campus = CumtDormCampus.fromId(saved.campusId)
        val building = CumtDormCatalog.resolveBuilding(saved.campusId, saved.buildingId, saved.buildingName)
        query(campus, building, saved.roomId, saved.studentIdUsed)
    }
}

private fun formatPower(value: Double): String {
    val scaled = round(value * 100.0).toLong()
    val whole = scaled / 100
    val frac = kotlin.math.abs(scaled % 100).toString().padStart(2, '0')
    return "$whole.$frac"
}

@Composable
fun CumtDormPowerScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtDormPowerViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val powerInfo by viewModel.powerInfo.collectAsState()
    val isQuerying by viewModel.isQuerying.collectAsState()

    var selectedCampus by remember { mutableStateOf(CumtDormCampus.NANHU) }
    var selectedBuilding by remember { mutableStateOf(CumtDormCatalog.defaultBuilding(CumtDormCampus.NANHU)) }
    var roomId by remember { mutableStateOf("") }
    var studentIdInput by remember { mutableStateOf("") }
    var syncedRecordKey by remember { mutableStateOf("") }

    LaunchedEffect(powerInfo, profile.username) {
        val recordKey = "${powerInfo.campusId}|${powerInfo.buildingId}|${powerInfo.roomId}|${powerInfo.updatedAtMillis}"
        if (powerInfo.roomId.isNotBlank() && syncedRecordKey != recordKey) {
            syncedRecordKey = recordKey
            selectedCampus = CumtDormCampus.fromId(powerInfo.campusId)
            selectedBuilding = CumtDormCatalog.resolveBuilding(
                campusId = powerInfo.campusId,
                buildingId = powerInfo.buildingId,
                buildingName = powerInfo.buildingName
            )
            if (roomId.isEmpty()) roomId = powerInfo.roomId
        }
        if (studentIdInput.isEmpty()) {
            studentIdInput = powerInfo.studentIdUsed.ifBlank { profile.username }
        }
    }

    val isBound = powerInfo.roomId.isNotBlank()
    val hasCasBinding = profile.username.isNotBlank()
    val isLowPower = isBound && powerInfo.remainingPower < 15.0
    val ratio = if (powerInfo.maxPower > 0.0) {
        (powerInfo.remainingPower / powerInfo.maxPower).toFloat().coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "宿舍电量",
                subtitle = if (isBound) {
                    "${powerInfo.campusName} · ${powerInfo.buildingName} ${powerInfo.roomId}"
                } else {
                    "南湖 / 文昌双校区实时购电查询"
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::refreshSaved) {
                        if (isQuerying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
                        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(2.dp))

            if (!hasCasBinding) {
                CumtSurfaceCard(
                    containerColor = CumtAccent.AMBER.containerColor().copy(alpha = 0.72f),
                    borderColor = CumtAccent.AMBER.contentColor().copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "查询电费需先绑定统一身份认证",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = CumtAccent.AMBER.contentColor()
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "一卡通与宿舍电量接口需使用 CAS 鉴权令牌",
                                style = MaterialTheme.typography.bodySmall,
                                color = CumtAccent.AMBER.contentColor().copy(alpha = 0.85f)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { onNavigate(Destination.CumtAuth) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("前往绑定")
                        }
                    }
                }
            }

            // 电量环形仪表盘卡片
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusAccent = when {
                        !isBound -> CumtAccent.SLATE
                        isLowPower -> CumtAccent.CORAL
                        powerInfo.remainingPower < 30.0 -> CumtAccent.AMBER
                        else -> CumtAccent.EMERALD
                    }
                    val arcColor = if (!isBound) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        statusAccent.contentColor()
                    }
                    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(182.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 15.dp.toPx()
                            drawArc(
                                color = trackColor,
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = arcColor.copy(alpha = 0.22f),
                                startAngle = 135f,
                                sweepAngle = 270f * ratio,
                                useCenter = false,
                                style = Stroke(width = strokeWidth + 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = arcColor,
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
                                tint = arcColor,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (!isBound) "--" else formatPower(powerInfo.remainingPower),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = arcColor
                            )
                            Text(
                                text = "剩余度数 (kWh)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    CumtStatusPill(
                        text = when {
                            !isBound -> "尚未绑定宿舍"
                            isLowPower -> "电量偏低 · 建议及时购电"
                            else -> "电量充足"
                        },
                        containerColor = statusAccent.containerColor(),
                        contentColor = statusAccent.contentColor(),
                        dotColor = statusAccent.contentColor()
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (isBound) {
                            "当前监测：${powerInfo.campusName} · ${powerInfo.buildingName} ${powerInfo.roomId}"
                        } else {
                            "在下方切换校区、选择宿舍楼并输入房间号即可绑定"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))
                    FilledTonalButton(
                        onClick = {
                            onNavigate(Destination.WebView(CumtConstants.YKT_RECHARGE_URL, null))
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("前往一卡通购电大厅")
                    }
                }
            }

            // 绑定配置表单
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "绑定或切换宿舍",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        CumtStatusPill(
                            text = selectedCampus.label,
                            containerColor = CumtAccent.AMBER.containerColor(),
                            contentColor = CumtAccent.AMBER.contentColor()
                        )
                    }

                    val campuses = CumtDormCampus.entries
                    CumtSegmentedBar(
                        options = campuses.map { it.label },
                        selectedIndex = campuses.indexOf(selectedCampus).coerceAtLeast(0),
                        onSelect = { idx ->
                            val newCampus = campuses[idx]
                            if (newCampus != selectedCampus) {
                                selectedCampus = newCampus
                                selectedBuilding = CumtDormCatalog.defaultBuilding(newCampus)
                            }
                        }
                    )

                    val campusBuildings = CumtDormCatalog.buildingsFor(selectedCampus)
                    val columns = if (selectedCampus == CumtDormCampus.NANHU) 3 else 4
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        campusBuildings.chunked(columns).forEach { rowBuildings ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowBuildings.forEach { b ->
                                    val selected = selectedBuilding.campusId == b.campusId && selectedBuilding.id == b.id
                                    Surface(
                                        onClick = { selectedBuilding = b },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        border = if (selected) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = b.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                        )
                                    }
                                }
                                repeat(columns - rowBuildings.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = roomId,
                        onValueChange = { roomId = it },
                        label = { Text("宿舍房间号（${selectedCampus.roomHint}）") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = studentIdInput,
                        onValueChange = { studentIdInput = it },
                        label = { Text("关联学号（用于定位校园卡账户）") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.query(selectedCampus, selectedBuilding, roomId, studentIdInput) },
                        enabled = !isQuerying && hasCasBinding && roomId.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
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
                            Text(
                                text = if (hasCasBinding) "查询并保存宿舍绑定" else "请先绑定统一身份认证",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
