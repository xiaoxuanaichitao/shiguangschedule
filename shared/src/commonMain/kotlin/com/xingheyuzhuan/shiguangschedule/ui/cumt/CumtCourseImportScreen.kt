package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.Destination
import com.xingheyuzhuan.shiguangschedule.cumt.CumtConstants
import com.xingheyuzhuan.shiguangschedule.cumt.CumtServiceRepository
import com.xingheyuzhuan.shiguangschedule.cumt.CumtWebScripts
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_back_24px
import shiguangschedule.shared.generated.resources.chevron_right_24px
import shiguangschedule.shared.generated.resources.download_24px
import shiguangschedule.shared.generated.resources.info_24px
import shiguangschedule.shared.generated.resources.school_24px
import shiguangschedule.shared.generated.resources.sync_alt_24px

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtCourseImportScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit
) {
    val serviceRepo: CumtServiceRepository = koinInject()
    var intranetOk by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        intranetOk = serviceRepo.checkIntranet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("矿大教务导课表") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // 校园内网连接状态横幅
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (intranetOk) {
                        true -> MaterialTheme.colorScheme.primaryContainer
                        false -> MaterialTheme.colorScheme.errorContainer
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = when (intranetOk) {
                            true -> "已接入中国矿业大学校内网络"
                            false -> "未检测到矿大教务内网直连"
                            null -> "正在检测校内教务连通性..."
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (intranetOk == false) {
                            "本科/研究生教务系统仅限校内网或 VPN 访问，若校外访问失败请先挂载矿大 VPN。"
                        } else {
                            "提示：若已在「我的 / 校园」中绑定统一身份认证，进入教务网页时将自动填充学号密码。"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (intranetOk == false) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    onNavigate(Destination.WebView(CumtConstants.VPN_GUIDE_WEB_URL, null))
                                }
                            ) { Text("WebVPN 指引") }
                            OutlinedButton(
                                onClick = {
                                    onNavigate(Destination.WebView(CumtConstants.VPN_GUIDE_CLIENT_URL, null))
                                }
                            ) { Text("客户端 VPN 指引") }
                        }
                    }
                }
            }

            ImportEntryCard(
                icon = Res.drawable.school_24px,
                title = "本科生教务课表导入",
                subtitle = "适配中国矿业大学本科生教务管理系统（jwxt.cumt.edu.cn），登录并显示课表后点击底部「执行导入」即可解析学分、单双周及参考开学日期。",
                badge = "正方教务",
                onClick = {
                    onNavigate(
                        Destination.WebView(
                            initialUrl = CumtConstants.JWXT_SCHEDULE_URL,
                            assetJsPath = CumtWebScripts.KEY_BK
                        )
                    )
                }
            )

            ImportEntryCard(
                icon = Res.drawable.school_24px,
                title = "研究生教务课表导入",
                subtitle = "适配中国矿业大学研究生管理系统（yjsxt.cumt.edu.cn），登录后将自动导航到个人课表页，点击底部「执行导入」完成课程提取。",
                badge = "研究生院",
                onClick = {
                    onNavigate(
                        Destination.WebView(
                            initialUrl = CumtConstants.YJSXT_HOME_URL,
                            assetJsPath = CumtWebScripts.KEY_YJS
                        )
                    )
                }
            )

            ImportEntryCard(
                icon = Res.drawable.sync_alt_24px,
                title = "本地 JSON / ICS 导入与导出",
                subtitle = "从本地共享课表文件或 iCalendar 日历导入，或导出当前矿大课表分享给同学。",
                badge = "文件互通",
                onClick = { onNavigate(Destination.CourseTableConversion) }
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        vectorResource(Res.drawable.info_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "导入课程时会自动应用矿大南湖/文昌标准 12 节课作息时间表（第 1 节 08:00 起至第 12 节 22:35 止，每节 50 分钟）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImportEntryCard(
    icon: DrawableResource,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = vectorResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = vectorResource(Res.drawable.chevron_right_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
