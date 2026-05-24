package com.example.dailybloom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailybloom.ui.theme.*
import java.util.Calendar

private fun daysInMonth(year: Int, month: Int): Int =
    Calendar.getInstance().apply { set(year, month - 1, 1) }
        .getActualMaximum(Calendar.DAY_OF_MONTH)

private fun firstWeekdayOf(year: Int, month: Int): Int =
    Calendar.getInstance().apply { set(year, month - 1, 1) }
        .get(Calendar.DAY_OF_WEEK) - 1  // 0 = Sunday

@Composable
fun CalendarScreen(currentRoute: String, navActions: BottomNavActions, appViewModel: AppViewModel) {
    val now = remember { Calendar.getInstance() }
    var currentYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    val todayDay = now.get(Calendar.DAY_OF_MONTH)
    val isThisMonth = now.get(Calendar.YEAR) == currentYear && now.get(Calendar.MONTH) + 1 == currentMonth

    val monthCalendar by appViewModel.monthCalendar.collectAsState()
    val entries by appViewModel.entries.collectAsState()

    // Fetch whenever the displayed month changes
    LaunchedEffect(currentYear, currentMonth) {
        appViewModel.fetchMonthCalendar(currentYear, currentMonth)
    }

    val totalDays = daysInMonth(currentYear, currentMonth)
    val startDay = firstWeekdayOf(currentYear, currentMonth)

    // Derived monthly summary stats
    val activeDays = monthCalendar.values.count { it.hasActivity }
    val perfectDays = monthCalendar.values.count { it.isPerfect }
    val totalCompleted = monthCalendar.values.sumOf { it.completed }
    val totalExpected = monthCalendar.values.sumOf { it.total }
    val monthSuccessRate = if (totalExpected == 0) 0 else (totalCompleted * 100 / totalExpected)

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    selectedDay?.let { day ->
        DayDetailModal(
            year = currentYear,
            month = currentMonth,
            day = day,
            isToday = isThisMonth && day == todayDay,
            appViewModel = appViewModel,
            onDismiss = { selectedDay = null }
        )
    }

    fun prevMonth() {
        if (currentMonth == 1) { currentMonth = 12; currentYear-- } else currentMonth--
    }
    fun nextMonth() {
        if (currentMonth == 12) { currentMonth = 1; currentYear++ } else currentMonth++
    }

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
                Text(text = "캘린더", fontSize = 24.sp, color = ForegroundDark)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "날짜를 탭하면 그날의 습관 현황을 볼 수 있어요",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
                Spacer(Modifier.height(24.dp))

                // ── Calendar card ─────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border = BorderStroke(1.dp, Green.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        // Month navigation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { prevMonth() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.ChevronLeft,
                                    contentDescription = "이전 달",
                                    tint = MutedForeground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "${currentYear}년 ${currentMonth}월",
                                fontSize = 20.sp,
                                color = ForegroundDark
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { nextMonth() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = "다음 달",
                                    tint = MutedForeground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Day-of-week headers
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                                Text(
                                    text = label,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MutedForeground
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Days grid
                        val rows = (startDay + totalDays + 6) / 7
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                for (col in 0 until 7) {
                                    val day = row * 7 + col - startDay + 1
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                            .aspectRatio(1f)
                                            .then(
                                                if (day in 1..totalDays)
                                                    Modifier.clickable { selectedDay = day }
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (day in 1..totalDays) {
                                            val dayData = monthCalendar[day]
                                            val todayRate = entries?.let { todayEntries ->
                                                if (todayEntries.isEmpty()) 0 else {
                                                    todayEntries.count { it.checked } * 100 / todayEntries.size
                                                }
                                            }
                                            val isTodayCell = isThisMonth && day == todayDay
                                            val completionRate = if (isTodayCell && todayRate != null) {
                                                todayRate
                                            } else {
                                                dayData?.completionRate ?: 0
                                            }
                                            val hasActivity = if (isTodayCell && entries != null) {
                                                entries?.isNotEmpty() == true
                                            } else {
                                                dayData?.hasActivity ?: false
                                            }
                                            CalendarDayCell(
                                                day = day,
                                                isToday = isTodayCell,
                                                completionRate = completionRate,
                                                hasActivity = hasActivity
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // ── Monthly summary card ──────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border = BorderStroke(1.dp, Green.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "${currentMonth}월 요약",
                            fontSize = 18.sp,
                            color = ForegroundDark
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SummaryTile(
                                label = "활동한 날",
                                value = "${activeDays}일",
                                bgColor = Green.copy(alpha = 0.08f),
                                modifier = Modifier.weight(1f)
                            )
                            SummaryTile(
                                label = "완전 달성일",
                                value = "${perfectDays}일",
                                bgColor = GreenLight.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Green.copy(alpha = 0.1f),
                                            GreenLight.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .padding(16.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "월간 성공률",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MutedForeground
                                    )
                                    Text(
                                        text = "$monthSuccessRate%",
                                        fontSize = 16.sp,
                                        color = ForegroundDark
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.6f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(monthSuccessRate / 100f)
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(Green)
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

@Composable
private fun CalendarDayCell(
    day: Int,
    isToday: Boolean,
    completionRate: Int,
    hasActivity: Boolean
) {
    val bgColor = when {
        isToday                        -> Green
        hasActivity && completionRate == 100 -> Green.copy(alpha = 0.25f)
        hasActivity && completionRate >= 75  -> GreenLight.copy(alpha = 0.35f)
        hasActivity && completionRate >= 50  -> CalendarMedium
        hasActivity                          -> CalendarLight
        else                                 -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isToday) Color.White else ForegroundDark
            )
            if (hasActivity) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$completionRate%",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isToday) Color.White.copy(alpha = 0.92f) else Green,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )
            Spacer(Modifier.height(4.dp))
            Text(text = value, fontSize = 24.sp, color = ForegroundDark)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CalendarScreenPreview() {
    DailyBloomTheme { CalendarScreen("", BottomNavActions({}, {}, {}, {}, {}), viewModel()) }
}
