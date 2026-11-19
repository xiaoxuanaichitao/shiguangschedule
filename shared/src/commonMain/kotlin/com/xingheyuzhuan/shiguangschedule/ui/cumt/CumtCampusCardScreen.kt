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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            CumtTopBar(
                title = "校园一卡通",
                subtitle = if (profile.username.isBlank()) "未绑定统一认证账号" else "账户 ${cardInfo.account.ifBlank { profile.username }}",
                onBack = onBack,
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
            CumtEmptyState(
                icon = Res.drawable.credit_card_24px,
                accent = CumtAccent.OCEAN,
                title = "绑定统一身份认证后可查询",
                subtitle = "自动同步校园卡当前余额及近期食堂、超市消费流水明细",
                primaryActionText = "去绑定账号",
                onPrimaryAction = { onNavigate(Destination.CumtAuth) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CumtHeroGradientCard(
                        gradientColors = listOf(Color(0xFF0A6CA8), Color(0xFF0B7872)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            vectorResource(Res.drawable.credit_card_24px),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "中国矿业大学 · 虚拟校园卡",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )
                                    }
                                }
                                Text(
                                    text = "No.${cardInfo.account.ifBlank { profile.username }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }

                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = "当前可用余额",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "¥ ${formatYuan(cardInfo.balance)}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(50),
                                    onClick = { viewModel.refresh(silentIfUnbound = false) }
                                ) {
                                    Text(
                                        text = if (isRefreshing) "刷新中..." else "立即刷新余额",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    color = Color.White.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onNavigate(Destination.WebView(CumtConstants.YKT_RECHARGE_URL, null))
                                    }
                                ) {
                                    Text(
                                        text = "一卡通大厅充值 ›",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    CumtSectionHeader(
                        title = "近期交易流水",
                        subtitle = "最多展示最近 20 条记录",
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (cardInfo.transactions.isEmpty()) {
                    item {
                        CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            CumtEmptyState(
                                icon = Res.drawable.credit_card_24px,
                                accent = CumtAccent.OCEAN,
                                title = if (isRefreshing) "正在加载交易记录..." else "暂无交易流水",
                                subtitle = "可点击上方「立即刷新余额」拉取最新消费明细",
                                primaryActionText = if (isRefreshing) null else "立即刷新",
                                onPrimaryAction = if (isRefreshing) null else {
                                    { viewModel.refresh(silentIfUnbound = false) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    item {
                        CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            cardInfo.transactions.forEachIndexed { index, tx ->
                                val isPositive = tx.amountYuan >= 0
                                val badgeAccent = if (isPositive) CumtAccent.EMERALD else CumtAccent.ORANGE
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = badgeAccent.containerColor(),
                                        shape = CircleShape,
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (isPositive) "+" else "-",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = badgeAccent.contentColor()
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = tx.description,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${tx.timeStr} · 余额 ¥${formatYuan(tx.balanceAfterYuan)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = (if (isPositive) "+" else "") + formatYuan(tx.amountYuan),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPositive) {
                                            CumtAccent.EMERALD.contentColor()
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                                if (index < cardInfo.transactions.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
