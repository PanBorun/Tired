package com.tired.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tired.app.domain.model.AppCategory
import com.tired.app.presentation.theme.categoryColor

@Composable
fun SimplePieChart(
    data: Map<AppCategory, Long>,
    modifier: Modifier = Modifier
) {
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    val sortedEntries = data.entries.sortedByDescending { it.value }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 饼图
        Canvas(
            modifier = Modifier.size(160.dp)
        ) {
            var startAngle = -90f
            sortedEntries.forEachIndexed { index, (category, minutes) ->
                val sweepAngle = (minutes / total) * 360f
                drawArc(
                    color = categoryColor(category.ordinal),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = true,
                    size = Size(size.minDimension, size.minDimension),
                    topLeft = Offset(
                        (size.width - size.minDimension) / 2,
                        (size.height - size.minDimension) / 2
                    )
                )
                startAngle += sweepAngle
            }

            // 中心圆（制造环形效果）
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.28f,
                center = Offset(size.width / 2, size.height / 2)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 图例
        Column(modifier = Modifier.weight(1f)) {
            sortedEntries.take(5).forEachIndexed { index, (category, minutes) ->
                val percent = (minutes * 100 / total).toInt()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = categoryColor(category.ordinal))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${category.emoji} ${category.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${percent}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleBarChart(
    data: Map<Int, Long>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val maxValue = data.values.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val barWidth = size.width / 26f
            val chartHeight = size.height - 30f

            for (hour in 0..23) {
                val value = data[hour] ?: 0L
                val barHeight = (value / maxValue) * chartHeight
                val x = (hour + 0.5f) * (size.width / 24f)

                // 柱子
                drawRect(
                    color = barColor.copy(alpha = 0.7f),
                    topLeft = Offset(x - barWidth / 2, chartHeight - barHeight),
                    size = Size(barWidth, barHeight)
                )
            }

            // 底部时间轴线
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1f
            )
        }

        // 时间标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("0", "6", "12", "18", "23").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SimpleLineChart(
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) return

    val maxValue = data.maxOf { it.second }.toFloat().coerceAtLeast(1f)
    val minValue = 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 20f
            val points = data.mapIndexed { index, (_, value) ->
                val x = if (data.size > 1) index * chartWidth / (data.size - 1) else chartWidth / 2
                val y = chartHeight - ((value - minValue) / (maxValue - minValue)) * chartHeight
                Offset(x, y)
            }

            // 绘制区域填充
            if (points.size >= 2) {
                val path = android.graphics.Path().apply {
                    moveTo(points.first().x, chartHeight)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, chartHeight)
                    close()
                }
                drawContext.canvas.nativeCanvas.drawPath(path, android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(30, 103, 80, 164)
                    style = android.graphics.Paint.Style.FILL
                })
            }

            // 绘制连线
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = points[i],
                    end = points[i + 1],
                    strokeWidth = 3f
                )
            }

            // 绘制数据点
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = lineColor,
                    radius = 6f,
                    center = point
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = point
                )

                // 分数标签
                drawContext.canvas.nativeCanvas.drawText(
                    "${data[index].second}",
                    point.x,
                    point.y - 15f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }

        // 日期标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { (label, _) ->
                Text(
                    text = label.takeLast(5), // MM-dd
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
