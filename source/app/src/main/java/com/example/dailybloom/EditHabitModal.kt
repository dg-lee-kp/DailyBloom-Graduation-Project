package com.example.dailybloom

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailybloom.ui.theme.ButtonDisabled
import com.example.dailybloom.ui.theme.ChipUnselected
import com.example.dailybloom.ui.theme.ForegroundDark
import com.example.dailybloom.ui.theme.Green
import com.example.dailybloom.ui.theme.InputBorder
import com.example.dailybloom.ui.theme.MutedForeground
import com.example.dailybloom.ui.theme.MutedSecondary

private val editDayOptions = listOf(
    0 to "일", 1 to "월", 2 to "화", 3 to "수", 4 to "목", 5 to "금", 6 to "토"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditHabitModal(
    habit: Habit,
    onDismiss: () -> Unit,
    appViewModel: AppViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var name by remember { mutableStateOf(habit.name) }
    var description by remember { mutableStateOf(habit.description ?: "") }
    var selectedDays by remember { mutableStateOf(habit.schedule.toSet()) }
    var selectedCategory by remember { mutableStateOf(normalizedHabitCategory(habit.category)) }
    var selectedIconKey by remember { mutableStateOf(normalizedHabitIcon(habit.iconKey)) }
    var customImageUri by remember { mutableStateOf(habit.customImageUri) }
    var requiresPhoto by remember { mutableStateOf(habit.requiresPhoto) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            customImageUri = it.toString()
        }
    }

    val isValid = name.isNotBlank() && selectedDays.isNotEmpty()

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "습관 수정", fontSize = 20.sp, color = ForegroundDark)
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

            Spacer(Modifier.height(24.dp))

            Text(
                text = "습관 이름",
                fontSize = 14.sp,
                color = ForegroundDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = {
                    Text(
                        text = "예: 영어단어 10개 외우기",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MutedSecondary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = InputBorder,
                    focusedBorderColor = Green,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                singleLine = true
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "설명",
                fontSize = 14.sp,
                color = ForegroundDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = {
                    Text(
                        text = "간단한 설명을 입력해 주세요",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MutedSecondary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = InputBorder,
                    focusedBorderColor = Green,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                maxLines = 4
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "반복 요일",
                fontSize = 14.sp,
                color = ForegroundDark,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                editDayOptions.forEach { (index, label) ->
                    val selected = index in selectedDays
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Green else ChipUnselected)
                            .clickable {
                                selectedDays = if (selected)
                                    selectedDays - index
                                else
                                    selectedDays + index
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = if (selected) Color.White else ForegroundDark
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            HabitCategorySelector(
                selectedCategory = selectedCategory,
                onSelect = {
                    selectedCategory = it
                    selectedIconKey = defaultIconForCategory(it)
                    customImageUri = null
                }
            )

            Spacer(Modifier.height(20.dp))

            HabitIconSelector(
                selectedIconKey = selectedIconKey,
                customImageUri = customImageUri,
                onSelect = {
                    selectedIconKey = it
                    customImageUri = null
                }
            )

            Spacer(Modifier.height(20.dp))

            CustomImageUploadField(
                customImageUri = customImageUri,
                onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                onClearImage = { customImageUri = null }
            )

            Spacer(Modifier.height(20.dp))

            PhotoRequirementToggle(
                requiresPhoto = requiresPhoto,
                onChange = { requiresPhoto = it }
            )

            Spacer(Modifier.height(20.dp))

            HabitPreviewCard(
                name = name,
                description = description,
                category = selectedCategory,
                iconKey = selectedIconKey,
                customImageUri = customImageUri,
                requiresPhoto = requiresPhoto
            )

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isValid) Green else ButtonDisabled)
                    .clickable(enabled = isValid) {
                        appViewModel.editHabit(
                            id = habit.id,
                            name = name.trim(),
                            description = description.trim(),
                            schedule = selectedDays.sorted(),
                            category = selectedCategory,
                            iconKey = selectedIconKey,
                            customImageUri = customImageUri,
                            requiresPhoto = requiresPhoto
                        )
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "수정하기",
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}
