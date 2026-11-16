package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtCampusCardInfo
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormBuilding
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormPowerInfo
import com.xingheyuzhuan.shiguangschedule.cumt.CumtExamItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtLocalRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtScoreItem
import com.xingheyuzhuan.shiguangschedule.cumt.CumtServiceRepository
import com.xingheyuzhuan.shiguangschedule.ui.components.ToastManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinViewModel
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.account_circle_24px
import shiguangschedule.shared.generated.resources.award_star_24px
import shiguangschedule.shared.generated.resources.bolt_24px
import shiguangschedule.shared.generated.resources.calendar_today_24px
import shiguangschedule.shared.generated.resources.cloud_24px
import shiguangschedule.shared.generated.resources.credit_card_24px
import shiguangschedule.shared.generated.resources.directions_bus_24px
import shiguangschedule.shared.generated.resources.menu_book_24px
import shiguangschedule.shared.generated.resources.refresh_24px
import shiguangschedule.shared.generated.resources.schedule_24px
import shiguangschedule.shared.generated.resources.school_24px
import shiguangschedule.shared.generated.resources.wifi_24px
import kotlin.math.round
import kotlin.time.Clock

@KoinViewModel
class CampusDiscoverViewModel(
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

    val dormPower: StateFlow<CumtDormPowerInfo> = localRepo.dormPowerFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtDormPowerInfo()
    )

    val exams: StateFlow<List<CumtExamItem>> = localRepo.examsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scores: StateFlow<List<CumtScoreItem>> = localRepo.scoresFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isRefreshingCard = MutableStateFlow(false)
    val isRefreshingCard: StateFlow<Boolean> = _isRefreshingCard.asStateFlow()

    private val _isRefreshingPower = MutableStateFlow(false)
    val isRefreshingPower: StateFlow<Boolean> = _isRefreshingPower.asStateFlow()

    fun refreshCampusCard() {
        if (_isRefreshingCard.value) return
        viewModelScope.launch {
            val creds = localRepo.getSavedCredentials()
            if (creds == null) {
                ToastManager.show("请先绑定统一身份认证")
                return@launch
            }
            _isRefreshingCard.value = true
            serviceRepo.fetchCampusCardInfo(creds.first, creds.second).fold(
                onSuccess = {
                    localRepo.saveCampusCard(it)
                    ToastManager.show("一卡通余额已更新")
                },
                onFailure = { ToastManager.show(it.message ?: "刷新失败") }
            )
            _isRefreshingCard.value = false
        }
    }

    fun refreshDormPower() {
        val current = dormPower.value
        if (current.roomId.isBlank() || _isRefreshingPower.value) return
        val building = CumtDormBuilding.fromId(current.buildingId) ?: CumtDormBuilding.YANMEI
        val sid = current.studentIdUsed.ifBlank { profile.value.username }
        viewModelScope.launch {
            _isRefreshingPower.value = true
            serviceRepo.queryDormPower(sid, building, current.roomId).fold(
                onSuccess = {
                    localRepo.saveDormPower(it.copy(maxPower = maxOf(current.maxPower, it.remainingPower, 100.0)))
                    ToastManager.show("宿舍电量已更新")
                },
                onFailure = { ToastManager.show(it.message ?: "刷新失败") }
            )
            _isRefreshingPower.value = false
        }
    }
}

private data class DiscoverToolItem(
    val title: String,
    val subtitle: String,
    val icon: DrawableResource,
    val badge: String? = null,
    val onClick: () -> Unit
)

