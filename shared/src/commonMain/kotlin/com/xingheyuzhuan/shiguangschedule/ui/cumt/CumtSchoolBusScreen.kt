package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.BusCampus
import com.xingheyuzhuan.shiguangschedule.cumt.BusDayType
import com.xingheyuzhuan.shiguangschedule.cumt.BusLine
import com.xingheyuzhuan.shiguangschedule.cumt.CumtBusData
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.vectorResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.info_24px
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CumtSchoolBusScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit
) {
    val now = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
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

    val upcomingTimes = remember(times, now) {
        times.mapNotNull { t ->
            CumtBusData.getSoonMinutes(t, now.hour, now.minute)?.let { mins -> t to mins }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("矿大校车时刻表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(vectorResource(Res.drawable.arrow_back_24px), contentDescription = "返回")
                    }
                },
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
            PrimaryTabRow(selectedTabIndex = campusIndex) {
                BusCampus.entries.forEachIndexed { idx, campus ->
                    Tab(
                        selected = campusIndex == idx,
                        onClick = { campusIndex = idx },
                        text = { Text(campus.displayName) }
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 筛选条
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BusDayType.entries.forEach { dayType ->
                            FilterChip(
                                selected = selectedDayType == dayType,
                                onClick = { selectedDayType = dayType },
                                label = { Text(dayType.displayName) }
                            )
                        }
                        if (selectedDayType == BusDayType.WORKDAY) {
                            BusLine.entries.forEach { line ->
                                FilterChip(
                                    selected = selectedLine == line,
                                    onClick = { selectedLine = line },
                                    label = { Text(line.displayName) }
                                )
                            }
                        }
                    }
                }

                // 一小时内即将发车横幅
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1 小时内即将发车",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        onNavigate(Destination.WebView(CumtConstants.SCHOOL_BUS_ANNOUNCEMENT_URL, null))
                                    }
                                ) { Text("官网公共服务公告") }
                            }
                            if (upcomingTimes.isEmpty()) {
                                Text(
                                    text = "未来 60 分钟内暂无此线路班次",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            } else {
                                Spacer(Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    upcomingTimes.forEach { (time, mins) ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = if (mins == 0) "$time (即将发车)" else "$time (${mins}分钟后)",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 全部时刻网格
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedDayType == BusDayType.WEEKEND) {
                                "${selectedCampus.displayName}（节假日通用 · 共 ${times.size} 班）"
                            } else {
                                "${selectedCampus.displayName} · ${selectedLine.displayName}（共 ${times.size} 班）"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { showRoutesDialog = true }) {
                            Text("查看停靠站点")
                        }
                    }
                }

                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        times.forEach { t ->
                            val soonMins = CumtBusData.getSoonMinutes(t, now.hour, now.minute)
                            val isSoon = soonMins != null
                            Surface(
                                color = if (isSoon) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = if (isSoon) "$t · ${soonMins}m" else t,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSoon) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSoon) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(CumtBusData.routes) { route ->
                        Column {
                            Text(route.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = route.stops.joinToString(" → "),
                                style = MaterialTheme.typography.bodySmall
                            )
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
