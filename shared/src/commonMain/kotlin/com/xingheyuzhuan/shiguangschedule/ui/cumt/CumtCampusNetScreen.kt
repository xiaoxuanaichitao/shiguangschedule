package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CampusNetAccount
import com.xingheyuzhuan.shiguangschedule.cumt.CampusNetCampus
import com.xingheyuzhuan.shiguangschedule.cumt.CampusNetIsp
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtPlatformBridge
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
import shiguangschedule.shared.generated.resources.delete_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.wifi_24px
import kotlin.time.Clock

@KoinViewModel
class CumtCampusNetViewModel(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository,
    private val platformBridge: CumtPlatformBridge
) : ViewModel() {

    val accounts: StateFlow<List<CampusNetAccount>> = localRepo.campusNetAccountsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val autoLoginEnabled: StateFlow<Boolean> = localRepo.campusNetAutoLoginFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val lastConfig: StateFlow<Pair<String, String>> = localRepo.lastCampusNetConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CampusNetCampus.NANHU.code to CampusNetIsp.CUMT.id
    )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val isWifiConnected: Boolean
        get() = platformBridge.isWifiConnected()

    fun decryptPassword(account: CampusNetAccount): String = localRepo.decryptCampusNetPassword(account)

    fun setAutoLogin(enabled: Boolean) {
        viewModelScope.launch {
            localRepo.setCampusNetAutoLogin(enabled)
            ToastManager.show(if (enabled) "已开启连 Wi-Fi 启动自动登录" else "已关闭自动登录")
        }
    }

    fun login(
        username: String,
        password: String,
        campus: CampusNetCampus,
        isp: CampusNetIsp,
        saveToHistory: Boolean = true
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            _statusMessage.value = null
            val res = serviceRepo.loginCampusNet(username, password, campus, isp)
            _statusMessage.value = res.message
            ToastManager.show(res.message)
            if (saveToHistory && username.isNotBlank() && password.isNotBlank()) {
                val id = "${campus.code}_${isp.id}_${username.trim()}_${Clock.System.now().toEpochMilliseconds()}"
                localRepo.saveCampusNetAccount(
                    id = id,
                    username = username,
                    password = password,
                    campusCode = campus.code,
                    ispId = isp.id
                )
            } else {
                localRepo.saveLastCampusNetConfig(campus.code, isp.id)
            }
            _isBusy.value = false
        }
    }

    fun quickLoginSaved(account: CampusNetAccount) {
        val pwd = localRepo.decryptCampusNetPassword(account)
        val campus = CampusNetCampus.fromCode(account.campusCode)
        val isp = CampusNetIsp.fromId(account.ispId)
        login(account.username, pwd, campus, isp, saveToHistory = false)
    }

    fun logout(campus: CampusNetCampus) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = serviceRepo.logoutCampusNet(campus)
            _statusMessage.value = res.message
            ToastManager.show(if (res.success) "已注销当前校园网连接" else res.message)
            _isBusy.value = false
        }
    }

    fun deleteSavedAccount(id: String) {
        viewModelScope.launch {
            localRepo.deleteCampusNetAccount(id)
        }
    }
}

