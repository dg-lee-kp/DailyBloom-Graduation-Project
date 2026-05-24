package com.example.dailybloom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.*
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailybloom.ui.theme.*

private data class GrowthRequirement(
    val requiredTotalCompletions: Int,
    val requiredPerfectDays: Int
)

private fun growthRequirementForStage(stage: Int): GrowthRequirement? = when (stage) {
    1 -> GrowthRequirement(10, 3)
    2 -> GrowthRequirement(25, 7)
    3 -> GrowthRequirement(50, 15)
    4 -> GrowthRequirement(80, 25)
    5 -> GrowthRequirement(120, 35)
    6 -> GrowthRequirement(180, 50)
    7 -> GrowthRequirement(250, 70)
    else -> null
}

private fun stageLabelFor(stage: Int): String = when (stage) {
    1 -> "씨앗"
    2 -> "발아"
    3 -> "새싹"
    4 -> "묘목"
    5 -> "어린 나무"
    6 -> "성장 중인 나무"
    7 -> "큰 나무"
    8 -> "완성된 나무"
    else -> "씨앗"
}

private fun computeGrowthStage(totalCompleted: Int, perfectDays: Int): Int {
    for (stage in 7 downTo 1) {
        val req = growthRequirementForStage(stage)
        if (req != null &&
            totalCompleted >= req.requiredTotalCompletions &&
            perfectDays    >= req.requiredPerfectDays) {
            return stage + 1
        }
    }
    return 1
}

private fun treeDrawableFor(stage: Int): Int = when (stage) {
    1 -> R.drawable.tree_stage1
    2 -> R.drawable.tree_stage2
    3 -> R.drawable.tree_stage3
    4 -> R.drawable.tree_stage4
    5 -> R.drawable.tree_stage5
    6 -> R.drawable.tree_stage6
    7 -> R.drawable.tree_stage7
    8 -> R.drawable.tree_stage8
    else -> R.drawable.tree_stage1
}

@Composable
fun MainScreen(
    currentRoute: String,
    navActions: BottomNavActions,
    onLogout: () -> Unit,
    appViewModel: AppViewModel
) {
    val growthData by appViewModel.growthData.collectAsState()

    LaunchedEffect(Unit) {
        appViewModel.fetchGrowthData()
    }

    val totalCompleted = growthData?.totalCompleted ?: 0
    val perfectDays    = growthData?.perfectDays    ?: 0
    val streakDays     = growthData?.streakDays     ?: 0
    val currentStage   = computeGrowthStage(totalCompleted, perfectDays)

    val requirement = growthRequirementForStage(currentStage)
    val totalProgress = if (requirement == null || requirement.requiredTotalCompletions == 0) 1f
        else (totalCompleted.toFloat() / requirement.requiredTotalCompletions).coerceIn(0f, 1f)
    val perfectDayProgress = if (requirement == null || requirement.requiredPerfectDays == 0) 1f
        else (perfectDays.toFloat() / requirement.requiredPerfectDays).coerceIn(0f, 1f)

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                navActions = navActions
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GrowthGradientStart, BackgroundLight)
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
                GrowthHeader(onLogout = onLogout)
                Spacer(Modifier.height(24.dp))
                TreeStageCard(stage = currentStage)
                Spacer(Modifier.height(24.dp))
                GrowthStatsRow(
                    totalCompleted = totalCompleted,
                    perfectDays = perfectDays,
                    streakDays = streakDays
                )
                if (requirement != null) {
                    Spacer(Modifier.height(24.dp))
                    NextStageCard(
                        requirement = requirement,
                        totalCompleted = totalCompleted,
                        perfectDays = perfectDays,
                        totalProgress = totalProgress,
                        perfectDayProgress = perfectDayProgress
                    )
                }
                Spacer(Modifier.height(24.dp))
                GrowthGuideCard(stage = currentStage)
            }
        }
    }
}

