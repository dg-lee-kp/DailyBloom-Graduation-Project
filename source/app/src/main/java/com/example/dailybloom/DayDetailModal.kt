package com.example.dailybloom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import com.example.dailybloom.ui.theme.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailModal(
    year: Int,
    month: Int,
    day: Int,
    isToday: Boolean,
    appViewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val habits by appViewModel.habits.collectAsState()
    val entries by appViewModel.entries.collectAsState()

    // Which habits are scheduled for this day-of-week
    val dayOfWeek = remember(year, month, day) {
        Calendar.getInstance().apply { set(year, month - 1, day) }
            .get(Calendar.DAY_OF_WEEK) - 1  // 0 = Sunday … 6 = Saturday
    }
    val targetDateKey = remember(year, month, day) { dateKey(year, month, day) }
    val habitsForDay = remember(habits, dayOfWeek, targetDateKey) {
        habits?.filter { habit ->
            dayOfWeek in habit.schedule &&
                (habitCreatedDateKey(habit) ?: Int.MIN_VALUE) <= targetDateKey
        } ?: emptyList()
    }

    // Completion data – only valid for today
    val completedIds: Set<Int> = remember(entries, isToday) {
        if (isToday) entries?.filter { it.checked }?.map { it.habitId }?.toSet() ?: emptySet()
        else emptySet()
    }

    val completedCount = if (isToday) habitsForDay.count { it.id in completedIds } else 0
    val totalCount = habitsForDay.size
    val completionRate = if (totalCount == 0) 0 else (completedCount * 100 / totalCount)
    val perfectDay = isToday && totalCount > 0 && completedCount == totalCount

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White,
        dragHandle = null
    ) {
        // Sticky header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "${month}월 ${day}일",
                        fontSize = 20.sp,
                        color = ForegroundDark
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isToday)
                            "완료 $completedCount/$totalCount · 완료율 $completionRate%"
                        else
                            "예정 습관 ${totalCount}개",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MutedForeground
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ChipBackground),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = MutedForeground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isToday) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(ProgressTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(completionRate / 100f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Green)
                    )
                }
            }

            if (perfectDay) {
                Spacer(Modifier.height(12.dp))
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
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "🌟 이 날은 모든 습관을 완료한 100% 달성일이에요.",
                        fontSize = 14.sp,
                        color = ForegroundDark
                    )
                }
            }

            // Note for non-today dates
            if (!isToday && habitsForDay.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChipBackground)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "오늘이 아닌 날짜는 완료 여부를 확인할 수 없어요.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MutedSecondary
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Divider, thickness = 1.dp)
        }

        // Habit list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (habitsForDay.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceGreenTint)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "이 날짜에는 예정된 습관이 없어요.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MutedForeground
                        )
                    }
                }
            } else {
                items(habitsForDay, key = { it.id }) { habit ->
                    val completed = isToday && habit.id in completedIds
                    DayHabitItem(
                        habit = habit,
                        completed = completed,
                        showStatus = isToday
                    )
                }
            }
        }
    }
}

private fun dateKey(year: Int, month: Int, day: Int): Int = year * 10000 + month * 100 + day

private fun habitCreatedDateKey(habit: Habit): Int? {
    val raw = habit.createdAt.takeIf { it.isNotBlank() && it != "null" } ?: return null
    Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(raw)?.let { match ->
        val (year, month, day) = match.destructured
        return dateKey(year.toInt(), month.toInt(), day.toInt())
    }

    val parsedDate = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    ).firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).parse(raw)
        }.getOrNull()
    } ?: return null

    return Calendar.getInstance().apply { time = parsedDate }.let { calendar ->
        dateKey(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}

@Composable
private fun DayHabitItem(
    habit: Habit,
    completed: Boolean,
    showStatus: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HabitIconBadge(
                iconKey = habit.iconKey,
                customImageUri = habit.customImageUri,
                size = 48.dp
            )

            // Status icon
            if (showStatus) {
                Icon(
                    imageVector = if (completed)
                        Icons.Outlined.CheckCircle
                    else
                        Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (completed) Green else CheckCircleInactive,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(top = 2.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    fontSize = 16.sp,
                    color = ForegroundDark
                )
                if (!habit.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = habit.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MutedForeground
                    )
                }
                Spacer(Modifier.height(10.dp))
                HabitMetaChips(
                    category = habit.category,
                    requiresPhoto = habit.requiresPhoto
                )

                if (showStatus) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (completed) ProgressTrack else ChipInactive
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (completed) "완료됨" else "미완료",
                            fontSize = 12.sp,
                            color = if (completed) Green else MutedForeground
                        )
                    }
                }
            }
        }
    }
}
