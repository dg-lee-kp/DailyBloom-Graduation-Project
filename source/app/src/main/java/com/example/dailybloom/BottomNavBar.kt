package com.example.dailybloom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailybloom.ui.theme.*

@Composable
private fun BottomNavItem(
    imageVector: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = Dimens.Small)
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = if (isSelected) Green else MutedSecondary,
            modifier = Modifier.size(Dimens.IconSizeSmall)
        )
        Spacer(Modifier.height(Dimens.ExtraSmall))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Green else MutedSecondary
        )
    }
}

@Composable
fun BottomNavBar(currentRoute: String?, navActions: BottomNavActions) {
    Surface(
        shadowElevation = Dimens.Small,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 20.dp, bottom = Dimens.Small, start = Dimens.Small, end = Dimens.Small),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            BottomNavItem(
                imageVector = Icons.AutoMirrored.Outlined.Chat,
                label = "AI 채팅",
                isSelected = currentRoute == Screen.BloomAIChatScreen.route,
                onClick = { navActions.onNavigateToChat() }
            )
            BottomNavItem(
                imageVector = Icons.Outlined.CalendarMonth,
                label = "캘린더",
                isSelected = currentRoute == Screen.BloomCalendarScreen.route,
                onClick = { navActions.onNavigateToCalendar() }
            )

            // Center raised growth button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = Dimens.Small)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(GreenLight, Green)
                            )
                        )
                        .clickable { navActions.onNavigateToMain() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = "성장",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(Dimens.ExtraSmall))
                Text(
                    text = "성장",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Green
                )
            }

            BottomNavItem(
                imageVector = Icons.AutoMirrored.Outlined.FormatListBulleted,
                label = "오늘",
                isSelected = currentRoute == Screen.BloomChecklistScreen.route,
                onClick = { navActions.onNavigateToChecklist() }
            )
            BottomNavItem(
                imageVector = Icons.Outlined.BarChart,
                label = "리포트",
                isSelected = currentRoute == Screen.BloomReportScreen.route,
                onClick = { navActions.onNavigateToReport() }
            )
        }
    }
}
