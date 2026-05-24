package com.example.dailybloom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailybloom.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Message(
    val text: String,
    val isSent: Boolean,
    val timestamp: String = SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date())
)

private data class QuickAction(val label: String, val icon: ImageVector)

private val quickActions = listOf(
    QuickAction("목표 수정하기", Icons.Outlined.TrackChanges),
    QuickAction("난이도 조정", Icons.Outlined.TrendingUp),
    QuickAction("습관 추가", Icons.Outlined.Add),
    QuickAction("습관 줄이기", Icons.Outlined.Remove),
)

private val chatDayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun AIChatScreen(
    currentRoute: String,
    navActions: BottomNavActions,
    appViewModel: AppViewModel
) {
    val messages by appViewModel.chatMessages.collectAsState()
    val isLoading by appViewModel.isChatLoading.collectAsState()
    val pendingAction by appViewModel.pendingChatAction.collectAsState()
    val habits by appViewModel.habits.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var editingPendingAction by remember { mutableStateOf<PendingChatAction?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    val showQuickActions = messages.isEmpty() && !isLoading

    // Scroll to bottom whenever messages change or loading state toggles
    LaunchedEffect(messages.size, isLoading) {
        val itemCount = messages.size + if (isLoading) 1 else 0
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    // Scroll to bottom when re-entering the screen with existing messages
    LaunchedEffect(Unit) {
        val itemCount = messages.size + if (isLoading) 1 else 0
        if (itemCount > 0) {
            listState.scrollToItem(itemCount - 1)
        }
    }

    LaunchedEffect(pendingAction) {
        if (pendingAction == null) editingPendingAction = null
        if (pendingAction != null) showConfirmationDialog = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(
                color = Color.White.copy(alpha = 0.9f),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Green.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "AI 상담", fontSize = 20.sp, color = ForegroundDark)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "언제든지 편하게 이야기 나눠요",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = MutedForeground
                        )
                    }
                    // Clear button — only visible when there is a conversation
                    if (messages.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceLight)
                                .clickable { appViewModel.clearChat() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "대화 초기화",
                                tint = MutedForeground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = Green.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    pendingAction?.let { action ->
                        ChatConfirmationCard(
                            pendingAction = action,
                            isLoading = isLoading,
                            onConfirm = { appViewModel.confirmPendingChatAction() },
                            onCancel = { appViewModel.cancelPendingChatAction() },
                            onEdit = { editingPendingAction = action }
                        )
                    }
                    ChatInputBar(
                        inputText = inputText,
                        onTextChange = { inputText = it },
                        onSend = {
                            appViewModel.sendChatMessage(inputText)
                            inputText = ""
                        },
                        isLoading = isLoading
                    )
                    BottomNavBar(currentRoute, navActions)
                }
            }
        }
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                itemsIndexed(messages) { _, message ->
                    ChatMessageRow(message)
                }

                if (isLoading) {
                    item { AiLoadingBubble() }
                }

                if (showQuickActions) {
                    item {
                        QuickActionGrid(
                            actions = quickActions,
                            onAction = { appViewModel.sendChatMessage(it) }
                        )
                    }
                }
            }
        }
    }

    editingPendingAction?.takeIf { it.canEditDirectly() }?.let { action ->
        PendingHabitActionEditDialog(
            action = action,
            targetHabit = habits?.firstOrNull { it.id == action.habitId },
            onDismiss = { editingPendingAction = null },
            onSave = { updatedAction ->
                appViewModel.updatePendingChatAction(updatedAction)
                editingPendingAction = null
            }
        )
    }

    pendingAction?.let { action ->
        if (showConfirmationDialog && editingPendingAction == null) {
            ChatConfirmationDialog(
                pendingAction = action,
                isLoading = isLoading,
                onConfirm = {
                    showConfirmationDialog = false
                    appViewModel.confirmPendingChatAction()
                },
                onCancel = {
                    showConfirmationDialog = false
                    appViewModel.cancelPendingChatAction()
                },
                onEdit = {
                    showConfirmationDialog = false
                    editingPendingAction = action
                },
                onBackToChat = { showConfirmationDialog = false }
            )
        }
    }
}

