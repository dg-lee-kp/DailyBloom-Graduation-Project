package com.example.dailybloom

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailybloom.ui.theme.*

private val categoryOptions = listOf("건강", "공부/성장", "마음관리", "생활정리", "인간관계")
private val timeOptions = listOf("아침", "점심/오후", "저녁", "자기 전", "아직 모르겠어요")
private val difficultyOptions = listOf("아주 가볍게", "적당히", "조금 도전적으로")
private val frictionOptions = listOf("귀찮음", "시간 부족", "동기 부족", "자주 미루기", "스마트폰/콘텐츠")
private val dayLabels = listOf("일", "월", "화", "수", "목", "금", "토")

private const val ONBOARDING_STEP_COUNT = 5

@Composable
fun OnboardingScreen(
    appViewModel: AppViewModel,
    onComplete: () -> Unit
) {
    val recommendation by appViewModel.onboardingRecommendation.collectAsState()
    val isLoading by appViewModel.isOnboardingLoading.collectAsState()
    val error by appViewModel.onboardingError.collectAsState()

    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedTime by remember { mutableStateOf("저녁") }
    var selectedDifficulty by remember { mutableStateOf("아주 가볍게") }
    var selectedFrictions by remember { mutableStateOf(setOf<String>()) }
    var extra by remember { mutableStateOf("") }
    var currentStep by remember { mutableIntStateOf(0) }
    var previousStep by remember { mutableIntStateOf(0) }

    val canMoveNext = when (currentStep) {
        0 -> selectedCategories.isNotEmpty()
        1 -> selectedTime.isNotBlank()
        2 -> selectedDifficulty.isNotBlank()
        else -> true
    }
    val canGenerate = selectedCategories.isNotEmpty() && selectedTime.isNotBlank() &&
        selectedDifficulty.isNotBlank() && !isLoading

    fun moveToStep(step: Int) {
        previousStep = currentStep
        currentStep = step.coerceIn(0, ONBOARDING_STEP_COUNT - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        OnboardingVideoBackground(
            modifier = Modifier.matchParentSize(),
            opacity = 1f
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BackgroundLight.copy(alpha = 0.18f),
                            GradientStart.copy(alpha = 0.10f),
                            BackgroundLight.copy(alpha = 0.32f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(GreenLight, Green))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(text = "DailyBloom 시작하기", fontSize = 24.sp, color = ForegroundDark)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "몇 가지 질문을 하나씩 답하면 오늘부터 시작하기 좋은 작은 습관을 추천해드릴게요.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Black,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(24.dp))

            if (recommendation == null) {
                StepProgress(currentStep = currentStep)
                Spacer(Modifier.height(14.dp))

                OnboardingStepContent(
                    currentStep = currentStep,
                    previousStep = previousStep,
                    selectedCategories = selectedCategories,
                    selectedTime = selectedTime,
                    selectedDifficulty = selectedDifficulty,
                    selectedFrictions = selectedFrictions,
                    extra = extra,
                    onToggleCategory = { option ->
                        selectedCategories = if (option in selectedCategories)
                            selectedCategories - option
                        else
                            selectedCategories + option
                    },
                    onSelectTime = { selectedTime = it },
                    onSelectDifficulty = { selectedDifficulty = it },
                    onToggleFriction = { option ->
                        selectedFrictions = if (option in selectedFrictions)
                            selectedFrictions - option
                        else
                            selectedFrictions + option
                    },
                    onExtraChange = { extra = it }
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(text = it, color = ErrorRed, fontSize = 13.sp)
                }

                Spacer(Modifier.height(16.dp))
                OnboardingStepButtons(
                    currentStep = currentStep,
                    canMoveNext = canMoveNext,
                    canGenerate = canGenerate,
                    isLoading = isLoading,
                    onPrevious = { moveToStep(currentStep - 1) },
                    onNext = { moveToStep(currentStep + 1) },
                    onGenerate = {
                        appViewModel.generateOnboardingRecommendation(
                            categories = selectedCategories.toList(),
                            preferredTime = selectedTime,
                            difficulty = selectedDifficulty,
                            frictions = selectedFrictions.toList(),
                            extra = extra
                        )
                    }
                )
            } else {
                RecommendationCard(
                    recommendation = recommendation!!,
                    isLoading = isLoading,
                    onRefresh = {
                        appViewModel.clearOnboardingRecommendation()
                        appViewModel.generateOnboardingRecommendation(
                            categories = selectedCategories.toList(),
                            preferredTime = selectedTime,
                            difficulty = selectedDifficulty,
                            frictions = selectedFrictions.toList(),
                            extra = extra
                        )
                    },
                    onEdit = {
                        appViewModel.clearOnboardingRecommendation()
                        moveToStep(ONBOARDING_STEP_COUNT - 1)
                    },
                    onUpdateHabit = { index, habit ->
                        appViewModel.updateOnboardingHabit(index, habit)
                    },
                    onConfirm = { appViewModel.completeOnboarding(onComplete) }
                )

                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(text = it, color = ErrorRed, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StepProgress(currentStep: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentStep + 1} / $ONBOARDING_STEP_COUNT",
                fontSize = 13.sp,
                color = Green,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "질문을 하나씩 넘겨요",
                fontSize = 13.sp,
                color = Color.Black
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (currentStep + 1) / ONBOARDING_STEP_COUNT.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = Green,
            trackColor = ProgressTrack
        )
    }
}

@Composable
@OptIn(ExperimentalAnimationApi::class)
private fun OnboardingStepContent(
    currentStep: Int,
    previousStep: Int,
    selectedCategories: Set<String>,
    selectedTime: String,
    selectedDifficulty: String,
    selectedFrictions: Set<String>,
    extra: String,
    onToggleCategory: (String) -> Unit,
    onSelectTime: (String) -> Unit,
    onSelectDifficulty: (String) -> Unit,
    onToggleFriction: (String) -> Unit,
    onExtraChange: (String) -> Unit
) {
    val forward = currentStep >= previousStep

    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            val enterOffset: (Int) -> Int = { width -> if (forward) width else -width }
            val exitOffset: (Int) -> Int = { width -> if (forward) -width else width }
            (slideInHorizontally(animationSpec = tween(280), initialOffsetX = enterOffset) + fadeIn(tween(220)))
                .togetherWith(slideOutHorizontally(animationSpec = tween(280), targetOffsetX = exitOffset) + fadeOut(tween(160)))
                .using(SizeTransform(clip = false))
        },
        label = "onboardingStep"
    ) { step ->
        when (step) {
            0 -> OnboardingQuestionCard(title = "어떤 방향의 습관을 만들고 싶나요?") {
                MultiSelectChips(
                    options = categoryOptions,
                    selected = selectedCategories,
                    onToggle = onToggleCategory
                )
            }
            1 -> OnboardingQuestionCard(title = "하루 중 언제 실천하기 가장 편한가요?") {
                SingleSelectChips(
                    options = timeOptions,
                    selected = selectedTime,
                    onSelect = onSelectTime
                )
            }
            2 -> OnboardingQuestionCard(title = "어느 정도 페이스로 시작하고 싶나요?") {
                SingleSelectChips(
                    options = difficultyOptions,
                    selected = selectedDifficulty,
                    onSelect = onSelectDifficulty
                )
            }
            3 -> OnboardingQuestionCard(title = "요즘 가장 방해되는 것은 무엇인가요?") {
                MultiSelectChips(
                    options = frictionOptions,
                    selected = selectedFrictions,
                    onToggle = onToggleFriction
                )
            }
            else -> OnboardingQuestionCard(title = "원하거나 피하고 싶은 습관이 있나요?") {
                OutlinedTextField(
                    value = extra,
                    onValueChange = onExtraChange,
                    placeholder = {
                        Text(
                            text = "예: 아침 운동은 피하고 싶어요",
                            color = MutedSecondary,
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = InputBorder,
                        focusedBorderColor = Green,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    maxLines = 4
                )
            }
        }
    }
}

@Composable
private fun OnboardingStepButtons(
    currentStep: Int,
    canMoveNext: Boolean,
    canGenerate: Boolean,
    isLoading: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onGenerate: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (currentStep > 0) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = !isLoading,
                modifier = Modifier
                    .weight(0.9f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                border = BorderStroke(1.dp, BorderGreenMuted)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "이전", fontSize = 15.sp)
            }
        }

        PrimaryOnboardingButton(
            label = if (currentStep == ONBOARDING_STEP_COUNT - 1) "추천 습관 만들기" else "다음",
            enabled = if (currentStep == ONBOARDING_STEP_COUNT - 1) canGenerate else canMoveNext && !isLoading,
            isLoading = isLoading,
            onClick = if (currentStep == ONBOARDING_STEP_COUNT - 1) onGenerate else onNext,
            modifier = Modifier.weight(if (currentStep > 0) 1.2f else 1f)
        )
    }
}

