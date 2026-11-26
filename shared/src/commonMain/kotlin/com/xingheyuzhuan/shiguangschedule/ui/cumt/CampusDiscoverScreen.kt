package com.xingheyuzhuan.shiguangschedule.ui.cumt

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAccountProfile
import com.xingheyuzhuan.shiguangschedule.cumt.CumtAiConfig
import com.xingheyuzhuan.shiguangschedule.cumt.CumtCampusCardInfo
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormCampus
import com.xingheyuzhuan.shiguangschedule.cumt.CumtDormCatalog
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
import shiguangschedule.shared.generated.resources.chevron_right_24px
import shiguangschedule.shared.generated.resources.cloud_24px
import shiguangschedule.shared.generated.resources.credit_card_24px
import shiguangschedule.shared.generated.resources.directions_bus_24px
import shiguangschedule.shared.generated.resources.image_24px
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

    val aiConfig: StateFlow<CumtAiConfig> = localRepo.aiConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CumtAiConfig()
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
        val campus = CumtDormCampus.fromId(current.campusId)
        val building = CumtDormCatalog.resolveBuilding(current.campusId, current.buildingId, current.buildingName)
        viewModelScope.launch {
            val creds = localRepo.getSavedCredentials()
            if (creds == null) {
                ToastManager.show("请先绑定统一身份认证")
                return@launch
            }
            val sid = current.studentIdUsed.ifBlank { creds.first }
            _isRefreshingPower.value = true
            serviceRepo.queryDormPower(
                casUsername = creds.first,
                casPassword = creds.second,
                campus = campus,
                building = building,
                roomId = current.roomId,
                queryStudentId = sid
            ).fold(
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
    val accent: CumtAccent,
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
    val aiConfig by viewModel.aiConfig.collectAsState()
    val isRefreshingCard by viewModel.isRefreshingCard.collectAsState()
    val isRefreshingPower by viewModel.isRefreshingPower.collectAsState()

    var showVpnDialog by remember { mutableStateOf(false) }

    val now = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    }
    val today = now.date
    val greeting = remember(now.hour) {
        when (now.hour) {
            in 5..11 -> "早安，矿大人"
            in 12..13 -> "午安，矿大人"
            in 14..17 -> "下午好，矿大人"
            else -> "晚上好，矿大人"
        }
    }

    val upcomingExams = remember(exams, today) {
        exams.mapNotNull { e ->
            val d = runCatching { LocalDate(e.year, e.month, e.day) }.getOrNull() ?: return@mapNotNull null
            val diff = today.daysUntil(d)
            if (diff >= 0) e to diff else null
        }.sortedBy { it.second }
    }
    val upcomingExamCount = upcomingExams.size
    val nearestSpotlight = upcomingExams.firstOrNull()?.takeIf { it.second <= 14 }

    val tools = listOf(
        DiscoverToolItem(
            title = "教务导课表",
            subtitle = "本科 / 研究生正方导入",
            icon = Res.drawable.school_24px,
            accent = CumtAccent.INDIGO,
            onClick = { onNavigate(Destination.CumtCourseImport) }
        ),
        DiscoverToolItem(
            title = "矿大 AI 助手",
            subtitle = "对话排课 / 识图 / MCP",
            icon = Res.drawable.image_24px,
            accent = CumtAccent.VIOLET,
            badge = if (aiConfig.hasApiKey) "已配置" else "配置 Key",
            onClick = { onNavigate(Destination.CumtAiCourseImport) }
        ),
        DiscoverToolItem(
            title = "考试倒计时",
            subtitle = "抓考表与同步进课表",
            icon = Res.drawable.schedule_24px,
            accent = CumtAccent.CORAL,
            badge = if (upcomingExamCount > 0) "待考 $upcomingExamCount" else null,
            onClick = { onNavigate(Destination.CumtExams) }
        ),
        DiscoverToolItem(
            title = "成绩与绩点",
            subtitle = "加权均分与保研计算",
            icon = Res.drawable.award_star_24px,
            accent = CumtAccent.AMBER,
            badge = if (scores.isNotEmpty()) "${scores.size} 门" else null,
            onClick = { onNavigate(Destination.CumtScores) }
        ),
        DiscoverToolItem(
            title = "矿大图书馆",
            subtitle = "在借续借与馆藏检索",
            icon = Res.drawable.menu_book_24px,
            accent = CumtAccent.EMERALD,
            onClick = { onNavigate(Destination.CumtLibrary) }
        ),
        DiscoverToolItem(
            title = "校车时刻表",
            subtitle = "南湖 / 文昌实时倒计时",
            icon = Res.drawable.directions_bus_24px,
            accent = CumtAccent.SKY,
            onClick = { onNavigate(Destination.CumtSchoolBus) }
        ),
        DiscoverToolItem(
            title = "校园网登录",
            subtitle = "四网合一与自动认证",
            icon = Res.drawable.wifi_24px,
            accent = CumtAccent.VIOLET,
            onClick = { onNavigate(Destination.CumtCampusNet) }
        ),
        DiscoverToolItem(
            title = "矿大校历",
            subtitle = "官方教学周历与放假",
            icon = Res.drawable.calendar_today_24px,
            accent = CumtAccent.ORANGE,
            onClick = { onNavigate(Destination.WebView(CumtConstants.SCHOOL_CALENDAR_URL, null)) }
        ),
        DiscoverToolItem(
            title = "校外 VPN 指引",
            subtitle = "校外访问教务与校内网",
            icon = Res.drawable.cloud_24px,
            accent = CumtAccent.SLATE,
            onClick = { showVpnDialog = true }
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("矿大校园", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                top = 4.dp,
                bottom = navBottom + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部身份认证渐变 Hero 卡
            item {
                val isBound = profile.username.isNotBlank()
                CumtHeroGradientCard(
                    gradientColors = if (isBound) {
                        listOf(Color(0xFF0E7C86), Color(0xFF14587A))
                    } else {
                        listOf(Color(0xFF284B63), Color(0xFF3C6E71))
                    },
                    onClick = { onNavigate(Destination.CumtAuth) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    vectorResource(Res.drawable.account_circle_24px),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isBound) {
                                        profile.name.ifBlank { greeting }
                                    } else {
                                        greeting
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                CumtStatusPill(
                                    text = if (isBound) "已认证" else "未绑定",
                                    containerColor = Color.White.copy(alpha = 0.22f),
                                    contentColor = Color.White,
                                    dotColor = if (isBound) Color(0xFF7BE0B2) else Color(0xFFFFCA73)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (isBound) {
                                    "学号 ${profile.username} · 本地密钥加密直连官服"
                                } else {
                                    "绑定矿大统一身份认证，解锁一卡通 / 图书馆 / 自动填表"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            vectorResource(Res.drawable.chevron_right_24px),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 校园卡 & 宿舍电量双实时看板
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CumtSurfaceCard(
                        onClick = { onNavigate(Destination.CumtCampusCard) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CumtIconBadge(
                                        icon = Res.drawable.credit_card_24px,
                                        accent = CumtAccent.OCEAN,
                                        size = 32.dp,
                                        iconSize = 18.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "校园卡",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::refreshCampusCard,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    if (isRefreshingCard) {
                                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            vectorResource(Res.drawable.refresh_24px),
                                            contentDescription = "刷新校园卡",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (cardInfo.updatedAtMillis > 0L) "¥ ${formatDecimal(cardInfo.balance)}" else "未查询",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = CumtAccent.OCEAN.contentColor()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "流水与在线充值",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val powerLow = dormPower.roomId.isNotBlank() && dormPower.remainingPower < 15.0
                    val powerAccent = if (powerLow) CumtAccent.CORAL else CumtAccent.TEAL
                    val powerRatio = if (dormPower.maxPower > 0.0 && dormPower.roomId.isNotBlank()) {
                        (dormPower.remainingPower / dormPower.maxPower).toFloat().coerceIn(0f, 1f)
                    } else 0f

                    CumtSurfaceCard(
                        onClick = { onNavigate(Destination.CumtDormPower) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CumtIconBadge(
                                        icon = Res.drawable.bolt_24px,
                                        accent = powerAccent,
                                        size = 32.dp,
                                        iconSize = 18.dp,
                                        cornerRadius = 10.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "宿舍电量",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::refreshDormPower,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    if (isRefreshingPower) {
                                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            vectorResource(Res.drawable.refresh_24px),
                                            contentDescription = "刷新电量",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = if (dormPower.roomId.isNotBlank()) {
                                    "${formatDecimal(dormPower.remainingPower)} 度"
                                } else {
                                    "点击绑定"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = powerAccent.contentColor()
                            )
                            Spacer(Modifier.height(6.dp))
                            if (dormPower.roomId.isNotBlank()) {
                                LinearProgressIndicator(
                                    progress = { powerRatio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = powerAccent.contentColor(),
                                    trackColor = powerAccent.containerColor(),
                                    strokeCap = StrokeCap.Round
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                text = if (dormPower.roomId.isNotBlank()) {
                                    "${dormPower.buildingName} ${dormPower.roomId}"
                                } else {
                                    "南湖 / 文昌双校区实时查电"
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

            // 近期考试聚焦条（14 天内存在考试时展示）
            if (nearestSpotlight != null) {
                item {
                    val (exam, daysLeft) = nearestSpotlight
                    CumtSurfaceCard(
                        onClick = { onNavigate(Destination.CumtExams) },
                        containerColor = CumtAccent.CORAL.containerColor().copy(alpha = 0.65f),
                        borderColor = CumtAccent.CORAL.contentColor().copy(alpha = 0.28f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CumtIconBadge(
                                icon = Res.drawable.schedule_24px,
                                accent = CumtAccent.CORAL,
                                size = 42.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CumtStatusPill(
                                        text = if (daysLeft == 0) "今天考试" else "距下一场 $daysLeft 天",
                                        containerColor = CumtAccent.CORAL.contentColor(),
                                        contentColor = MaterialTheme.colorScheme.surface
                                    )
                                    if (exam.syncedToSchedule) {
                                        CumtStatusPill(
                                            text = "已入课表",
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = CumtAccent.EMERALD.contentColor()
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = exam.courseName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = CumtAccent.CORAL.contentColor(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val meta = listOf(
                                    exam.dateTimeRaw,
                                    exam.location,
                                    exam.seatNumber.takeIf { it.isNotBlank() }?.let { "座$it" } ?: ""
                                ).filter { it.isNotBlank() }.joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CumtAccent.CORAL.contentColor().copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                vectorResource(Res.drawable.chevron_right_24px),
                                contentDescription = null,
                                tint = CumtAccent.CORAL.contentColor()
                            )
                        }
                    }
                }
            }

            // 校园功能网格标题
            item {
                CumtSectionHeader(
                    title = "矿大百宝箱",
                    subtitle = "点击直达校内教务与生活服务"
                )
            }

            // 两列卡片网格
            val rows = tools.chunked(2)
            items(rows.size) { rowIdx ->
                val pair = rows[rowIdx]
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
    CumtSurfaceCard(
        onClick = item.onClick,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CumtIconBadge(
                    icon = item.icon,
                    accent = item.accent,
                    size = 42.dp,
                    iconSize = 21.dp,
                    cornerRadius = 13.dp
                )
                if (!item.badge.isNullOrBlank()) {
                    CumtStatusPill(
                        text = item.badge,
                        containerColor = item.accent.containerColor(),
                        contentColor = item.accent.contentColor()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
