package com.example.dailybloom.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary green palette ─────────────────────────────────────────────────────
val Green                 = Color(0xFF6BAF8D)   // primary / active
val GreenDark             = Color(0xFF5DA581)   // pressed state, gradient end
val GreenLight            = Color(0xFFA8D5BA)   // accent, gradient
val GreenChart            = Color(0xFF81C784)   // chart bars / stat icon bg
val GreenDisabled         = Color(0xFFB0CFC0)   // disabled send button

// ── Backgrounds ───────────────────────────────────────────────────────────────
val BackgroundLight       = Color(0xFFFDFCF9)   // app background
val GradientStart         = Color(0xFFF0F9F4)   // screen gradient top (most screens)
val GrowthGradientStart   = Color(0xFFE8F5ED)   // growth screen gradient top
val TreeCardGradientStart = Color(0xFFDFF4E8)   // tree card gradient top
val TreeCardGradientEnd   = Color(0xFFF7FAF7)   // tree card gradient bottom

// ── Surfaces ──────────────────────────────────────────────────────────────────
val SurfaceLight          = Color(0xFFF5F3EE)   // secondary surface, input bg
val SurfaceGreenTint      = Color(0xFFF7FBF8)   // light green-tinted tile bg
val SurfaceMuted          = Color(0xFFE8E6E0)   // avatar bg, warm muted surface

// ── Text ──────────────────────────────────────────────────────────────────────
val ForegroundDark        = Color(0xFF2D3436)   // primary text
val MutedForeground       = Color(0xFF6B7280)   // secondary / label text
val MutedSecondary        = Color(0xFF9E9E9E)   // inactive nav, timestamps, chevrons

// ── Progress & interaction ────────────────────────────────────────────────────
val ProgressTrack         = Color(0xFFEAF4ED)   // progress bar track
val CheckCircleInactive   = Color(0xFFC8D7CC)   // unchecked habit circle border

// ── Chips & selectors ─────────────────────────────────────────────────────────
val ChipBackground        = Color(0xFFF5F8F5)   // schedule day chips (display)
val ChipUnselected        = Color(0xFFF4F8F4)   // day selector buttons in modals
val ChipInactive          = Color(0xFFF5F6F7)   // "미완료" status chip

// ── Borders ───────────────────────────────────────────────────────────────────
val BorderGreenMuted      = Color(0xFFE3EEE5)   // settings button border
val InputBorder           = Color(0xFFDDE9DF)   // text field outline
val Divider               = Color(0xFFEEF3EF)   // horizontal divider

// ── Destructive ───────────────────────────────────────────────────────────────
val DeleteBackground      = Color(0xFFFFF1F1)   // delete button background
val DeleteForeground      = Color(0xFFC55A5A)   // delete icon / text
val ErrorRed              = Color(0xFFEF4444)   // form error / confirm delete dialog
val ErrorRedDark          = Color(0xFF7F1D1D)   // dark-mode error

// ── Form ──────────────────────────────────────────────────────────────────────
val ButtonDisabled        = Color(0xFFD1D5DB)   // disabled primary button

// ── Charts ────────────────────────────────────────────────────────────────────
val ChartGrid             = Color(0xFFE8F0EA)   // chart grid lines

// ── Calendar heat-map ─────────────────────────────────────────────────────────
val CalendarMedium        = Color(0xFFD8EDD9)   // 50–74 % completion
val CalendarLight         = Color(0xFFEEF7EF)   // 1–49 % completion

// ── Report ────────────────────────────────────────────────────────────────────
val MissedBackground      = Color(0xFFFFF9F4)   // missed-habits row background

// ── Dark mode ─────────────────────────────────────────────────────────────────
val BackgroundDark        = Color(0xFF252525)
val SurfaceDark           = Color(0xFF3D3D3D)
val MutedForegroundDark   = Color(0xFFB5B5B5)

// kept for legacy compatibility (unused in screens post-refactor)
val GreenLighter          = Color(0xFFC8E6C9)
val GreenLightest         = Color(0xFFE8F5E9)
