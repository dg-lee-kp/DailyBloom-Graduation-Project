package com.example.dailybloom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailybloom.ui.theme.*
import java.util.Calendar

@Composable
fun ReportScreen(currentRoute: String, navActions: BottomNavActions, appViewModel: AppViewModel) {
    val reportData by appViewModel.reportData.collectAsState()

    val now = remember { Calendar.getInstance() }
    LaunchedEffect(Unit) {
        appViewModel.fetchReport(now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1)
    }

    val monthlyRate    = reportData?.monthlyRate    ?: 0
    val totalCompleted = reportData?.totalCompleted ?: 0
    val weekData       = reportData?.weekData       ?: emptyList()
    val trendData      = reportData?.trendData      ?: emptyList()
    val habitStats     = reportData?.habitStats     ?: emptyList()

    // Split habit stats into best (top half by rate) and missed (bottom half or failing)
    val bestHabits   = habitStats.sortedByDescending { it.rate }.take(3)
    val missedHabits = habitStats.sortedByDescending { it.failCount }
        .filter { it.failCount > 0 }
        .take(3)
    val bestHabitName = bestHabits.firstOrNull()?.name ?: "데이터 없음"

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { BottomNavBar(currentRoute, navActions) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientStart, BackgroundLight)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 32.dp, bottom = 24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Green,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(text = "월간 리포트", fontSize = 24.sp, color = ForegroundDark)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "이번 달 습관 수행 결과를 한눈에 확인해요",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )

                Spacer(Modifier.height(24.dp))

                if (reportData == null) {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Green,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    // 2-col stat tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ReportStatTile(
                            icon = Icons.Outlined.TrendingUp,
                            label = "월간 성공률",
                            value = "$monthlyRate%",
                            modifier = Modifier.weight(1f)
                        )
                        ReportStatTile(
                            icon = Icons.Outlined.EmojiEvents,
                            label = "누적 완료",
                            value = "$totalCompleted",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Weekly bar chart
                    ReportCard(title = "최근 7일 완료 현황") {
                        if (weekData.isEmpty() || weekData.all { it.total == 0 }) {
                            EmptyDataNote()
                        } else {
                            WeekBarChart(data = weekData)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Monthly trend
                    ReportCard(title = "월간 추세") {
                        if (trendData.isEmpty()) {
                            EmptyDataNote()
                        } else {
                            TrendLineChart(data = trendData)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Best habits
                    ReportCard(title = "가장 잘한 습관") {
                        if (bestHabits.isEmpty()) {
                            EmptyDataNote()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                bestHabits.forEachIndexed { index, stat ->
                                    HabitStatRow(
                                        rank = index + 1,
                                        name = stat.name,
                                        detail = "성공 ${stat.completed}회 · 성공률 ${stat.rate}%",
                                        category = stat.category,
                                        iconKey = stat.iconKey,
                                        customImageUri = stat.customImageUri,
                                        requiresPhoto = stat.requiresPhoto,
                                        bgColor = SurfaceGreenTint
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Most missed habits
                    ReportCard(title = "자주 놓친 습관") {
                        if (missedHabits.isEmpty()) {
                            EmptyDataNote()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                missedHabits.forEach { stat ->
                                    HabitStatRow(
                                        rank = null,
                                        name = stat.name,
                                        detail = "미완료 ${stat.failCount}회 · 전체 ${stat.total}회",
                                        category = stat.category,
                                        iconKey = stat.iconKey,
                                        customImageUri = stat.customImageUri,
                                        requiresPhoto = stat.requiresPhoto,
                                        bgColor = MissedBackground
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Bottom tip tile
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Green.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Green.copy(alpha = 0.1f),
                                        GreenLight.copy(alpha = 0.1f)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = "이번 달 가장 안정적으로 이어진 습관",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MutedForeground
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(text = bestHabitName, fontSize = 18.sp, color = ForegroundDark)
                        }
                    }
                }
            }
        }
    }
}

// ── Shared card wrapper ───────────────────────────────────────────────────────

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = title, fontSize = 18.sp, color = ForegroundDark)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun EmptyDataNote() {
    Text(
        text = "아직 이번 달 데이터가 없어요",
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = MutedForeground,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    )
}

// ── Stat tile ─────────────────────────────────────────────────────────────────

@Composable
private fun ReportStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = value, fontSize = 30.sp, color = ForegroundDark)
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

@Composable
private fun WeekBarChart(data: List<WeekDayData>) {
    val barAreaHeight = 110.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Fixed-height container: fraction is now relative to barAreaHeight only,
                // so the bar can never grow into the label row below.
                Box(
                    modifier = Modifier
                        .height(barAreaHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val fraction = day.rate.coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight(fraction.coerceAtLeast(0.02f))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (day.rate >= 1f) Green else GreenLight
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = day.label.ifEmpty { "?" },
                    fontSize = 11.sp,
                    color = MutedForeground
                )
            }
        }
    }
}

// ── Line chart ────────────────────────────────────────────────────────────────

@Composable
private fun TrendLineChart(data: List<TrendPoint>) {
    val lineColor = Green
    val gridColor = ChartGrid

    Column {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            if (data.size < 2) return@Canvas
            val maxVal = data.maxOf { it.rate }.toFloat().takeIf { it > 0f } ?: 1f
            val w = size.width
            val h = size.height
            val stepX = w / (data.size - 1)

            for (i in 0..3) {
                val y = h - (i / 3f) * h
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
            }

            val path = Path()
            data.forEachIndexed { idx, point ->
                val x = idx * stepX
                val y = h - (point.rate / maxVal) * h
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

            data.forEachIndexed { idx, point ->
                val x = idx * stepX
                val y = h - (point.rate / maxVal) * h
                drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x, y))
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            data.forEach { point ->
                Text(text = point.label, fontSize = 11.sp, color = MutedForeground)
            }
        }
    }
}

// ── Habit row ─────────────────────────────────────────────────────────────────

@Composable
private fun HabitStatRow(
    rank: Int?,
    name: String,
    detail: String,
    category: String,
    iconKey: String,
    customImageUri: String?,
    requiresPhoto: Boolean,
    bgColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            HabitIconBadge(
                iconKey = iconKey,
                customImageUri = customImageUri,
                size = 40.dp
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rank?.let {
                        Text(
                            text = "$it. ",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Green
                        )
                    }
                    Text(text = name, fontSize = 15.sp, color = ForegroundDark)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
                Spacer(Modifier.height(6.dp))
                HabitMetaChips(
                    category = category,
                    requiresPhoto = requiresPhoto
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MutedSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ReportScreenPreview() {
    DailyBloomTheme { ReportScreen("", BottomNavActions({}, {}, {}, {}, {}), viewModel()) }
}