@Composable
private fun GrowthHeader(onLogout: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "나의 성장", fontSize = 24.sp, color = ForegroundDark)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "작은 실천이 쌓여 씨앗이 나무가 되어가요",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )
        }
        Spacer(Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, BorderGreenMuted, RoundedCornerShape(16.dp))
                .clickable { showConfirm = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ExitToApp,
                contentDescription = "로그아웃",
                tint = MutedForeground,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White,
            title = {
                Text(text = "로그아웃", fontSize = 18.sp, color = ForegroundDark)
            },
            text = {
                Text(
                    text = "정말 로그아웃 할까요?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Green)
                        .clickable { showConfirm = false; onLogout() }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(text = "로그아웃", fontSize = 14.sp, color = Color.White)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceLight)
                        .clickable { showConfirm = false }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(text = "취소", fontSize = 14.sp, color = ForegroundDark)
                }
            }
        )
    }
}

@Composable
private fun TreeStageCard(stage: Int) {
    val stageLabel = stageLabelFor(stage)
    val overallProgress = stage.toFloat() / 8f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(TreeCardGradientStart, TreeCardGradientEnd)
                )
            )
    ) {
        Image(
            painter = painterResource(id = treeDrawableFor(stage)),
            contentDescription = stageLabel,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.BottomCenter
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x8C000000))
                    )
                )
        )

        // "성장 기록" badge – top right
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "성장 기록",
                fontSize = 14.sp,
                color = Color.White
            )
        }

        // Stage info and progress – bottom overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "현재 단계",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.9f)
            )
            Text(
                text = "${stage}단계 · $stageLabel",
                fontSize = 28.sp,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            // Progress container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "전체 성장 진행도",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    )
                    Text(
                        text = "$stage/8 단계",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(overallProgress)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun GrowthStatsRow(
    totalCompleted: Int,
    perfectDays: Int,
    streakDays: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GrowthStatTile(
            icon = Icons.Outlined.WaterDrop,
            label = "누적 완료",
            value = "${totalCompleted}회",
            iconBg = Green.copy(alpha = 0.1f),
            modifier = Modifier.weight(1f)
        )
        GrowthStatTile(
            icon = Icons.Outlined.CalendarMonth,
            label = "100% 달성일",
            value = "${perfectDays}일",
            iconBg = GreenLight.copy(alpha = 0.2f),
            modifier = Modifier.weight(1f)
        )
        GrowthStatTile(
            icon = Icons.Outlined.WbSunny,
            label = "연속 일수",
            value = "${streakDays}일",
            iconBg = GreenChart.copy(alpha = 0.2f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GrowthStatTile(
    icon: ImageVector,
    label: String,
    value: String,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                color = ForegroundDark
            )
        }
    }
}

@Composable
private fun NextStageCard(
    requirement: GrowthRequirement,
    totalCompleted: Int,
    perfectDays: Int,
    totalProgress: Float,
    perfectDayProgress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "다음 단계 조건",
                fontSize = 16.sp,
                color = ForegroundDark
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "누적 수행 횟수",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
                Text(
                    text = "$totalCompleted / ${requirement.requiredTotalCompletions}",
                    fontSize = 13.sp,
                    color = ForegroundDark
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(totalProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Green)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "100% 달성일",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
                Text(
                    text = "$perfectDays / ${requirement.requiredPerfectDays}",
                    fontSize = 13.sp,
                    color = ForegroundDark
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(perfectDayProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(GreenLight)
                )
            }
        }
    }
}

@Composable
private fun GrowthGuideCard(stage: Int) {
    val tipText = if (stage < 8) {
        "매일 습관을 완료하면 나무가 자라요. 누적 수행 횟수와 100% 달성일 조건을 모두 채우면 다음 단계로 성장해요."
    } else {
        "축하해요! 최고 단계에 도달했어요. 지금처럼 꾸준히 습관을 이어가세요."
    }

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
                text = "💡 성장 안내",
                fontSize = 16.sp,
                color = ForegroundDark
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = tipText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground,
                lineHeight = 22.sp
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    DailyBloomTheme { MainScreen("", BottomNavActions({}, {}, {}, {}, {}), {}, viewModel()) }
}
