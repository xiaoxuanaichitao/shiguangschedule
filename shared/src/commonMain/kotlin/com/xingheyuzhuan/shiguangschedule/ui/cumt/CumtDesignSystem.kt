package com.xingheyuzhuan.shiguangschedule.ui.cumt

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xingheyuzhuan.shiguangschedule.ui.theme.LocalIsDarkTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import shiguangschedule.shared.generated.resources.Res
import shiguangschedule.shared.generated.resources.arrow_back_24px

enum class CumtAccent(
    val lightBg: Color,
    val lightFg: Color,
    val darkBg: Color,
    val darkFg: Color
) {
    INDIGO(Color(0xFFE7ECFF), Color(0xFF2D4CCA), Color(0xFF1E2952), Color(0xFF9EB4FF)),
    CORAL(Color(0xFFFFEAE6), Color(0xFFD1432E), Color(0xFF4A1D18), Color(0xFFFFA596)),
    AMBER(Color(0xFFFFF2DB), Color(0xFFB86E00), Color(0xFF453010), Color(0xFFFFCA73)),
    EMERALD(Color(0xFFE2F7EE), Color(0xFF137E52), Color(0xFF143828), Color(0xFF7BE0B2)),
    OCEAN(Color(0xFFE1F3FF), Color(0xFF0A6CA8), Color(0xFF13314A), Color(0xFF83CEFF)),
    TEAL(Color(0xFFE0F7F6), Color(0xFF0B7872), Color(0xFF113836), Color(0xFF7AE5DD)),
    SKY(Color(0xFFE6F0FF), Color(0xFF1E64D4), Color(0xFF182D4F), Color(0xFF8FB8FF)),
    VIOLET(Color(0xFFF1EAFF), Color(0xFF6B3CC9), Color(0xFF312052), Color(0xFFC5A8FF)),
    ORANGE(Color(0xFFFFEFE2), Color(0xFFC95E12), Color(0xFF452815), Color(0xFFFFB27D)),
    SLATE(Color(0xFFECEFF4), Color(0xFF495668), Color(0xFF262B33), Color(0xFFB5C0CF));

    @Composable
    fun containerColor(): Color = if (LocalIsDarkTheme.current) darkBg else lightBg

    @Composable
    fun contentColor(): Color = if (LocalIsDarkTheme.current) darkFg else lightFg
}

@Composable
fun CumtSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 22.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale"
    )
    val clickModifier = if (onClick != null) {
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(clickModifier),
        shape = shape,
        color = containerColor,
        tonalElevation = 1.dp,
        border = BorderStroke(0.8.dp, borderColor)
    ) {
        Column(content = content)
    }
}

@Composable
fun CumtHeroGradientCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color>,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .clip(shape)
        .background(Brush.linearGradient(gradientColors))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Box(modifier = baseModifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val circleColor = Color.White.copy(alpha = 0.08f)
            drawCircle(
                color = circleColor,
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 0.92f, size.height * 0.15f)
            )
            drawCircle(
                color = circleColor,
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.12f, size.height * 0.95f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        content()
    }
}

@Composable
fun CumtIconBadge(
    icon: DrawableResource,
    accent: CumtAccent,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    cornerRadius: Dp = 14.dp
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = accent.containerColor()
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = vectorResource(icon),
                contentDescription = null,
                tint = accent.contentColor(),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun CumtStatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    dotColor: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = vectorResource(icon),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CumtSegmentedBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(index) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (selected) 1.5.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CumtSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun CumtEmptyState(
    icon: DrawableResource,
    accent: CumtAccent,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CumtIconBadge(
                icon = icon,
                accent = accent,
                size = 64.dp,
                iconSize = 32.dp,
                cornerRadius = 20.dp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!primaryActionText.isNullOrBlank() && onPrimaryAction != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPrimaryAction,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(primaryActionText)
                    }
                    if (!secondaryActionText.isNullOrBlank() && onSecondaryAction != null) {
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(secondaryActionText)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CumtTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    navIcon: ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = navIcon ?: vectorResource(Res.drawable.arrow_back_24px),
                    contentDescription = "返回"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f)
        ),
        actions = actions
    )
}