@Composable
private fun ChatConfirmationCard(
    pendingAction: PendingChatAction,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit
) {
    val canEditDirectly = pendingAction.canEditDirectly()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGreenTint),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Rule,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "이렇게 수정할까요?",
                    fontSize = 14.sp,
                    color = ForegroundDark
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = pendingAction.displayDescription(),
                fontSize = 14.sp,
                color = ForegroundDark,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "이대로 적용하거나, 아래 채팅창에서 원하는 대로 다시 말해도 좋아요.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )
            Spacer(Modifier.height(10.dp))
            if (canEditDirectly) {
                OutlinedButton(
                    enabled = !isLoading,
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                    border = BorderStroke(1.dp, Green.copy(alpha = 0.25f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("직접 수정하기")
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    enabled = !isLoading,
                    onClick = onCancel
                ) {
                    Text("취소")
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    enabled = !isLoading,
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("이대로 적용")
                }
            }
        }
    }
}

@Composable
private fun ChatConfirmationDialog(
    pendingAction: PendingChatAction,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onEdit: () -> Unit,
    onBackToChat: () -> Unit
) {
    val canEditDirectly = pendingAction.canEditDirectly()

    AlertDialog(
        onDismissRequest = {},
        icon = {
            Icon(
                imageVector = Icons.Outlined.Rule,
                contentDescription = null,
                tint = Green
            )
        },
        title = { Text("정말 이렇게 변경할까요?") },
        text = {
            Column {
                Text(
                    text = pendingAction.displayDescription(),
                    color = ForegroundDark,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "확인 전에는 습관이 추가, 수정, 삭제되지 않아요.",
                    color = MutedForeground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 19.sp
                )
                if (canEditDirectly) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        enabled = !isLoading,
                        onClick = onEdit,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                        border = BorderStroke(1.dp, Green.copy(alpha = 0.25f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("직접 수정하기")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("이대로 적용")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    enabled = !isLoading,
                    onClick = onBackToChat
                ) {
                    Text("채팅에서 수정")
                }
                TextButton(
                    enabled = !isLoading,
                    onClick = onCancel
                ) {
                    Text("취소")
                }
            }
        }
    )
}

@Composable
private fun PendingHabitActionEditDialog(
    action: PendingChatAction,
    targetHabit: Habit?,
    onDismiss: () -> Unit,
    onSave: (PendingChatAction) -> Unit
) {
    val initialName = action.name.ifBlank {
        if (action.action == "edit_habit") targetHabit?.name.orEmpty() else ""
    }
    val initialDescription = when {
        action.action == "add_habit" || action.hasDescription -> action.habitDescription
        action.action == "edit_habit" -> targetHabit?.description.orEmpty()
        else -> action.habitDescription
    }
    val initialSchedule = when {
        action.action == "add_habit" || action.hasSchedule -> action.schedule
        action.action == "edit_habit" -> targetHabit?.schedule ?: emptyList()
        else -> action.schedule
    }
    val initialCategory = when {
        action.action == "add_habit" || action.hasCategory -> action.category
        action.action == "edit_habit" -> targetHabit?.category ?: action.category
        else -> action.category
    }
    val initialIconKey = when {
        action.action == "add_habit" || action.hasIconKey -> action.iconKey
        action.action == "edit_habit" -> targetHabit?.iconKey ?: action.iconKey
        else -> action.iconKey
    }
    val initialRequiresPhoto = when {
        action.action == "add_habit" || action.hasRequiresPhoto -> action.requiresPhoto
        action.action == "edit_habit" -> targetHabit?.requiresPhoto ?: action.requiresPhoto
        else -> action.requiresPhoto
    }

    var name by remember(action, targetHabit) { mutableStateOf(initialName) }
    var description by remember(action, targetHabit) { mutableStateOf(initialDescription) }
    var selectedSchedule by remember(action, targetHabit) { mutableStateOf(initialSchedule.toSet()) }
    var selectedCategory by remember(action, targetHabit) { mutableStateOf(normalizedHabitCategory(initialCategory)) }
    var selectedIconKey by remember(action, targetHabit) { mutableStateOf(normalizedHabitIcon(initialIconKey)) }
    var requiresPhoto by remember(action, targetHabit) { mutableStateOf(initialRequiresPhoto) }
    val canSave = name.isNotBlank() && selectedSchedule.isNotEmpty() &&
        (action.action != "edit_habit" || action.habitId != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (action.action == "edit_habit") "수정 내용 직접 조정" else "습관 직접 수정") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("습관 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = InputBorder,
                        focusedBorderColor = Green,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("설명") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = InputBorder,
                        focusedBorderColor = Green,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    maxLines = 3
                )
                Spacer(Modifier.height(16.dp))
                Text(text = "요일", fontSize = 14.sp, color = ForegroundDark)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chatDayLabels.forEachIndexed { index, label ->
                        val selected = index in selectedSchedule
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Green else ChipUnselected)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) Green else BorderGreenMuted,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedSchedule = if (selected) {
                                        selectedSchedule - index
                                    } else {
                                        selectedSchedule + index
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else ForegroundDark
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                HabitCategorySelector(
                    selectedCategory = selectedCategory,
                    onSelect = { selectedCategory = it }
                )
                Spacer(Modifier.height(16.dp))
                HabitIconSelector(
                    selectedIconKey = selectedIconKey,
                    customImageUri = null,
                    onSelect = { selectedIconKey = it }
                )
                Spacer(Modifier.height(16.dp))
                PhotoRequirementToggle(
                    requiresPhoto = requiresPhoto,
                    onChange = { requiresPhoto = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        action.copy(
                            name = name.trim(),
                            habitDescription = description.trim(),
                            schedule = selectedSchedule.sorted(),
                            category = selectedCategory,
                            iconKey = selectedIconKey,
                            requiresPhoto = requiresPhoto,
                            hasDescription = true,
                            hasSchedule = true,
                            hasCategory = true,
                            hasIconKey = true,
                            hasRequiresPhoto = true
                        )
                    )
                }
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

private fun PendingChatAction.displayDescription(): String {
    if (action != "add_habit") return actionDescription
    val scheduleText = when (schedule) {
        listOf(0, 1, 2, 3, 4, 5, 6) -> "매일"
        else -> schedule.joinToString(" ") { chatDayLabels.getOrElse(it) { "" } }
    }
    return "'${name.ifBlank { "새 습관" }}' 습관 추가" +
        if (scheduleText.isBlank()) "" else " ($scheduleText)"
}

private fun PendingChatAction.canEditDirectly(): Boolean =
    action == "add_habit" || (action == "edit_habit" && habitId != null)

@Composable
private fun ChatMessageRow(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isSent) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!message.isSent) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GreenLight, Green)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = "AI",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isSent) 16.dp else 4.dp,
                        bottomEnd = if (message.isSent) 4.dp else 16.dp
                    )
                )
                .background(
                    if (message.isSent)
                        Brush.horizontalGradient(listOf(Green, GreenDark))
                    else
                        Brush.linearGradient(listOf(Color.White, Color.White))
                )
                .border(
                    width = if (message.isSent) 0.dp else 1.dp,
                    color = Green.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (message.isSent) 16.dp else 4.dp,
                        bottomEnd = if (message.isSent) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (message.isSent) Color.White else ForegroundDark,
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = message.timestamp,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (message.isSent) Color.White.copy(alpha = 0.7f) else MutedSecondary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        if (message.isSent) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "User",
                    tint = MutedForeground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AiLoadingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GreenLight, Green)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 4.dp, bottomEnd = 16.dp
                    )
                )
                .background(Color.White)
                .border(
                    width = 1.dp,
                    color = Green.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = 4.dp, bottomEnd = 16.dp
                    )
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Green,
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun QuickActionGrid(
    actions: List<QuickAction>,
    onAction: (String) -> Unit
) {
    Column {
        Text(
            text = "빠른 작업",
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MutedForeground,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            actions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowActions.forEach { action ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, Green.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                .clickable { onAction(action.label) }
                                .padding(16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Green.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = action.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = ForegroundDark
                                )
                            }
                        }
                    }
                    if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = "메시지를 입력하세요...",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedSecondary
                )
            },
            modifier = Modifier.weight(1f),
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Green.copy(alpha = 0.3f),
                unfocusedContainerColor = SurfaceLight,
                focusedContainerColor = SurfaceLight,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            singleLine = true
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = if (isLoading)
                            listOf(GreenDisabled, GreenDisabled)
                        else
                            listOf(Green, GreenDark)
                    )
                )
                .clickable(enabled = !isLoading) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "전송",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AIChatScreenPreview() {
    DailyBloomTheme {
        AIChatScreen("", BottomNavActions({}, {}, {}, {}, {}), androidx.lifecycle.viewmodel.compose.viewModel())
    }
}
