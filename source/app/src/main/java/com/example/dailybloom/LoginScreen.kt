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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailybloom.ui.theme.*

data class LoginUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@Composable
fun LoginScreen(
    onNavigateToMain: () -> Unit,
    onNavigateToSignup: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    appViewModel: AppViewModel
) {
    val userState by appViewModel.user.collectAsState()
    val uiState by appViewModel.loginUiState.collectAsState()
    val signupUiState by appViewModel.signupUiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isSubmittable = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userState) {
        if (userState != null) onNavigateToMain()
    }

    LaunchedEffect(signupUiState.createdUser) {
        val user = signupUiState.createdUser ?: return@LaunchedEffect
        snackbarHostState.showSnackbar("${user.username}님, 회원가입에 성공했어요!")
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
            Spacer(Modifier.height(72.dp))

            // Logo
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(GreenLight, Green))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Eco,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "DailyBloom",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = ForegroundDark
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "매일 조금씩, 싹을 틔우는 시간",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MutedForeground
            )

            Spacer(Modifier.height(40.dp))

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
                    AuthTextField(
                        value = email,
                        onValueChange = { email = it; appViewModel.setLoginUiState() },
                        label = "이메일",
                        placeholder = "이메일을 입력하세요",
                        isError = uiState.errorMessage != null,
                        enabled = !uiState.isLoading,
                        keyboardType = KeyboardType.Email
                    )

                    Spacer(Modifier.height(16.dp))

                    AuthPasswordField(
                        value = password,
                        onValueChange = { password = it; appViewModel.setLoginUiState() },
                        label = "비밀번호",
                        placeholder = "비밀번호를 입력하세요",
                        isError = uiState.errorMessage != null,
                        enabled = !uiState.isLoading
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "비밀번호를 잊으셨나요?",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (!uiState.isLoading) Green else MutedForeground,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .then(
                                    if (!uiState.isLoading)
                                        Modifier.clickable { onNavigateToForgotPassword() }
                                    else Modifier
                                )
                        )
                    }

                    AnimatedVisibility(visible = uiState.errorMessage != null) {
                        uiState.errorMessage?.let {
                            AuthErrorBox(message = it)
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    AuthPrimaryButton(
                        label = "로그인",
                        enabled = isSubmittable,
                        isLoading = uiState.isLoading,
                        onClick = { appViewModel.login(email, password) }
                    )

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Green.copy(alpha = 0.12f))
                    Spacer(Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "계정이 없으신가요?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = MutedForeground
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "회원가입",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (!uiState.isLoading) Green else MutedForeground,
                            modifier = if (!uiState.isLoading)
                                Modifier.clickable { appViewModel.setSignupUiState(); onNavigateToSignup() }
                            else Modifier
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Green,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        )
    }
}

// ── Shared auth field components ──────────────────────────────────────────────

@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            Text(
                text = placeholder,
                fontWeight = FontWeight.Normal,
                color = MutedSecondary
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        isError = isError,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = InputBorder,
            focusedBorderColor = Green,
            errorBorderColor = ErrorRed,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            errorContainerColor = Color.White,
            unfocusedLabelColor = MutedForeground,
            focusedLabelColor = Green,
            errorLabelColor = ErrorRed,
            cursorColor = Green
        )
    )
}

@Composable
internal fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    isError: Boolean,
    enabled: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            Text(
                text = placeholder,
                fontWeight = FontWeight.Normal,
                color = MutedSecondary
            )
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = null,
                    tint = MutedForeground,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        isError = isError,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = InputBorder,
            focusedBorderColor = Green,
            errorBorderColor = ErrorRed,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White,
            errorContainerColor = Color.White,
            unfocusedLabelColor = MutedForeground,
            focusedLabelColor = Green,
            errorLabelColor = ErrorRed,
            cursorColor = Green
        )
    )
}

@Composable
internal fun AuthErrorBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ErrorRed.copy(alpha = 0.08f))
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = ErrorRed,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
internal fun AuthPrimaryButton(
    label: String,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Green else ButtonDisabled)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    com.example.dailybloom.ui.theme.DailyBloomTheme {
        LoginScreen({}, {}, {}, viewModel())
    }
}
