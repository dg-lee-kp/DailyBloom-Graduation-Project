package com.example.dailybloom

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dailybloom.ui.theme.*
import java.util.Calendar

@Composable
fun ChecklistScreen(
    currentRoute: String,
    navActions: BottomNavActions,
    appViewModel: AppViewModel
) {
    val habits by appViewModel.habits.collectAsState()
    val entries by appViewModel.entries.collectAsState()
    val context = LocalContext.current
    var pendingPhotoProof by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var selectedDetail by remember { mutableStateOf<Pair<Habit, ChecklistEntry>?>(null) }
    val checkedStates = remember(entries) {
        mutableStateMapOf<Int, Boolean>().apply {
            entries?.forEach { put(it.habitId, it.checked) }
        }
    }
    val todayWeekday = remember {
        Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    }
    val todayItems = remember(entries, habits, todayWeekday) {
        entries.orEmpty().mapNotNull { entry ->
            val habit = habits.orEmpty().firstOrNull { it.id == entry.habitId }
            if (habit != null && todayWeekday in habit.schedule) {
                habit to entry
            } else {
                null
            }
        }
    }

    val photoProofLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val pending = pendingPhotoProof
        if (success && pending != null) {
            val (entryId, habitId) = pending
            checkedStates[habitId] = true
            appViewModel.toggleEntry(entryId, true, cameraUri?.toString())
        }
        pendingPhotoProof = null
        cameraUri = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val pending = pendingPhotoProof
        if (uri != null && pending != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val (entryId, habitId) = pending
            checkedStates[habitId] = true
            appViewModel.toggleEntry(entryId, true, uri.toString())
        }
        pendingPhotoProof = null
    }

    fun launchCamera(entryId: Int, habitId: Int) {
        val photoFile = File(context.cacheDir, "photo_proof").also { it.mkdirs() }
            .let { File(it, "proof_${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
        cameraUri = uri
        pendingPhotoProof = entryId to habitId
        photoProofLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val pending = pendingPhotoProof ?: return@rememberLauncherForActivityResult
            launchCamera(pending.first, pending.second)
        } else {
            pendingPhotoProof = null
        }
    }

    if (showPhotoSourceDialog) {
        val pending = pendingPhotoProof
        AlertDialog(
            onDismissRequest = {
                showPhotoSourceDialog = false
                pendingPhotoProof = null
            },
            title = { Text("사진 선택") },
            text = { Text("사진을 어떻게 첨부할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    if (pending != null) {
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPermission) {
                            launchCamera(pending.first, pending.second)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }) { Text("카메라 촬영") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPhotoSourceDialog = false
                    galleryLauncher.launch(arrayOf("image/*"))
                }) { Text("갤러리에서 선택") }
            }
        )
    }

    val totalCount = todayItems.size
    val checkedCount = todayItems.count { (habit, entry) ->
        checkedStates[habit.id] ?: entry.checked
    }
    val completionRate = if (totalCount == 0) 0 else (checkedCount * 100 / totalCount)
    val allHabitsComplete = totalCount > 0 && checkedCount == totalCount
    var initializedCompletionState by remember { mutableStateOf(false) }
    var wasAllHabitsComplete by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(allHabitsComplete, totalCount) {
        if (totalCount == 0) {
            initializedCompletionState = false
            wasAllHabitsComplete = false
            return@LaunchedEffect
        }
        if (!initializedCompletionState) {
            initializedCompletionState = true
            wasAllHabitsComplete = allHabitsComplete
        } else {
            if (allHabitsComplete && !wasAllHabitsComplete) {
                showCompletionDialog = true
            }
            wasAllHabitsComplete = allHabitsComplete
        }
    }

    var showAddModal by remember { mutableStateOf(false) }
    var showManageModal by remember { mutableStateOf(false) }

    if (showAddModal) {
        AddHabitModal(
            onDismiss = { showAddModal = false },
            appViewModel = appViewModel
        )
    }

    if (showManageModal) {
        ManageHabitsModal(
            onDismiss = { showManageModal = false },
            appViewModel = appViewModel
        )
    }

    if (showCompletionDialog) {
        TodayCompletionDialog(
            onDismiss = { showCompletionDialog = false }
        )
    }

    selectedDetail?.let { (habit, entry) ->
        HabitDetailDialog(
            habit = habit,
            entry = entry,
            onDismiss = { selectedDetail = null }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddModal = true },
                containerColor = Green,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "습관 추가",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
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
                        colors = listOf(GradientStart, BackgroundLight)
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
                TodayHeader(onSettingsClick = { showManageModal = true })
                Spacer(Modifier.height(24.dp))
                TodayProgressCard(
                    checkedCount = checkedCount,
                    totalCount = totalCount,
                    completionRate = completionRate
                )
                Spacer(Modifier.height(24.dp))
                todayItems.forEach { (habit, entry) ->
                    val isChecked = checkedStates[entry.habitId] ?: entry.checked
                    TodayHabitItem(
                        habit = habit,
                        entry = entry,
                        isChecked = isChecked,
                        onToggle = {
                            if (!isChecked && habit.requiresPhoto) {
                                pendingPhotoProof = entry.id to habit.id
                                showPhotoSourceDialog = true
                            } else {
                                checkedStates[habit.id] = !isChecked
                                appViewModel.toggleEntry(entry.id, !isChecked)
                            }
                        },
                        onShowDetails = {
                            selectedDetail = habit to entry.copy(
                                checked = isChecked,
                                photoProofUri = entry.photoProofUri
                            )
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TodayCompletionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Green),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("좋아요")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("오늘의 습관 완료")
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(Green, GreenLight, CalendarMedium, Green.copy(alpha = 0.55f)).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "오늘의 습관을 전부 완료했어요!",
                    fontSize = 16.sp,
                    color = ForegroundDark
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "좋은 흐름이에요. 이 작은 완료들이 차곡차곡 자라날 거예요.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MutedForeground
                )
            }
        }
    )
}

