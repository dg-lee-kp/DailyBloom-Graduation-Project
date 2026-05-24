package com.example.dailybloom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class AppViewModel : ViewModel() {
    private val _loginUiState = MutableStateFlow(LoginUiState())
    val loginUiState = _loginUiState.asStateFlow()

    private val _signupUiState = MutableStateFlow(SignupUiState())
    val signupUiState = _signupUiState.asStateFlow()

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _habits = MutableStateFlow<List<Habit>?>(null)
    val habits: StateFlow<List<Habit>?> = _habits.asStateFlow()

    private val _entries = MutableStateFlow<List<ChecklistEntry>?>(null)
    val entries: StateFlow<List<ChecklistEntry>?> = _entries.asStateFlow()

    private val _monthCalendar = MutableStateFlow<Map<Int, DayCompletion>>(emptyMap())
    val monthCalendar: StateFlow<Map<Int, DayCompletion>> = _monthCalendar.asStateFlow()

    private val _growthData = MutableStateFlow<GrowthData?>(null)
    val growthData: StateFlow<GrowthData?> = _growthData.asStateFlow()

    fun fetchGrowthData() {
        val user = _user.value ?: return
        viewModelScope.launch {
            _growthData.value = apiGetGrowthData(user.id)
        }
    }

    private val _reportData = MutableStateFlow<ReportData?>(null)
    val reportData: StateFlow<ReportData?> = _reportData.asStateFlow()

    private val _onboardingRecommendation = MutableStateFlow<OnboardingRecommendation?>(null)
    val onboardingRecommendation: StateFlow<OnboardingRecommendation?> = _onboardingRecommendation.asStateFlow()

    private val _isOnboardingLoading = MutableStateFlow(false)
    val isOnboardingLoading: StateFlow<Boolean> = _isOnboardingLoading.asStateFlow()

    private val _onboardingError = MutableStateFlow<String?>(null)
    val onboardingError: StateFlow<String?> = _onboardingError.asStateFlow()

    fun fetchReport(year: Int, month: Int) {
        val user = _user.value ?: return
        viewModelScope.launch {
            _reportData.value = apiGetReport(user.id, year, month)
        }
    }

    fun generateOnboardingRecommendation(
        categories: List<String>,
        preferredTime: String,
        difficulty: String,
        frictions: List<String>,
        extra: String
    ) {
        if (_isOnboardingLoading.value) return
        viewModelScope.launch {
            _isOnboardingLoading.value = true
            _onboardingError.value = null
            val recommendation = apiGetOnboardingRecommendations(
                categories = categories,
                preferredTime = preferredTime,
                difficulty = difficulty,
                frictions = frictions,
                extra = extra
            )
            _onboardingRecommendation.value = recommendation
            if (recommendation == null) {
                _onboardingError.value = "추천 습관을 만들지 못했습니다. 다시 시도해 주세요."
            }
            _isOnboardingLoading.value = false
        }
    }

    fun clearOnboardingRecommendation() {
        _onboardingRecommendation.value = null
        _onboardingError.value = null
    }

    fun updateOnboardingHabit(index: Int, habit: OnboardingHabit) {
        val recommendation = _onboardingRecommendation.value ?: return
        if (index !in recommendation.habits.indices) return
        _onboardingRecommendation.value = recommendation.copy(
            habits = recommendation.habits.mapIndexed { habitIndex, current ->
                if (habitIndex == index) habit else current
            }
        )
    }

    fun completeOnboarding(onComplete: () -> Unit) {
        val user = _user.value ?: return
        val recommendation = _onboardingRecommendation.value ?: return
        if (_isOnboardingLoading.value) return

        viewModelScope.launch {
            _isOnboardingLoading.value = true
            _onboardingError.value = null
            val updatedUser = apiCompleteOnboarding(user.id, recommendation.habits)
            if (updatedUser == null) {
                _onboardingError.value = "초기 습관을 저장하지 못했습니다. 다시 시도해 주세요."
            } else {
                _user.value = updatedUser
                refreshChecklist()
                onComplete()
            }
            _isOnboardingLoading.value = false
        }
    }

    fun fetchMonthCalendar(year: Int, month: Int) {
        val user = _user.value ?: return
        viewModelScope.launch {
            val days = apiGetMonthCalendar(user.id, year, month)
            if (days != null) {
                _monthCalendar.value = days.associateBy { it.day }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginUiState.value = LoginUiState(isLoading = true)
            _user.value = apiValidateUser(email, password)

            if (_user.value == null) {
                _loginUiState.value = LoginUiState(errorMessage = "이메일 또는 비밀번호가 올바르지 않습니다.")
            } else {
                _user.value?.let {
                    val userData = apiGetChecklist(it)
                    _habits.value = userData?.first
                    _entries.value = userData?.second
                    loadChatHistory(it.id)
                }
                _loginUiState.value = LoginUiState()
            }
        }
    }

    fun refreshChecklist() {
        viewModelScope.launch {
            _user.value?.let {
                val userData = apiGetChecklist(it)
                _habits.value = userData?.first
                _entries.value = userData?.second
            }
        }
    }

    fun setLoginUiState(state: LoginUiState = LoginUiState()) {
        _loginUiState.value = state
    }

    fun signup(email: String, username: String, password: String) {
        viewModelScope.launch {
            setSignupUiState(SignupUiState(isLoading = true))
            val user = apiRequestSignup(email, username, password)
            setSignupUiState(if (user == null)
                SignupUiState(errorMessage = "회원가입에 실패했습니다.")
            else
                SignupUiState(createdUser = user))
        }
    }

    fun setSignupUiState(state: SignupUiState = SignupUiState()) {
        _signupUiState.value = state
    }

    fun logout() {
        _user.value = null
        _habits.value = null
        _entries.value = null
        _chatMessages.value = emptyList()
        _pendingChatAction.value = null
        _onboardingRecommendation.value = null
        _isOnboardingLoading.value = false
        _onboardingError.value = null
    }

    fun toggleEntry(entryId: Int, checked: Boolean, photoProofUri: String? = null) {
        val user = _user.value ?: return
        viewModelScope.launch {
            val previousEntries = _entries.value
            _entries.value = _entries.value?.map {
                if (it.id == entryId) {
                    it.copy(
                        checked = checked,
                        photoProofUri = if (checked) photoProofUri ?: it.photoProofUri else null
                    )
                } else {
                    it
                }
            }
            if (!apiToggleEntry(entryId, checked, user.id, photoProofUri)) {
                _entries.value = previousEntries
            }
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    private val _chatMessages = MutableStateFlow<List<Message>>(emptyList())
    val chatMessages: StateFlow<List<Message>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _pendingChatAction = MutableStateFlow<PendingChatAction?>(null)
    val pendingChatAction: StateFlow<PendingChatAction?> = _pendingChatAction.asStateFlow()

    private fun parseChatTimestamp(createdAt: String): String = try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(createdAt.take(19))
        SimpleDateFormat("a h:mm", Locale.KOREAN).format(date!!)
    } catch (e: Exception) {
        ""
    }

    private fun loadChatHistory(userId: Int) {
        viewModelScope.launch {
            val history = apiGetChatMessages(userId) ?: return@launch
            _chatMessages.value = history.map { msg ->
                Message(
                    text = msg.content,
                    isSent = msg.role == "user",
                    timestamp = parseChatTimestamp(msg.createdAt)
                )
            }
        }
    }

    private fun looksLikeHabitMutationRequest(text: String): Boolean {
        val compact = text.lowercase(Locale.KOREAN).replace(" ", "")
        val habitWords = listOf("습관", "체크리스트", "루틴", "목표", "할일", "해야할")
        val mutationWords = listOf(
            "추가", "등록", "만들", "생성", "넣어", "시작",
            "수정", "변경", "바꿔", "고쳐", "조정", "늘려", "줄여", "요일",
            "삭제", "제거", "지워", "없애", "빼줘", "빼",
            "완료", "체크", "해제"
        )
        val directMutationPhrases = listOf(
            "추가해", "등록해", "만들어", "넣어줘", "시작할래",
            "수정해", "변경해", "바꿔", "고쳐", "조정해",
            "삭제해", "제거해", "지워", "없애", "빼줘"
        )
        return (habitWords.any { it in compact } && mutationWords.any { it in compact }) ||
            directMutationPhrases.any { it in compact }
    }

    fun sendChatMessage(text: String) {
        val user = _user.value ?: return
        if (text.isBlank() || _isChatLoading.value) return
        val previousPendingAction = _pendingChatAction.value
        val shouldPlanHabitAction = previousPendingAction != null || looksLikeHabitMutationRequest(text)
        _pendingChatAction.value = null
        _chatMessages.value = _chatMessages.value + Message(text, isSent = true)
        _isChatLoading.value = true
        viewModelScope.launch {
            if (shouldPlanHabitAction) {
                val result = apiPlanLLMAction(user.id, text)
                _chatMessages.value = _chatMessages.value + Message(result.message, isSent = false)
                _pendingChatAction.value = result.pendingAction
                    ?.takeIf { result.requiresConfirmation }
                    ?: result.pendingActionDescription
                        ?.takeIf { result.requiresConfirmation }
                        ?.let { PendingChatAction(action = "unknown", actionDescription = it) }
                _isChatLoading.value = false
                return@launch
            }

            var firstToken = true
            val result = apiQueryLLM(user.id, text) { token ->
                if (firstToken) {
                    _chatMessages.value = _chatMessages.value + Message(token, isSent = false)
                    _isChatLoading.value = false
                    firstToken = false
                } else {
                    val list = _chatMessages.value.toMutableList()
                    val last = list.last()
                    list[list.lastIndex] = last.copy(text = last.text + token)
                    _chatMessages.value = list
                }
            }
            if (firstToken) {
                _chatMessages.value = _chatMessages.value + Message("죄송해요, 지금은 응답하기 어려워요.", isSent = false)
            }
            _isChatLoading.value = false
            _pendingChatAction.value = result.pendingAction
                ?.takeIf { result.requiresConfirmation }
                ?: result.pendingActionDescription
                    ?.takeIf { result.requiresConfirmation }
                    ?.let { PendingChatAction(action = "unknown", actionDescription = it) }
            if (result.changed) refreshChecklist()
        }
    }

    fun confirmPendingChatAction() {
        val action = _pendingChatAction.value
        if (action == null || action.action == "unknown") {
            sendChatMessage("확인")
        } else {
            applyPendingChatAction(action)
        }
    }

    fun cancelPendingChatAction() {
        val user = _user.value ?: return
        if (_isChatLoading.value) return
        _pendingChatAction.value = null
        _chatMessages.value = _chatMessages.value + Message("취소", isSent = true)
        _isChatLoading.value = true
        viewModelScope.launch {
            val result = apiCancelLLMAction(user.id)
            _chatMessages.value = _chatMessages.value + Message(result.message, isSent = false)
            _isChatLoading.value = false
        }
    }

    fun updatePendingChatAction(action: PendingChatAction) {
        _pendingChatAction.value = action
    }

    fun applyPendingChatAction(action: PendingChatAction) {
        val user = _user.value ?: return
        if (_isChatLoading.value) return
        _pendingChatAction.value = null
        _chatMessages.value = _chatMessages.value + Message("이대로 적용", isSent = true)
        _isChatLoading.value = true
        viewModelScope.launch {
            val result = apiConfirmLLMAction(user.id, action)
            _chatMessages.value = _chatMessages.value + Message(result.message, isSent = false)
            _isChatLoading.value = false
            if (result.changed) refreshChecklist()
        }
    }

    fun clearChat() {
        val user = _user.value ?: return
        _chatMessages.value = emptyList()
        _isChatLoading.value = false
        _pendingChatAction.value = null
        viewModelScope.launch { apiClearChat(user.id) }
    }

    // ── Habits ────────────────────────────────────────────────────────────────

    fun addHabit(
        name: String,
        description: String,
        schedule: List<Int>,
        category: String,
        iconKey: String,
        customImageUri: String?,
        requiresPhoto: Boolean
    ) {
        val user = _user.value ?: return
        viewModelScope.launch {
            apiAddHabit(
                userId = user.id,
                name = name,
                description = description,
                schedule = schedule,
                category = category,
                iconKey = iconKey,
                customImageUri = customImageUri,
                requiresPhoto = requiresPhoto
            )
            refreshChecklist()
        }
    }

    fun editHabit(
        id: Int,
        name: String,
        description: String,
        schedule: List<Int>,
        category: String,
        iconKey: String,
        customImageUri: String?,
        requiresPhoto: Boolean
    ) {
        val user = _user.value ?: return
        viewModelScope.launch {
            apiEditHabit(
                id = id,
                userId = user.id,
                name = name,
                description = description,
                schedule = schedule,
                category = category,
                iconKey = iconKey,
                customImageUri = customImageUri,
                requiresPhoto = requiresPhoto
            )
            refreshChecklist()
        }
    }

    fun deleteHabit(habitId: Int) {
        viewModelScope.launch {
            if (apiRemoveHabit(habitId)) refreshChecklist()
        }
    }
}
