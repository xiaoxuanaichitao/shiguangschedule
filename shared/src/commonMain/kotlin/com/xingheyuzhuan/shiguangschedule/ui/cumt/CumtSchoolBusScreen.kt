package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.BusCampus
import com.xingheyuzhuan.shiguangschedule.cumt.BusDayType
import com.xingheyuzhuan.shiguangschedule.cumt.BusLine
import com.xingheyuzhuan.shiguangschedule.cumt.CumtBusData
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import kotlinx.coroutines.delay
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.vectorResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.directions_bus_24px
import shiguangschedule.shared.generated.resources.info_24px
import kotlin.time.Clock

private fun parseBusMinutesOfDay(timeStr: String): Int? {
    val parts = timeStr.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CumtSchoolBusScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit
) {
    var now by remember {
        mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
    }

    var campusIndex by remember { mutableIntStateOf(0) }
    var selectedLine by remember { mutableStateOf(BusLine.NORTH) }
    var selectedDayType by remember {
        val isWeekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        mutableStateOf(if (isWeekend) BusDayType.WEEKEND else BusDayType.WORKDAY)
    }
    var showRoutesDialog by remember { mutableStateOf(false) }

    val selectedCampus = if (campusIndex == 0) BusCampus.NANHU else BusCampus.WENCHANG
    val times = remember(selectedCampus, selectedLine, selectedDayType) {
        CumtBusData.getTimes(selectedCampus, selectedLine, selectedDayType)
    }

    val nowMinutes = now.hour * 60 + now.minute
    val upcomingTimes = remember(times, now) {
        times.mapNotNull { t ->
            CumtBusData.getSoonMinutes(t, now.hour, now.minute)?.let { mins -> t to mins }
        }
    }
    val nextDeparture = remember(times, nowMinutes) {
        times.firstOrNull { (parseBusMinutesOfDay(it) ?: -1) >= nowMinutes }
    }

    Scaffold(
        topBar = {
            CumtTopBar(
                title = "矿大校车时刻表",
                subtitle = "${selectedCampus.displayName} · 当前时间 ${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showRoutesDialog = true }) {
                        Icon(vectorResource(Res.drawable.info_24px), contentDescription = "停靠站点")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 校区切换分段栏
            CumtSegmentedBar(
                options = BusCampus.entries.map { it.displayName },
                selectedIndex = campusIndex,
                onSelect = { campusIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 日期类型与线路筛选
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CumtSegmentedBar(
                            options = BusDayType.entries.map { it.displayName },
                            selectedIndex = BusDayType.entries.indexOf(selectedDayType).coerceAtLeast(0),
                            onSelect = { selectedDayType = BusDayType.entries[it] },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (selectedDayType == BusDayType.WORKDAY) {
                            CumtSegmentedBar(
                                options = BusLine.entries.map { it.displayName },
                                selectedIndex = BusLine.entries.indexOf(selectedLine).coerceAtLeast(0),
                                onSelect = { selectedLine = BusLine.entries[it] },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 下一班次聚焦卡片
                item {
                    CumtHeroGradientCard(
                        gradientColors = listOf(Color(0xFF1E64D4), Color(0xFF2D4CCA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        vectorResource(Res.drawable.directions_bus_24px),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "1 小时内即将发车",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(50),
                                    onClick = {
                                        onNavigate(Destination.WebView(CumtConstants.SCHOOL_BUS_ANNOUNCEMENT_URL, null))
                                    }
                                ) {
                                    Text(
                                        text = "官网公告 ›",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            if (upcomingTimes.isNotEmpty()) {
                                val (firstTime, firstMins) = upcomingTimes.first()
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = firstTime,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Surface(
                                        color = Color.White.copy(alpha = 0.22f),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    ) {
                                        Text(
                                            text = if (firstMins == 0) "即将发车" else "${firstMins} 分钟后发车",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (upcomingTimes.size > 1) {
                                    Spacer(Modifier.height(12.dp))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        upcomingTimes.drop(1).forEach { (time, mins) ->
                                            Surface(
                                                color = Color.White.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text(
                                                    text = if (mins == 0) "$time (即将)" else "$time (${mins}m)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (nextDeparture != null) {
                                        "未来 60 分钟内暂无班次，下一班发车时间为 $nextDeparture"
                                    } else {
                                        "今日该线路发车班次已全部结束"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }

                // 全部时刻表网格
                item {
                    CumtSectionHeader(
                        title = if (selectedDayType == BusDayType.WEEKEND) {
                            "${selectedCampus.displayName}（节假日通用）"
                        } else {
                            "${selectedCampus.displayName} · ${selectedLine.displayName}"
                        },
                        subtitle = "共 ${times.size} 个班次 · 灰色为今日已发班次",
                        trailing = {
                            TextButton(onClick = { showRoutesDialog = true }) {
                                Text("停靠站点")
                            }
                        }
                    )
                }

                item {
                    CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            times.chunked(4).forEach { rowTimes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowTimes.forEach { t ->
                                        val soonMins = CumtBusData.getSoonMinutes(t, now.hour, now.minute)
                                        val isSoon = soonMins != null
                                        val minsOfDay = parseBusMinutesOfDay(t)
                                        val isDeparted = minsOfDay != null && minsOfDay < nowMinutes

                                        Surface(
                                            color = if (isSoon) {
                                                CumtAccent.SKY.containerColor()
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            border = if (isSoon) {
                                                BorderStroke(1.dp, CumtAccent.SKY.contentColor().copy(alpha = 0.45f))
                                            } else null,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (isDeparted && !isSoon) 0.42f else 1f)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .height(50.dp)
                                                    .padding(horizontal = 4.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = t,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSoon) FontWeight.ExtraBold else FontWeight.SemiBold,
                                                        color = if (isSoon) {
                                                            CumtAccent.SKY.contentColor()
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                        textAlign = TextAlign.Center
                                                    )
                                                    if (isSoon) {
                                                        Text(
                                                            text = if (soonMins == 0) "即将发车" else "${soonMins}分钟",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = CumtAccent.SKY.contentColor(),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(4 - rowTimes.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRoutesDialog) {
        AlertDialog(
            onDismissRequest = { showRoutesDialog = false },
            title = { Text("校车沿途停靠站点") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(CumtBusData.routes) { route ->
                        CumtSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = route.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = route.stops.joinToString(" → "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRoutesDialog = false }) { Text("关闭") }
            }
        )
    }
}
