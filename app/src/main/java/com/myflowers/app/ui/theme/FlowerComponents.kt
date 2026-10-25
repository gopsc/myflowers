package com.myflowers.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlantIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(size.width / 240f, size.height / 240f, pivot = Offset.Zero) {
            drawCircle(Color(0xFFE3EBD8), 98f, Offset(120f, 118f))
            drawCircle(Color(0xFFF4F3DC), 27f, Offset(188f, 53f))
            drawOval(Color(0xFF26442F).copy(alpha = .12f), Offset(67f, 214f), Size(115f, 12f))
            val stem = Path().apply {
                moveTo(124f, 174f)
                cubicTo(104f, 123f, 144f, 87f, 127f, 39f)
            }
            drawPath(stem, Color(0xFF41694A), style = Stroke(4f, cap = StrokeCap.Round))
            fun leaf(x: Float, y: Float, tipX: Float, tipY: Float, color: Color) {
                val dx = tipX - x
                val dy = tipY - y
                val path = Path().apply {
                    moveTo(x, y)
                    cubicTo(x + dx * .9f - dy * .4f, y + dy * .9f + dx * .4f, tipX - dy * .2f, tipY + dx * .2f, tipX, tipY)
                    cubicTo(tipX + dy * .3f, tipY - dx * .3f, x + dx * .5f + dy * .4f, y + dy * .5f - dx * .4f, x, y)
                }
                drawPath(path, color)
                drawLine(Color.White.copy(alpha = .20f), Offset(x, y), Offset(tipX, tipY), 1f)
            }
            leaf(119f, 146f, 55f, 104f, Color(0xFF6B9463))
            leaf(122f, 127f, 182f, 88f, Color(0xFF2E6045))
            leaf(127f, 105f, 66f, 65f, Color(0xFF3F7350))
            leaf(131f, 81f, 173f, 42f, Color(0xFF729858))
            leaf(131f, 62f, 108f, 24f, Color(0xFF426E43))
            val pot = Path().apply {
                moveTo(85f, 164f)
                lineTo(158f, 164f)
                lineTo(148f, 208f)
                quadraticTo(124f, 225f, 97f, 208f)
                close()
            }
            drawPath(pot, Brush.horizontalGradient(listOf(Color(0xFFC0805D), Color(0xFFDDAE85), Color(0xFFB67D59)), 85f, 158f))
            drawRoundRect(Color(0xFFE1B08A), Offset(81f, 160f), Size(81f, 12f), androidx.compose.ui.geometry.CornerRadius(4f))
            drawLine(Color(0xFFECD1B4), Offset(99f, 181f), Offset(104f, 205f), 3f, StrokeCap.Round)
            drawCircle(Color(0xFF92AD73), 3f, Offset(51f, 162f))
            drawCircle(Color(0xFF92AD73), 2f, Offset(193f, 149f))
        }
    }
}

@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Spa, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(8.dp))
        }
        Text("MyFlowers", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ErrorNotice(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun StatusPill(online: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (online) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (online) colors.onPrimaryContainer else colors.onSurfaceVariant
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(if (online) colors.primary else colors.onSurfaceVariant, CircleShape))
            Text(if (online) "在线" else "离线", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(modifier.size(size), shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

/** 浅色到主色容器的柔和渐变，用于页面顶部的主卡片。 */
@Composable
fun heroBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(colors.secondaryContainer, colors.primaryContainer))
}

@Composable
fun EmptyGarden(title: String, description: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlantIllustration(Modifier.size(136.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
