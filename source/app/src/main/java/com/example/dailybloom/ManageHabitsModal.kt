package com.example.dailybloom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import com.example.dailybloom.ui.theme.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val dayLabels = mapOf(
    0 to "일", 1 to "월", 2 to "화", 3 to "수", 4 to "목", 5 to "금", 6 to "토"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHabitsModal(
    onDismiss: () -> Unit,
    appViewModel: AppViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val habits by appViewModel.habits.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }

    // Show EditHabitModal on top when a habit is selected for editing
    editingHabit?.let { habit ->
        EditHabitModal(
            habit = habit,
            onDismiss = { editingHabit = null },
            appViewModel = appViewModel
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color.White,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "습관 관리", fontSize = 20.sp, color = ForegroundDark)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = MutedForeground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val habitList = habits ?: emptyList()

            if (habitList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "등록된 습관이 없어요",
                        fontSize = 15.sp,
                        color = MutedForeground
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(habitList, key = { it.id }) { habit ->
                        HabitManageItem(
                            habit = habit,
                            onEditClick = { editingHabit = habit },
                            onDeleteClick = { pendingDeleteId = habit.id }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // Delete confirmation dialog
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Text(text = "습관 삭제", fontSize = 18.sp, color = ForegroundDark)
            },
            text = {
                Text(
                    text = "이 습관을 삭제할까요? 삭제된 습관은 복구할 수 없어요.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ErrorRed)
                        .clickable {
                            pendingDeleteId?.let { appViewModel.deleteHabit(it) }
                            pendingDeleteId = null
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(text = "삭제", fontSize = 14.sp, color = Color.White)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLight)
                        .clickable { pendingDeleteId = null }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(text = "취소", fontSize = 14.sp, color = ForegroundDark)
                }
            }
        )
    }
}

@Composable
private fun HabitManageItem(
    habit: Habit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    HabitIconBadge(
                        iconKey = habit.iconKey,
                        customImageUri = habit.customImageUri,
                        size = 46.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = habit.name, fontSize = 16.sp, color = ForegroundDark)
                        if (!habit.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = habit.description,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MutedForeground
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HabitMetaChips(
                            category = habit.category,
                            requiresPhoto = habit.requiresPhoto
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Edit button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProgressTrack)
                            .clickable { onEditClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = "수정",
                            tint = ForegroundDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Delete button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeleteBackground)
                            .clickable { onDeleteClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "삭제",
                            tint = DeleteForeground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Schedule day chips
            if (habit.schedule.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    habit.schedule.sorted().forEach { dayIndex ->
                        val label = dayLabels[dayIndex] ?: dayIndex.toString()
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ChipBackground)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = label, fontSize = 12.sp, color = MutedForeground)
                        }
                    }
                }
            }
        }
    }
}
