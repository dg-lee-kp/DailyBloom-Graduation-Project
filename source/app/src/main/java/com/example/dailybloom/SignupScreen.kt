package com.example.dailybloom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailybloom.ui.theme.*

data class SignupUiState(
    val isLoading: Boolean = false,
    val passwordMismatch: Boolean = false,
    val errorMessage: String? = null,
    val createdUser: User? = null
)

@Composable
fun SignupScreen(
    onNavigateToLogin: () -> Unit,
    appViewModel: AppViewModel
) {
    val uiState by appViewModel.signupUiState.collectAsState()
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val isSubmittable = username.isNotBlank() && email.isNotBlank() &&
        password.isNotBlank() && confirmPassword.isNotBlank() && !uiState.isLoading

    LaunchedEffect(uiState.createdUser) {
        if (uiState.createdUser != null) onNavigateToLogin()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(GradientStart, BackgroundLight))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // Logo
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
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "회원가입",
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = ForegroundDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "DailyBloom과 함께 좋은 습관을 시작해요",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )

            Spacer(Modifier.height(32.dp))

            // Form card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, Green.copy(alpha = 0.12f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isGeneralError = uiState.errorMessage != null && !uiState.passwordMismatch

                    AuthTextField(
                        value = username,
                        onValueChange = { username = it; appViewModel.setSignupUiState() },
                        label = "닉네임",
                        placeholder = "사용할 닉네임을 입력하세요",
                        isError = isGeneralError,
                        enabled = !uiState.isLoading
                    )

                    Spacer(Modifier.height(16.dp))

                    AuthTextField(
                        value = email,
                        onValueChange = { email = it; appViewModel.setSignupUiState() },
                        label = "이메일",
                        placeholder = "이메일을 입력하세요",
                        isError = isGeneralError,
                        enabled = !uiState.isLoading,
                        keyboardType = KeyboardType.Email
                    )

                    Spacer(Modifier.height(16.dp))

                    AuthPasswordField(
                        value = password,
                        onValueChange = { password = it; appViewModel.setSignupUiState() },
                        label = "비밀번호",
                        placeholder = "비밀번호를 입력하세요",
                        isError = isGeneralError || uiState.passwordMismatch,
                        enabled = !uiState.isLoading
                    )

                    Spacer(Modifier.height(16.dp))

                    AuthPasswordField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; appViewModel.setSignupUiState() },
                        label = "비밀번호 확인",
                        placeholder = "비밀번호를 다시 입력하세요",
                        isError = isGeneralError || uiState.passwordMismatch,
                        enabled = !uiState.isLoading
                    )

                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let {
                            Spacer(Modifier.height(16.dp))
                            AuthErrorBox(message = it)
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    AuthPrimaryButton(
                        label = "회원가입",
                        enabled = isSubmittable,
                        isLoading = uiState.isLoading,
                        onClick = {
                            if (password != confirmPassword)
                                appViewModel.setSignupUiState(
                                    SignupUiState(
                                        errorMessage = "비밀번호가 일치하지 않습니다.",
                                        passwordMismatch = true
                                    )
                                )
                            else
                                appViewModel.signup(email, username, password)
                        }
                    )

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Green.copy(alpha = 0.12f))
                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "이미 계정이 있으신가요?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MutedForeground
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "로그인",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (!uiState.isLoading) Green else MutedForeground,
                            modifier = if (!uiState.isLoading)
                                Modifier.clickable { onNavigateToLogin() }
                            else Modifier
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
private fun SignupScreenPreview() {
    com.example.dailybloom.ui.theme.DailyBloomTheme {
        SignupScreen({}, viewModel())
    }
}
