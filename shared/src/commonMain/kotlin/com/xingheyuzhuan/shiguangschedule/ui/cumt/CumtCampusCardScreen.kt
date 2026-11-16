package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtCampusCardInfo
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
import shiguangschedule.shared.generated.resources.credit_card_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import kotlin.math.round

@KoinViewModel
class CumtCampusCardViewModel(
    private val localRepo: CumtLocalRepository,
    private val serviceRepo: CumtServiceRepository
) : ViewModel() {

    val profile: StateFlow<CumtAccountProfile> = localRepo.profileFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAccountProfile()
    )

    val cardInfo: StateFlow<CumtCampusCardInfo> = localRepo.campusCardFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtCampusCardInfo()
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh(silentIfUnbound = true)
    }

    fun refresh(silentIfUnbound: Boolean = false) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            val creds = localRepo.getSavedCredentials()
            if (creds == null) {
                if (!silentIfUnbound) {
                    ToastManager.show("请先绑定矿大统一身份认证")
                }
                return@launch
            }
            _isRefreshing.value = true
            serviceRepo.fetchCampusCardInfo(creds.first, creds.second).fold(
                onSuccess = { info ->
                    localRepo.saveCampusCard(info)
                    if (!silentIfUnbound) ToastManager.show("校园卡余额已更新")
                },
                onFailure = { e ->
                    if (!silentIfUnbound) {
                        ToastManager.show(e.message ?: "刷新校园卡失败")
                    }
                }
            )
            _isRefreshing.value = false
        }
    }
}

private fun formatYuan(amount: Double): String {
    val cents = round(amount * 100.0).toLong()
    val sign = if (cents < 0) "-" else ""
    val absCents = kotlin.math.abs(cents)
    return "$sign${absCents / 100}.${(absCents % 100).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtCampusCardScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: CumtCampusCardViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val cardInfo by viewModel.cardInfo.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园一卡通") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh(silentIfUnbound = false) }) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(vectorResource(Res.drawable.refresh_24px), contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (profile.username.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("绑定统一身份认证后可查询校园卡余额与流水", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onNavigate(Destination.CumtAuth) }) {
                        Text("去绑定账号")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    vectorResource(Res.drawable.credit_card_24px),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "一卡通账户 ${cardInfo.account.ifBlank { profile.username }}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "¥ ${formatYuan(cardInfo.balance)}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { viewModel.refresh(silentIfUnbound = false) }) {
                                    Text(if (isRefreshing) "刷新中..." else "立即刷新")
                                }
                                OutlinedButton(
                                    onClick = {
                                        onNavigate(Destination.WebView(CumtConstants.YKT_RECHARGE_URL, null))
                                    }
                                ) {
                                    Text("一卡通大厅充值")
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "近期交易流水（最多 20 条）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (cardInfo.transactions.isEmpty()) {
                    item {
                        Text(
                            text = if (isRefreshing) "正在加载交易记录..." else "暂无交易流水，可点击「立即刷新」获取最新记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                } else {
                    items(cardInfo.transactions) { tx ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${tx.timeStr} · 余额 ¥${formatYuan(tx.balanceAfterYuan)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val isPositive = tx.amountYuan >= 0
                                Text(
                                    text = (if (isPositive) "+" else "") + formatYuan(tx.amountYuan),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
