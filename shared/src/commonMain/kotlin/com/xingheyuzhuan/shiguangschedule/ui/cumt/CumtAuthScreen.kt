package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import shiguangschedule.shared.generated.resources.check_circle_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.school_24px

@KoinViewModel
class CumtAuthViewModel(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository
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
            ToastManager.show("已解绑矿大统一身份认证账号")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(profile.username) {
        if (username.isEmpty() && profile.username.isNotBlank()) {
            username = profile.username
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("矿大统一身份认证") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
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

            // 绑定状态卡片
            if (profile.username.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.check_circle_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "已绑定 ${profile.name.ifBlank { "矿大同学" }}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "学号: ${profile.username}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        TextButton(
                            onClick = viewModel::logout,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("解绑")
                        }
                    }
                }
            }

            // 登录表单
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            vectorResource(Res.drawable.school_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (profile.username.isBlank()) "绑定矿大统一身份认证" else "重新验证或切换账号",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("学号 / 工号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("统一身份认证密码") },
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
                        modifier = Modifier.fillMaxWidth()
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
                            Text("验证并本地加密保存")
                        }
                    }
                }
            }

            // 网络环境检测 & 校外 VPN 入口
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            vectorResource(Res.drawable.info_24px),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("隐私、网络与说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "• 本应用直连中国矿业大学官方认证服务器，不经第三方服务器中转，凭据仅使用系统安全密钥加密保存于本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (intranetStatus) {
                            true -> "• 教务内网检测：当前已可直连教务系统。"
                            false -> "• 教务内网检测：当前处于校外网络，统一认证仍可绑定，但访问教务系统建议连接校园网或矿大 VPN。"
                            null -> "• 教务内网检测中..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::checkIntranet) {
                            Text("重新检测内网")
                        }
                        OutlinedButton(
                            onClick = {
                                onNavigate(Destination.WebView(CumtConstants.VPN_GUIDE_CLIENT_URL, null))
                            }
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