@Composable
private fun OnboardingQuestionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, fontSize = 17.sp, color = ForegroundDark)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MultiSelectChips(
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SelectChip(
                text = option,
                selected = option in selected,
                onClick = { onToggle(option) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SingleSelectChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SelectChip(
                text = option,
                selected = option == selected,
                onClick = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Green else ChipUnselected)
            .border(
                width = 1.dp,
                color = if (selected) Green else BorderGreenMuted,
                shape = RoundedCornerShape(999.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else ForegroundDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun RecommendationCard(
    recommendation: OnboardingRecommendation,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onEdit: () -> Unit,
    onUpdateHabit: (Int, OnboardingHabit) -> Unit,
    onConfirm: () -> Unit
) {
    var editingHabitIndex by remember { mutableStateOf<Int?>(null) }
    editingHabitIndex?.let { index ->
        recommendation.habits.getOrNull(index)?.let { habit ->
            OnboardingHabitEditDialog(
                habit = habit,
                onDismiss = { editingHabitIndex = null },
                onSave = { updated ->
                    onUpdateHabit(index, updated)
                    editingHabitIndex = null
                }
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "추천 습관", fontSize = 18.sp, color = ForegroundDark)
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "다시 추천받기",
                        tint = Green
                    )
                }
            }

            if (recommendation.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = recommendation.summary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(16.dp))
            recommendation.habits.forEachIndexed { index, habit ->
                HabitRecommendationRow(
                    habit = habit,
                    onClick = { editingHabitIndex = index }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = recommendation.message.ifBlank { "이 습관으로 시작해볼까요?" },
                fontSize = 14.sp,
                color = ForegroundDark
            )
            Spacer(Modifier.height(16.dp))
            PrimaryOnboardingButton(
                label = "이대로 진행하기",
                enabled = !isLoading,
                isLoading = isLoading,
                onClick = onConfirm
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onEdit,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Green),
                border = BorderStroke(1.dp, BorderGreenMuted)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(text = "질문으로 돌아가기")
            }
        }
    }
}

@Composable
private fun HabitRecommendationRow(
    habit: OnboardingHabit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGreenTint)
            .clickable { onClick() }
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        HabitIconBadge(
            iconKey = habit.iconKey,
            customImageUri = null,
            size = 44.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = habit.name, fontSize = 15.sp, color = ForegroundDark)
            if (habit.description.isNotBlank()) {
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = habit.schedule.sorted().joinToString(" ") { dayLabels.getOrElse(it) { "" } },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Green
            )
        }
        Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = "추천 습관 수정",
            tint = MutedForeground,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun OnboardingHabitEditDialog(
    habit: OnboardingHabit,
    onDismiss: () -> Unit,
    onSave: (OnboardingHabit) -> Unit
) {
    var name by remember(habit) { mutableStateOf(habit.name) }
    var description by remember(habit) { mutableStateOf(habit.description) }
    var selectedSchedule by remember(habit) { mutableStateOf(habit.schedule.toSet()) }
    var selectedCategory by remember(habit) { mutableStateOf(normalizedHabitCategory(habit.category)) }
    var selectedIconKey by remember(habit) { mutableStateOf(normalizedHabitIcon(habit.iconKey)) }
    var requiresPhoto by remember(habit) { mutableStateOf(habit.requiresPhoto) }
    val canSave = name.isNotBlank() && selectedSchedule.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("추천 습관 수정") },
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
                    dayLabels.forEachIndexed { index, label ->
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
                        habit.copy(
                            name = name.trim(),
                            description = description.trim(),
                            schedule = selectedSchedule.sorted(),
                            category = selectedCategory,
                            iconKey = selectedIconKey,
                            requiresPhoto = requiresPhoto
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

@Composable
private fun PrimaryOnboardingButton(
    label: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Green,
            disabledContainerColor = ButtonDisabled,
            contentColor = Color.White,
            disabledContentColor = Color.White
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Text(text = label, fontSize = 16.sp)
        }
    }
}