private fun formatDecimal(value: Double): String {
    val cents = round(value * 100.0).toLong()
    return "${cents / 100}.${kotlin.math.abs(cents % 100).toString().padStart(2, '0')}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusDiscoverScreen(
    onNavigate: (Destination) -> Unit,
    viewModel: CampusDiscoverViewModel = koinViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val cardInfo by viewModel.cardInfo.collectAsState()
    val dormPower by viewModel.dormPower.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val scores by viewModel.scores.collectAsState()
    val isRefreshingCard by viewModel.isRefreshingCard.collectAsState()
    val isRefreshingPower by viewModel.isRefreshingPower.collectAsState()

    var showVpnDialog by remember { mutableStateOf(false) }

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val upcomingExamCount = remember(exams, today) {
        exams.count { e ->
            val d = runCatching { LocalDate(e.year, e.month, e.day) }.getOrNull()
            d != null && today.daysUntil(d) >= 0
        }
    }

    val tools = listOf(
        DiscoverToolItem(
            title = "教务导课表",
            subtitle = "本科 / 研究生正方系统",
            icon = Res.drawable.school_24px,
            onClick = { onNavigate(Destination.CumtCourseImport) }
        ),
        DiscoverToolItem(
            title = "考试倒计时",
            subtitle = "教务抓取与自定义提醒",
            icon = Res.drawable.schedule_24px,
            badge = if (upcomingExamCount > 0) "待考 $upcomingExamCount 门" else null,
            onClick = { onNavigate(Destination.CumtExams) }
        ),
        DiscoverToolItem(
            title = "成绩与绩点",
            subtitle = "加权均分与推免计算器",
            icon = Res.drawable.award_star_24px,
            badge = if (scores.isNotEmpty()) "${scores.size} 门" else null,
            onClick = { onNavigate(Destination.CumtScores) }
        ),
        DiscoverToolItem(
            title = "矿大图书馆",
            subtitle = "在借续借与馆藏检索",
            icon = Res.drawable.menu_book_24px,
            onClick = { onNavigate(Destination.CumtLibrary) }
        ),
        DiscoverToolItem(
            title = "校车时刻表",
            subtitle = "南湖 / 文昌发车与站点",
            icon = Res.drawable.directions_bus_24px,
            onClick = { onNavigate(Destination.CumtSchoolBus) }
        ),
        DiscoverToolItem(
            title = "校园网登录",
            subtitle = "四网合一与 Wi-Fi 自动认证",
            icon = Res.drawable.wifi_24px,
            onClick = { onNavigate(Destination.CumtCampusNet) }
        ),
        DiscoverToolItem(
            title = "矿大校历",
            subtitle = "查看官方学期教学周历",
            icon = Res.drawable.calendar_today_24px,
            onClick = { onNavigate(Destination.WebView(CumtConstants.SCHOOL_CALENDAR_URL, null)) }
        ),
        DiscoverToolItem(
            title = "校外 VPN 指引",
            subtitle = "校外访问教务内网指南",
            icon = Res.drawable.cloud_24px,
            onClick = { showVpnDialog = true }
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("矿大校园", fontWeight = FontWeight.ExtraBold) }
            )
        }
    ) { paddingValues ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = navBottom + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 统一身份认证横幅卡片
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (profile.username.isNotBlank()) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Destination.CumtAuth) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            vectorResource(Res.drawable.account_circle_24px),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (profile.username.isNotBlank()) {
                                    "${profile.name.ifBlank { "矿大同学" }} · ${profile.username}"
                                } else {
                                    "未绑定矿大统一身份认证"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (profile.username.isNotBlank()) {
                                    "已启用本地加密直连校园卡、图书馆与教务自动填表"
                                } else {
                                    "点击绑定学号，一键直查一卡通、图书馆借阅与教务系统"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 校园卡 & 宿舍电量双实时看板
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(Destination.CumtCampusCard) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        vectorResource(Res.drawable.credit_card_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("校园卡", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(
                                    onClick = viewModel::refreshCampusCard,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    if (isRefreshingCard) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            vectorResource(Res.drawable.refresh_24px),
                                            contentDescription = "刷新校园卡",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (cardInfo.updatedAtMillis > 0L) "¥ ${formatDecimal(cardInfo.balance)}" else "未查询",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "点击查看流水与充值",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(Destination.CumtDormPower) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        vectorResource(Res.drawable.bolt_24px),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("宿舍电量", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(
                                    onClick = viewModel::refreshDormPower,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    if (isRefreshingPower) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            vectorResource(Res.drawable.refresh_24px),
                                            contentDescription = "刷新电量",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (dormPower.roomId.isNotBlank()) {
                                    "${formatDecimal(dormPower.remainingPower)} 度"
                                } else {
                                    "点击绑定"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (dormPower.roomId.isNotBlank()) {
                                    "${dormPower.buildingName} ${dormPower.roomId}"
                                } else {
                                    "支持研梅/杏苑/松竹/桃苑"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 校园功能网格标题
            item {
                Text(
                    text = "矿大百宝箱",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // 两列卡片网格（按两行一组排布，避免嵌套 LazyVerticalGrid 的高度冲突）
            items(tools.chunked(2).size) { rowIdx ->
                val pair = tools.chunked(2)[rowIdx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { tool ->
                        DiscoverToolCard(
                            item = tool,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showVpnDialog) {
        AlertDialog(
            onDismissRequest = { showVpnDialog = false },
            title = { Text("校外访问矿大内网指引") },
            text = {
                Text("本科/研究生教务与部分校内服务仅限校内网访问。处于校外网络时，可参考信息中心官方教程使用 WebVPN 或客户端 VPN 接入。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showVpnDialog = false
                        onNavigate(Destination.WebView(CumtConstants.VPN_GUIDE_WEB_URL, null))
                    }
                ) { Text("WebVPN 教程") }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showVpnDialog = false
                        onNavigate(Destination.WebView(CumtConstants.VPN_GUIDE_CLIENT_URL, null))
                    }
                ) { Text("客户端 VPN 教程") }
            }
        )
    }
}

@Composable
private fun DiscoverToolCard(
    item: DiscoverToolItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = item.onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Icon(
                            vectorResource(item.icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (!item.badge.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
