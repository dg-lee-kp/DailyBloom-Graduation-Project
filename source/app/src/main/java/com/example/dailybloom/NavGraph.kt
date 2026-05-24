package com.example.dailybloom

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class Screen(val route: String) {
    object LoginScreen          : Screen("login")
    object SignupScreen         : Screen("signup")
    object ForgotPasswordScreen : Screen("forgot_password")
    object OnboardingScreen     : Screen("onboarding")
    object BloomAIChatScreen    : Screen("aichat")
    object BloomCalendarScreen  : Screen("calendar")
    object BloomMainScreen      : Screen("main")
    object BloomChecklistScreen : Screen("checklist")
    object BloomReportScreen    : Screen("report")
}

data class BottomNavActions(
    val onNavigateToMain: () -> Unit,
    val onNavigateToChecklist: () -> Unit,
    val onNavigateToCalendar: () -> Unit,
    val onNavigateToChat: () -> Unit,
    val onNavigateToReport: () -> Unit
)

@Composable
fun AppNavigation(appViewModel: AppViewModel) {
    val navController = rememberNavController()

    fun navigateToLogin() {
        appViewModel.logout()
        navController.navigate(Screen.LoginScreen.route) {
            popUpTo(0) { inclusive = true }
        }
    }

    NavHost(
        modifier = Modifier,
        navController = navController,
        startDestination = Screen.LoginScreen.route
    ) {
        val navActions = BottomNavActions(
            onNavigateToChecklist = { navController.navigate(Screen.BloomChecklistScreen.route) },
            onNavigateToCalendar  = { navController.navigate(Screen.BloomCalendarScreen.route) },
            onNavigateToMain      = { navController.navigate(Screen.BloomMainScreen.route) },
            onNavigateToChat      = { navController.navigate(Screen.BloomAIChatScreen.route) },
            onNavigateToReport    = { navController.navigate(Screen.BloomReportScreen.route) }
        )

        composable(Screen.LoginScreen.route) {
            LoginScreen(
                onNavigateToMain           = {
                    val route = if (appViewModel.user.value?.isFirstLogin == true)
                        Screen.OnboardingScreen.route
                    else
                        Screen.BloomMainScreen.route
                    navController.navigate(route) {
                        popUpTo(Screen.LoginScreen.route) { inclusive = true }
                    }
                },
                onNavigateToSignup         = { navController.navigate(Screen.SignupScreen.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPasswordScreen.route) },
                appViewModel               = appViewModel
            )
        }
        composable(Screen.SignupScreen.route) {
            SignupScreen(
                onNavigateToLogin = { navController.popBackStack() },
                appViewModel      = appViewModel
            )
        }
        composable(Screen.ForgotPasswordScreen.route) {
            ForgotPasswordScreen(
                onNavigateToLogin = { navController.popBackStack() }
            )
        }
        composable(Screen.OnboardingScreen.route) {
            OnboardingScreen(
                appViewModel = appViewModel,
                onComplete = {
                    navController.navigate(Screen.BloomMainScreen.route) {
                        popUpTo(Screen.OnboardingScreen.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.BloomAIChatScreen.route) {
            AIChatScreen(
                currentRoute = Screen.BloomAIChatScreen.route,
                navActions   = navActions,
                appViewModel = appViewModel
            )
        }
        composable(Screen.BloomCalendarScreen.route) {
            CalendarScreen(
                currentRoute = Screen.BloomCalendarScreen.route,
                navActions   = navActions,
                appViewModel = appViewModel
            )
        }
        composable(Screen.BloomMainScreen.route) {
            MainScreen(
                currentRoute = Screen.BloomMainScreen.route,
                navActions   = navActions,
                onLogout     = ::navigateToLogin,
                appViewModel = appViewModel
            )
        }
        composable(Screen.BloomChecklistScreen.route) {
            ChecklistScreen(
                currentRoute = Screen.BloomChecklistScreen.route,
                navActions   = navActions,
                appViewModel = appViewModel
            )
        }
        composable(Screen.BloomReportScreen.route) {
            ReportScreen(
                currentRoute = Screen.BloomReportScreen.route,
                navActions   = navActions,
                appViewModel = appViewModel
            )
        }
    }
}