@Composable
private fun TodayHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "오늘의 습관",
                fontSize = 24.sp,
                color = ForegroundDark
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "오늘 완료한 내용은 캘린더, 성장, 리포트 탭에 바로 반영돼요",
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
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "습관 관리",
                tint = MutedForeground,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TodayProgressCard(
    checkedCount: Int,
    totalCount: Int,
    completionRate: Int
) {
    val progress = if (totalCount == 0) 0f else checkedCount.toFloat() / totalCount

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "오늘 진행도",
                        fontSize = 14.sp,
                        color = MutedForeground
                    )
                    Text(
                        text = "$checkedCount/$totalCount",
                        fontSize = 30.sp,
                        color = ForegroundDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "완료율",
                        fontSize = 14.sp,
                        color = MutedForeground
                    )
                    Text(
                        text = "$completionRate%",
                        fontSize = 24.sp,
                        color = ForegroundDark
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Green)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatTile(
                    label = "누적 완료",
                    value = "${checkedCount}회",
                    modifier = Modifier.weight(1f)
                )
                MiniStatTile(
                    label = "100% 달성일",
                    value = "0일",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGreenTint)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                color = MutedForeground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                color = ForegroundDark
            )
        }
    }
}

@Composable
private fun TodayHabitItem(
    habit: Habit,
    entry: ChecklistEntry,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onShowDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetails() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Green.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HabitIconBadge(
                iconKey = habit.iconKey,
                customImageUri = habit.customImageUri,
                size = 54.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = habit.name,
                        fontSize = 16.sp,
                        color = ForegroundDark,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isChecked) Green else Color.Transparent)
                            .border(
                                width = 2.dp,
                                color = if (isChecked) Green else CheckCircleInactive,
                                shape = CircleShape
                            )
                            .clickable { onToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChecked) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                if (!habit.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = habit.description,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = MutedForeground
                    )
                }
                Spacer(Modifier.height(10.dp))
                HabitMetaChips(
                    category = habit.category,
                    requiresPhoto = habit.requiresPhoto
                )
                if (isChecked && habit.requiresPhoto && !entry.photoProofUri.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    PhotoProofThumbnail(photoProofUri = entry.photoProofUri)
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isChecked) ProgressTrack else Green
                        )
                        .clickable { onToggle() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isChecked) "완료 취소" else if (habit.requiresPhoto) "사진 인증" else "완료하기",
                        fontSize = 14.sp,
                        color = if (isChecked) Green else Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitDetailDialog(
    habit: Habit,
    entry: ChecklistEntry,
    onDismiss: () -> Unit
) {
    val checkedText = if (entry.checked) "완료됨" else "아직 미완료"
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        title = { Text(habit.name) },
        text = {
            Column {
                if (!habit.description.isNullOrBlank()) {
                    Text(
                        text = habit.description,
                        fontSize = 14.sp,
                        color = ForegroundDark,
                        fontWeight = FontWeight.Normal
                    )
                    Spacer(Modifier.height(12.dp))
                }
                HabitMetaChips(
                    category = habit.category,
                    requiresPhoto = habit.requiresPhoto
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = checkedText,
                    fontSize = 13.sp,
                    color = MutedForeground,
                    fontWeight = FontWeight.Normal
                )
                if (entry.checkedAt?.isNotBlank() == true && entry.checkedAt != "null") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.checkedAt,
                        fontSize = 12.sp,
                        color = MutedForeground,
                        fontWeight = FontWeight.Normal
                    )
                }
                if (habit.requiresPhoto) {
                    Spacer(Modifier.height(14.dp))
                    PhotoProofPreview(photoProofUri = entry.photoProofUri)
                }
            }
        }
    )
}

@Composable
private fun PhotoProofThumbnail(photoProofUri: String) {
    val context = LocalContext.current
    val imageBitmap = remember(photoProofUri) { loadImageBitmapFromUri(context, photoProofUri) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGreenTint)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "인증 사진",
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "인증 사진 보기",
            fontSize = 13.sp,
            color = Green,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun PhotoProofPreview(photoProofUri: String?) {
    val context = LocalContext.current
    val imageBitmap = remember(photoProofUri) { loadImageBitmapFromUri(context, photoProofUri) }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "인증 사진",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ProgressTrack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (photoProofUri.isNullOrBlank()) "저장된 인증 사진이 없어요" else "사진을 불러올 수 없어요",
                fontSize = 13.sp,
                color = MutedForeground,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

private fun loadImageBitmapFromUri(context: android.content.Context, uriString: String?): ImageBitmap? {
    if (uriString.isNullOrBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ChecklistScreenPreview() {
    DailyBloomTheme { ChecklistScreen("", BottomNavActions({}, {}, {}, {}, {}), viewModel()) }
}
