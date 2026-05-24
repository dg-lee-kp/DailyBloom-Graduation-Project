package com.example.dailybloom

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailybloom.ui.theme.BorderGreenMuted
import com.example.dailybloom.ui.theme.ChipBackground
import com.example.dailybloom.ui.theme.ChipUnselected
import com.example.dailybloom.ui.theme.ForegroundDark
import com.example.dailybloom.ui.theme.Green
import com.example.dailybloom.ui.theme.InputBorder
import com.example.dailybloom.ui.theme.MutedForeground
import com.example.dailybloom.ui.theme.MutedSecondary
import com.example.dailybloom.ui.theme.ProgressTrack
import com.example.dailybloom.ui.theme.SurfaceGreenTint
import com.example.dailybloom.ui.theme.SurfaceLight

data class HabitIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

val habitCategoryOptions = listOf("건강", "운동", "공부", "생활", "정리", "외출", "기타")

val habitIconOptions = listOf(
    HabitIconOption("water", "물", Icons.Outlined.WaterDrop),
    HabitIconOption("walk", "산책", Icons.Outlined.DirectionsWalk),
    HabitIconOption("sparkle", "스트레칭", Icons.Outlined.AutoAwesome),
    HabitIconOption("air", "환기", Icons.Outlined.Air),
    HabitIconOption("book", "공부", Icons.Outlined.MenuBook),
    HabitIconOption("home", "생활", Icons.Outlined.Home),
    HabitIconOption("check", "체크", Icons.Outlined.CheckCircle),
    HabitIconOption("star", "기타", Icons.Outlined.Star)
)

fun defaultIconForCategory(category: String): String = when (category) {
    "건강" -> "water"
    "운동" -> "walk"
    "공부" -> "book"
    "생활" -> "home"
    "정리" -> "check"
    "외출" -> "walk"
    else -> "star"
}

fun normalizedHabitCategory(category: String?): String =
    category?.takeIf { it in habitCategoryOptions } ?: "기타"

fun normalizedHabitIcon(iconKey: String?): String =
    iconKey?.takeIf { key -> habitIconOptions.any { it.key == key } } ?: "check"

private fun iconForKey(iconKey: String): ImageVector =
    habitIconOptions.firstOrNull { it.key == iconKey }?.icon ?: Icons.Outlined.CheckCircle

@Composable
fun HabitIconBadge(
    iconKey: String,
    customImageUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    selected: Boolean = false
) {
    val context = LocalContext.current
    val imageBitmap = remember(customImageUri) {
        loadBitmapFromUri(context, customImageUri)
    }
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (selected) Green else SurfaceGreenTint)
            .border(
                width = 1.dp,
                color = if (selected) Green else BorderGreenMuted,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(size)
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = iconForKey(normalizedHabitIcon(iconKey)),
                contentDescription = null,
                tint = if (selected) Color.White else Green,
                modifier = Modifier.size(size * 0.42f)
            )
        }
    }
}

@Composable
fun HabitCategorySelector(
    selectedCategory: String,
    onSelect: (String) -> Unit
) {
    SectionLabel("카테고리")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        habitCategoryOptions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    SelectablePill(
                        text = category,
                        selected = selectedCategory == category,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(category) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HabitIconSelector(
    selectedIconKey: String,
    customImageUri: String?,
    onSelect: (String) -> Unit
) {
    SectionLabel("대표 아이콘")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        habitIconOptions.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedIconKey == option.key && customImageUri == null) Green else ChipUnselected)
                            .clickable { onSelect(option.key) }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HabitIconBadge(
                            iconKey = option.key,
                            customImageUri = null,
                            size = 28.dp,
                            selected = selectedIconKey == option.key && customImageUri == null
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = option.label,
                            fontSize = 11.sp,
                            color = if (selectedIconKey == option.key && customImageUri == null) Color.White else ForegroundDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomImageUploadField(
    customImageUri: String?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit
) {
    SectionLabel("커스텀 대표 이미지")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, InputBorder, RoundedCornerShape(16.dp))
            .clickable { onPickImage() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = Green,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (customImageUri == null) "이미지 업로드" else "이미지 변경",
            fontSize = 14.sp,
            color = Green
        )
        if (customImageUri != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = "삭제",
                fontSize = 13.sp,
                color = MutedForeground,
                modifier = Modifier.clickable { onClearImage() }
            )
        }
    }
}

@Composable
fun PhotoRequirementToggle(
    requiresPhoto: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGreenTint)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "사진 인증 필요", fontSize = 14.sp, color = ForegroundDark)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "체크 대신 사진 업로드 후 완료 처리",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )
        }
        Switch(
            checked = requiresPhoto,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Green,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = InputBorder
            )
        )
    }
}

@Composable
fun HabitPreviewCard(
    name: String,
    description: String,
    category: String,
    iconKey: String,
    customImageUri: String?,
    requiresPhoto: Boolean
) {
    SectionLabel("미리보기")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderGreenMuted),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HabitIconBadge(
                iconKey = iconKey,
                customImageUri = customImageUri,
                size = 50.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "새 습관" },
                    fontSize = 15.sp,
                    color = ForegroundDark
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description.ifBlank { if (requiresPhoto) "사진 인증 방식" else "일반 체크 방식" },
                    fontSize = 12.sp,
                    color = MutedForeground,
                    fontWeight = FontWeight.Normal
                )
                Spacer(Modifier.height(8.dp))
                HabitMetaChips(category = category, requiresPhoto = requiresPhoto)
            }
        }
    }
}

@Composable
fun HabitMetaChips(
    category: String,
    requiresPhoto: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SmallMetaChip(text = normalizedHabitCategory(category))
        if (requiresPhoto) SmallMetaChip(text = "사진 인증")
    }
}

@Composable
private fun SmallMetaChip(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(ProgressTrack)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 11.sp, color = Green)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = ForegroundDark,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SelectablePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Green else SurfaceLight)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (selected) Color.White else ForegroundDark
        )
    }
}

private fun loadBitmapFromUri(context: Context, uriString: String?): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
}
