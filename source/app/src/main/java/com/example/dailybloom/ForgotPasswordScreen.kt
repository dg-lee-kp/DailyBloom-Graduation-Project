package com.example.dailybloom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dailybloom.ui.theme.*

@Composable
fun ForgotPasswordScreen(onNavigateToLogin: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    val isSubmittable = email.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GradientStart, BackgroundLight)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f))
                        .clickable { onNavigateToLogin() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "뒤로",
                        tint = ForegroundDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ProgressTrack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "비밀번호 재설정",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = ForegroundDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "가입할 때 사용한 이메일을 입력해주세요",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )

            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(
                visible = !submitted,
                enter = fadeIn()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Green.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        AuthTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "이메일",
                            placeholder = "이메일을 입력하세요",
                            isError = false,
                            enabled = true,
                            keyboardType = KeyboardType.Email
                        )

                        Spacer(Modifier.height(24.dp))

                        AuthPrimaryButton(
                            label = "재설정 링크 보내기",
                            enabled = isSubmittable,
                            isLoading = false,
                            onClick = { submitted = true }
                        )

                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = Green.copy(alpha = 0.12f))
                        Spacer(Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "기억이 나셨나요?",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = MutedForeground
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "로그인",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Green,
                                modifier = Modifier.clickable { onNavigateToLogin() }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = submitted,
                enter = fadeIn() + slideInVertically { it / 4 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    border = BorderStroke(1.dp, Green.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(ProgressTrack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = Green,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        Text(
                            text = "이메일을 확인해주세요",
                            fontSize = 18.sp,
                            color = ForegroundDark,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "등록된 이메일이 있다면 $email\n으로 재설정 안내를 발송했어요.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MutedForeground,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(Modifier.height(28.dp))

                        AuthPrimaryButton(
                            label = "로그인으로 돌아가기",
                            enabled = true,
                            isLoading = false,
                            onClick = onNavigateToLogin
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ForgotPasswordScreenPreview() {
    com.example.dailybloom.ui.theme.DailyBloomTheme {
        ForgotPasswordScreen({})
    }
}