@Composable
fun CumtCampusNetScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtCampusNetViewModel = koinViewModel()
) {
    val accounts by viewModel.accounts.collectAsState()
    val autoLogin by viewModel.autoLoginEnabled.collectAsState()
    val lastConfig by viewModel.lastConfig.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var selectedCampus by remember { mutableStateOf(CampusNetCampus.NANHU) }
    var selectedIsp by remember { mutableStateOf(CampusNetIsp.CUMT) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pwdVisible by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(lastConfig, accounts) {
        selectedCampus = CampusNetCampus.fromCode(lastConfig.first)
        selectedIsp = CampusNetIsp.fromId(lastConfig.second)
        if (username.isEmpty() && accounts.isNotEmpty()) {
            val latest = accounts.first()
            username = latest.username
            password = viewModel.decryptPassword(latest)
            selectedCampus = CampusNetCampus.fromCode(latest.campusCode)
            selectedIsp = CampusNetIsp.fromId(latest.ispId)
        }
    }

    val wifiConnected = viewModel.isWifiConnected

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "校园网快捷登录",
                subtitle = "${selectedCampus.displayName} · ${selectedIsp.displayName}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(vectorResource(Res.drawable.info_24px), contentDescription = "认证说明")
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

            // Wi-Fi 状态与自动认证卡片
            CumtHeroGradientCard(
                gradientColors = if (wifiConnected) {
                    listOf(Color(0xFF6B3CC9), Color(0xFF0E7C86))
                } else {
                    listOf(Color(0xFF475569), Color(0xFF334155))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    vectorResource(Res.drawable.wifi_24px),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (wifiConnected) "已连接无线网络" else "未检测到 Wi-Fi 连接",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (wifiConnected) {
                                    "请确认处于 CUMT 校园网下进行认证"
                                } else {
                                    "请先连接 CUMT / CUMT_5G 等校园无线网"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启动时自动认证校园网",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "检测到 Wi-Fi 后自动使用首条账号续签",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                            }
                            Switch(
                                checked = autoLogin,
                                onCheckedChange = viewModel::setAutoLogin,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF6B3CC9),
                                    checkedTrackColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // 登录表单卡片
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "校区与运营商配置",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    CumtSegmentedBar(
                        options = CampusNetCampus.entries.map { it.displayName },
                        selectedIndex = CampusNetCampus.entries.indexOf(selectedCampus).coerceAtLeast(0),
                        onSelect = { selectedCampus = CampusNetCampus.entries[it] },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CampusNetIsp.entries.forEach { isp ->
                            val selected = selectedIsp == isp
                            Surface(
                                onClick = { selectedIsp = isp },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    CumtAccent.VIOLET.containerColor()
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                border = if (selected) {
                                    BorderStroke(1.5.dp, CumtAccent.VIOLET.contentColor())
                                } else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = isp.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        CumtAccent.VIOLET.contentColor()
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
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("校园网账号（学号 / 宽带账号）") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("校园网密码") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        visualTransformation = if (pwdVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { pwdVisible = !pwdVisible }) {
                                Text(if (pwdVisible) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!statusMessage.isNullOrBlank()) {
                        CumtStatusPill(
                            text = statusMessage.orEmpty(),
                            containerColor = CumtAccent.TEAL.containerColor(),
                            contentColor = CumtAccent.TEAL.contentColor(),
                            dotColor = CumtAccent.TEAL.contentColor()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.login(username, password, selectedCampus, selectedIsp) },
                            enabled = !isBusy && username.isNotBlank() && password.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("连接认证", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.logout(selectedCampus) },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Text("注销")
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onNavigate(Destination.WebView(CumtConstants.CAMPUS_NET_SELF_SERVICE_URL, null))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("校园网自助服务（管理在线终端）")
                    }
                }
            }

            // 历史账号快捷切换
            if (accounts.isNotEmpty()) {
                CumtSectionHeader(
                    title = "已保存的快捷账号",
                    subtitle = "点击卡片即可自动填入并直接认证"
                )
                accounts.forEach { acc ->
                    val c = CampusNetCampus.fromCode(acc.campusCode)
                    val isp = CampusNetIsp.fromId(acc.ispId)
                    CumtSurfaceCard(
                        onClick = {
                            username = acc.username
                            password = viewModel.decryptPassword(acc)
                            selectedCampus = c
                            selectedIsp = isp
                            viewModel.quickLoginSaved(acc)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CumtIconBadge(
                                icon = Res.drawable.wifi_24px,
                                accent = CumtAccent.VIOLET,
                                size = 40.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.username,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CumtStatusPill(
                                        text = c.displayName,
                                        containerColor = CumtAccent.TEAL.containerColor(),
                                        contentColor = CumtAccent.TEAL.contentColor()
                                    )
                                    CumtStatusPill(
                                        text = isp.displayName,
                                        containerColor = CumtAccent.VIOLET.containerColor(),
                                        contentColor = CumtAccent.VIOLET.contentColor()
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.deleteSavedAccount(acc.id) }) {
                                Icon(
                                    vectorResource(Res.drawable.delete_24px),
                                    contentDescription = "删除账号",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("矿大校园网使用说明") },
            text = {
                Text(
                    "• 校园网认证仅在连接学校无线/有线内网（CUMT / CUMT_5G 等）时有效。\n" +
                        "• 南湖校区与文昌校区认证参数略有不同，请切换到所在校区再连接。\n" +
                        "• 若提示「在线设备数量超出限制」，可点击「校园网自助服务」登录踢下旧设备。\n" +
                        "• 开启「自动认证」后，当连接 Wi-Fi 并启动本软件时，会自动用列表第一条账号静默续签认证。"
                )
            },
            confirmButton = {
                Button(onClick = { showHelpDialog = false }) { Text("知道了") }
            }
        )
    }
}
