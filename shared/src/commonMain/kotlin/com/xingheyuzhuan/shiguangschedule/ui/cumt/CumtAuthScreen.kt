package com.xingheyuzhuan.shiguangschedule.ui.cumt

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAuthResult
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtParsers
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
import shiguangschedule.shared.generated.resources.check_circle_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.school_24px

@KoinViewModel
class CumtAuthViewModel(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository,
    private val platformBridge: CumtPlatformBridge
) : ViewModel() {

    val profile: StateFlow<CumtAccountProfile> = localRepo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAccountProfile()
    )

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _intranetStatus = MutableStateFlow<Boolean?>(null)
    val intranetStatus: StateFlow<Boolean?> = _intranetStatus.asStateFlow()

    private val _needActivationDialog = MutableStateFlow(false)
    val needActivationDialog: StateFlow<Boolean> = _needActivationDialog.asStateFlow()

    init {
        checkIntranet()
    }

    fun checkIntranet() {
        viewModelScope.launch {
            _intranetStatus.value = serviceRepo.checkIntranet()
        }
    }

    fun dismissActivationDialog() {
        _needActivationDialog.value = false
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        if (_isLoggingIn.value) return
        viewModelScope.launch {
            _isLoggingIn.value = true
            try {
                when (val res = serviceRepo.loginCas(username, password)) {
                    is CumtAuthResult.Success -> {
                        localRepo.saveCredentials(username, password, res.profile)
                        val welcomeName = res.profile.name.ifBlank { res.profile.username }
                        ToastManager.show("欢迎，$welcomeName")
                        onSuccess()
                    }
                    is CumtAuthResult.Error -> {
                        if (res.needsAccountActivation) {
                            _needActivationDialog.value = true
                        }
                        ToastManager.show(res.message)
                    }
                }
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            serviceRepo.logoutCas()
            localRepo.clearCredentials()
            platformBridge.clearWebCookies()
            ToastManager.show("已解绑矿大统一身份认证账号")
        }
    }
}

@Composable
fun CumtAuthScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtAuthViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val intranetStatus by viewModel.intranetStatus.collectAsState()
    val showActivationDialog by viewModel.needActivationDialog.collectAsState()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showRawStudentId by remember { mutableStateOf(false) }

    LaunchedEffect(profile.username) {
        if (username.isEmpty() && profile.username.isNotBlank()) {
            username = profile.username
        }
    }

    val isBound = profile.username.isNotBlank()

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "统一身份认证",
                subtitle = "中国矿业大学官方 CAS 登录中心",
                onBack = onBack
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

            // 绑定身份状态 Hero 卡片
            CumtHeroGradientCard(
                gradientColors = if (isBound) {
                    listOf(Color(0xFF0E7C86), Color(0xFF137E52))
                } else {
                    listOf(Color(0xFF2843A8), Color(0xFF0A6CA8))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = vectorResource(
                                    if (isBound) Res.drawable.check_circle_24px else Res.drawable.school_24px
                                ),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBound) {
                                "已绑定 ${profile.name.ifBlank { "矿大同学" }}"
                            } else {
                                "尚未绑定矿大校园账号"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(3.dp))
                        val displaySid = if (showRawStudentId) {
                            profile.username
                        } else {
                            CumtParsers.maskStudentId(profile.username)
                        }
                        Surface(
                            color = Color.Transparent,
                            onClick = { if (isBound) showRawStudentId = !showRawStudentId }
                        ) {
                            Text(
                                text = if (isBound) {
                                    "学号 $displaySid（点击${if (showRawStudentId) "隐藏" else "查看"}） · 本机安全加密"
                                } else {
                                    "绑定后可查询一卡通、图书馆借阅与快速导入课表"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    if (isBound) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50),
                            onClick = viewModel::logout
                        ) {
                            Text(
                                text = "解绑",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 登录表单
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CumtIconBadge(
                            icon = Res.drawable.school_24px,
                            accent = CumtAccent.TEAL,
                            size = 38.dp,
                            iconSize = 20.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isBound) "重新验证或切换账号" else "填写矿大统一身份认证凭据",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("学号 / 工号") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("统一身份认证密码") },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "隐藏" else "显示")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { viewModel.login(username, password, onSuccess = onBack) },
                        enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在直连矿大 CAS 认证...")
                        } else {
                            Text("验证并本地加密保存", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 网络环境检测 & 校外 VPN 入口
            CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CumtIconBadge(
                                icon = Res.drawable.info_24px,
                                accent = CumtAccent.SLATE,
                                size = 34.dp,
                                iconSize = 18.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "隐私与网络环境",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val netAccent = when (intranetStatus) {
                            true -> CumtAccent.EMERALD
                            false -> CumtAccent.CORAL
                            null -> CumtAccent.SLATE
                        }
                        CumtStatusPill(
                            text = when (intranetStatus) {
                                true -> "内网直连可用"
                                false -> "当前为校外网络"
                                null -> "检测中..."
                            },
                            containerColor = netAccent.containerColor(),
                            contentColor = netAccent.contentColor(),
                            dotColor = netAccent.contentColor()
                        )
                    }

                    Text(
                        text = "• 本应用直连中国矿业大学官方认证服务器，不经任何第三方服务器中转，凭据仅使用本地加密存储。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (intranetStatus) {
                            true -> "• 教务内网检测：当前网络可直接访问矿大教务与考试查询系统。"
                            false -> "• 教务内网检测：校外网络仍可绑定 CAS 账号，但同步课表、成绩与考表建议连接校园网或 VPN。"
                            null -> "• 正在探测校内教务网连通状态..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = viewModel::checkIntranet,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("重新检测内网")
                        }
                        OutlinedButton(
                            onClick = {
                                onNavigate(Destination.WebView(CumtConstants.VPN_GUIDE_CLIENT_URL, null))
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("校外 VPN 指南")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showActivationDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissActivationDialog,
            title = { Text("账号未激活") },
            text = { Text("检测到该学号尚未在统一身份认证中心激活。请先在打开的页面完成账号激活和首次密码设置后再回来绑定。") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissActivationDialog()
                        onNavigate(Destination.WebView(CumtConstants.CAS_LOGIN_URL, null))
                    }
                ) { Text("打开认证页面") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissActivationDialog) { Text("取消") }
            }
        )
    }
}
