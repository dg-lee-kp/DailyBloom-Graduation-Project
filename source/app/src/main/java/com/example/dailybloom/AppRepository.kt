package com.example.dailybloom

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val JSON = "application/json".toMediaType()
private val client: OkHttpClient by lazy { OkHttpClient() }
private val llmClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
}
private val BASE_URL = "https://kcits970.org/"

suspend fun apiRequestSignup(email: String, username: String, password: String): User? =
    withContext(Dispatchers.IO) {
        val body =
            buildJsonBody("email" to email, "username" to username, "password" to password)
        val request = buildPostRequest("$BASE_URL/add-user", body)
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body.string())
                    if (jsonObject.getInt("status") == 0)
                        parseUserFromJSONObject(jsonObject.getJSONObject("user"))
                    else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

suspend fun apiValidateUser(email: String, password: String): User? = withContext(Dispatchers.IO) {
    val body = buildJsonBody("email" to email, "password" to password)
    val request = buildPostRequest("$BASE_URL/find-user", body)
    try {
        client.newCall(request).execute().use { response ->
            val jsonObject = JSONObject(response.body.string())
            if (jsonObject.getInt("status") == 0)
                parseUserFromJSONObject(jsonObject.getJSONObject("user"))
            else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun apiGetAllHabits(user: User): List<Habit>? = withContext(Dispatchers.IO) {
    val body = buildJsonBody("user_id" to user.id)
    val request = buildPostRequest("$BASE_URL/get-all-habits", body)
    try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val jsonObject = JSONObject(response.body.string())
                if (jsonObject.getInt("status") == 0) {
                    val habitsArray = jsonObject.getJSONArray("habits")
                    (0 until habitsArray.length()).map {
                        parseHabitFromJSONObject(habitsArray.getJSONObject(it))
                    }
                } else null
            } else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun apiGetChecklist(user: User): Pair<List<Habit>, List<ChecklistEntry>>? =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody("user_id" to user.id)
        val request = buildPostRequest("$BASE_URL/get-checklist", body)
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonObject = JSONObject(response.body.string())
                    if (jsonObject.getInt("status") == 0) {
                        val habitsArray = jsonObject.getJSONArray("habits")
                        val entriesArray = jsonObject.getJSONArray("entries")
                        Pair(
                            (0 until habitsArray.length()).map {
                                parseHabitFromJSONObject(habitsArray.getJSONObject(it))
                            },
                            (0 until entriesArray.length()).map {
                                parseChecklistEntryFromJSONObject(entriesArray.getJSONObject(it))
                            }
                        )
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

data class GrowthData(
    val totalCompleted: Int,
    val perfectDays: Int,
    val streakDays: Int
)

suspend fun apiGetGrowthData(userId: Int): GrowthData? = withContext(Dispatchers.IO) {
    val body    = buildJsonBody("user_id" to userId)
    val request = buildPostRequest("$BASE_URL/get-growth-data", body)
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body.string())
            if (json.getInt("status") != 0) return@withContext null
            GrowthData(
                totalCompleted = json.getInt("total_completed"),
                perfectDays    = json.getInt("perfect_days"),
                streakDays     = json.getInt("streak_days")
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

data class WeekDayData(val label: String, val total: Int, val completed: Int) {
    val rate: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class TrendPoint(val label: String, val rate: Int)

data class HabitStat(
    val habitId: Int,
    val name: String,
    val category: String = "기타",
    val iconKey: String = "check",
    val customImageUri: String? = null,
    val requiresPhoto: Boolean = false,
    val total: Int,
    val completed: Int,
    val rate: Int
) {
    val failCount: Int get() = total - completed
}

data class ReportData(
    val monthlyRate: Int,
    val totalCompleted: Int,
    val weekData: List<WeekDayData>,
    val trendData: List<TrendPoint>,
    val habitStats: List<HabitStat>
)

data class OnboardingHabit(
    val name: String,
    val description: String,
    val schedule: List<Int>,
    val category: String = "기타",
    val iconKey: String = "check",
    val requiresPhoto: Boolean = false
)

data class OnboardingRecommendation(
    val summary: String,
    val habits: List<OnboardingHabit>,
    val message: String
)

suspend fun apiGetReport(userId: Int, year: Int, month: Int): ReportData? =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody("user_id" to userId, "year" to year, "month" to month)
        val request = buildPostRequest("$BASE_URL/get-report", body)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body.string())
                if (json.getInt("status") != 0) return@withContext null

                val weekArr = json.getJSONArray("week_data")
                val weekData = (0 until weekArr.length()).map {
                    val o = weekArr.getJSONObject(it)
                    WeekDayData(o.getString("label"), o.getInt("total"), o.getInt("completed"))
                }

                val trendArr = json.getJSONArray("trend_data")
                val trendData = (0 until trendArr.length()).map {
                    val o = trendArr.getJSONObject(it)
                    TrendPoint(o.getString("label"), o.getInt("rate"))
                }

                val habitArr = json.getJSONArray("habit_stats")
                val habitStats = (0 until habitArr.length()).map {
                    val o = habitArr.getJSONObject(it)
                    HabitStat(
                        habitId        = o.getInt("habit_id"),
                        name           = o.getString("name"),
                        category       = normalizedHabitCategory(o.optString("category")),
                        iconKey        = normalizedHabitIcon(o.optString("icon_key")),
                        customImageUri = o.optString("custom_image_uri")
                            .takeIf { it.isNotBlank() && it != "null" },
                        requiresPhoto  = o.optBoolean("requires_photo", false),
                        total          = o.getInt("total"),
                        completed      = o.getInt("completed"),
                        rate           = o.getInt("rate")
                    )
                }

                ReportData(
                    monthlyRate    = json.getInt("monthly_rate"),
                    totalCompleted = json.getInt("total_completed"),
                    weekData       = weekData,
                    trendData      = trendData,
                    habitStats     = habitStats
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

suspend fun apiGetOnboardingRecommendations(
    categories: List<String>,
    preferredTime: String,
    difficulty: String,
    frictions: List<String>,
    extra: String
): OnboardingRecommendation? = withContext(Dispatchers.IO) {
    val fallback = buildFallbackOnboardingRecommendation(
        categories = categories,
        preferredTime = preferredTime,
        difficulty = difficulty,
        frictions = frictions,
        extra = extra
    )
    val body = buildJsonBody(
        "categories" to org.json.JSONArray(categories),
        "preferred_time" to preferredTime,
        "difficulty" to difficulty,
        "frictions" to org.json.JSONArray(frictions),
        "extra" to extra
    )
    val request = buildPostRequest("$BASE_URL/onboarding-recommendations", body)
    try {
        llmClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext fallback
            val json = JSONObject(response.body.string())
            if (json.getInt("status") != 0) return@withContext fallback
            val habitsArray = json.getJSONArray("habits")
            val habits = (0 until habitsArray.length()).mapNotNull {
                val habit = habitsArray.getJSONObject(it)
                val scheduleArray = habit.getJSONArray("schedule")
                val parsedHabit = OnboardingHabit(
                    name = habit.optString("name"),
                    description = habit.optString("description"),
                    schedule = (0 until scheduleArray.length()).map { index ->
                        scheduleArray.getInt(index)
                    },
                    category = normalizedHabitCategory(habit.optString("category")),
                    iconKey = normalizedHabitIcon(habit.optString("icon_key")),
                    requiresPhoto = habit.optBoolean("requires_photo", false)
                )
                parsedHabit.takeIf { item -> item.name.isNotBlank() && item.schedule.isNotEmpty() }
            }
            if (habits.isEmpty()) return@withContext fallback
            OnboardingRecommendation(
                summary = json.optString("summary").ifBlank { fallback.summary },
                message = json.optString("message").ifBlank { fallback.message },
                habits = habits
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        fallback
    }
}

private fun buildFallbackOnboardingRecommendation(
    categories: List<String>,
    preferredTime: String,
    difficulty: String,
    frictions: List<String>,
    extra: String
): OnboardingRecommendation {
    val habits = mutableListOf<OnboardingHabit>()

    if ("건강" in categories) {
        habits += OnboardingHabit(
            name = "물 한 컵 마시기",
            description = "하루 중 편한 시간에 물 한 컵을 마셔요.",
            schedule = listOf(0, 1, 2, 3, 4, 5, 6),
            category = "건강",
            iconKey = "water"
        )
    }
    if ("공부/성장" in categories) {
        habits += OnboardingHabit(
            name = "책 1페이지 읽기",
            description = "부담 없이 한 페이지만 읽고 표시해요.",
            schedule = listOf(1, 2, 3, 4, 5),
            category = "공부",
            iconKey = "book"
        )
    }
    if ("마음관리" in categories) {
        habits += OnboardingHabit(
            name = "감정 한 줄 기록",
            description = "오늘의 기분을 한 문장으로 적어요.",
            schedule = listOf(0, 1, 2, 3, 4, 5, 6),
            category = "생활",
            iconKey = "star"
        )
    }
    if ("생활정리" in categories) {
        habits += OnboardingHabit(
            name = "책상 3분 정리",
            description = "눈에 보이는 물건 3개만 제자리에 둬요.",
            schedule = listOf(1, 2, 3, 4, 5),
            category = "정리",
            iconKey = "check"
        )
    }
    if ("인간관계" in categories) {
        habits += OnboardingHabit(
            name = "안부 메시지 보내기",
            description = "떠오르는 사람 한 명에게 짧게 연락해요.",
            schedule = listOf(1, 3, 5),
            category = "기타",
            iconKey = "star"
        )
    }

    if (habits.isEmpty()) {
        habits += listOf(
            OnboardingHabit(
                name = if (preferredTime == "저녁") "저녁 5분 산책" else "가벼운 스트레칭",
                description = "몸을 가볍게 움직이며 하루 리듬을 만들어요.",
                schedule = listOf(1, 2, 3, 4, 5),
                category = "운동",
                iconKey = "walk"
            ),
            OnboardingHabit(
                name = "내일 할 일 1개 적기",
                description = "가장 중요한 일 하나만 정리해요.",
                schedule = listOf(0, 1, 2, 3, 4, 5, 6),
                category = "생활",
                iconKey = "check"
            ),
            OnboardingHabit(
                name = "물 한 컵 마시기",
                description = "작게 시작하는 건강 루틴을 만들어요.",
                schedule = listOf(0, 1, 2, 3, 4, 5, 6),
                category = "건강",
                iconKey = "water"
            )
        )
    }

    val limit = if (
        difficulty == "아주 가볍게" ||
        "시간 부족" in frictions ||
        "동기 부족" in frictions
    ) 3 else 5
    val selectedHabits = habits.take(limit)
    val categorySummary = categories.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "기본 생활 리듬"
    val timeSummary = preferredTime.ifBlank { "편한 시간" }
    val extraSummary = extra.trim().takeIf { it.isNotBlank() }?.let { " 적어주신 내용도 반영해 무리 없는 습관으로 골랐어요." } ?: ""

    return OnboardingRecommendation(
        summary = "AI 추천 서버 연결이 불안정해서 기본 템플릿을 준비했어요. $categorySummary 중심으로 ${timeSummary}에 실천하기 좋은 습관입니다.$extraSummary",
        habits = selectedHabits,
        message = "이 습관들로 시작해볼까요?"
    )
}

suspend fun apiCompleteOnboarding(userId: Int, habits: List<OnboardingHabit>): User? =
    withContext(Dispatchers.IO) {
        val habitsArray = org.json.JSONArray().apply {
            habits.forEach { habit ->
                put(JSONObject().apply {
                    put("name", habit.name)
                    put("description", habit.description)
                    put("schedule", org.json.JSONArray(habit.schedule))
                    put("category", habit.category)
                    put("icon_key", habit.iconKey)
                    put("requires_photo", habit.requiresPhoto)
                    put("custom_image_uri", "")
                })
            }
        }
        val body = buildJsonBody("user_id" to userId, "habits" to habitsArray)
        val request = buildPostRequest("$BASE_URL/complete-onboarding", body)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body.string())
                if (json.getInt("status") == 0)
                    parseUserFromJSONObject(json.getJSONObject("user"))
                else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

data class DayCompletion(
    val date: String,   // "YYYY-MM-DD"
    val total: Int,
    val completed: Int
) {
    val day: Int get() = date.split("-")[2].toInt()
    val completionRate: Int get() = if (total == 0) 0 else (completed * 100 / total)
    val isPerfect: Boolean get() = total > 0 && completed == total
    val hasActivity: Boolean get() = total > 0
}

suspend fun apiGetMonthCalendar(userId: Int, year: Int, month: Int): List<DayCompletion>? =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody("user_id" to userId, "year" to year, "month" to month)
        val request = buildPostRequest("$BASE_URL/get-month-calendar", body)
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body.string())
                    if (json.getInt("status") == 0) {
                        val arr = json.getJSONArray("days")
                        (0 until arr.length()).map {
                            val obj = arr.getJSONObject(it)
                            DayCompletion(
                                date = obj.getString("date"),
                                total = obj.getInt("total"),
                                completed = obj.getInt("completed")
                            )
                        }
                    } else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

suspend fun apiAddHabit(
    userId: Int,
    name: String,
    description: String,
    schedule: List<Int>,
    category: String,
    iconKey: String,
    customImageUri: String?,
    requiresPhoto: Boolean
): Habit? =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody(
            "user_id" to userId,
            "name" to name,
            "description" to description,
            "schedule" to org.json.JSONArray(schedule),
            "category" to category,
            "icon_key" to iconKey,
            "custom_image_uri" to (customImageUri ?: ""),
            "requires_photo" to requiresPhoto
        )
        val request = buildPostRequest("$BASE_URL/add-habit", body)
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body.string())
                    if (json.getInt("status") == 0)
                        parseHabitFromJSONObject(json.getJSONObject("habit"))
                    else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

suspend fun apiEditHabit(
    id: Int,
    userId: Int,
    name: String,
    description: String,
    schedule: List<Int>,
    category: String,
    iconKey: String,
    customImageUri: String?,
    requiresPhoto: Boolean
): Habit? =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody(
            "id" to id,
            "user_id" to userId,
            "name" to name,
            "description" to description,
            "schedule" to org.json.JSONArray(schedule),
            "category" to category,
            "icon_key" to iconKey,
            "custom_image_uri" to (customImageUri ?: ""),
            "requires_photo" to requiresPhoto
        )
        val request = buildPostRequest("$BASE_URL/edit-habit", body)
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body.string())
                    if (json.getInt("status") == 0)
                        parseHabitFromJSONObject(json.getJSONObject("habit"))
                    else null
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

suspend fun apiRemoveHabit(habitId: Int): Boolean = withContext(Dispatchers.IO) {
    val body = buildJsonBody("id" to habitId)
    val request = buildPostRequest("$BASE_URL/remove-habit", body)
    try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful)
                JSONObject(response.body.string()).getInt("status") == 0
            else false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

suspend fun apiToggleEntry(
    entryId: Int,
    checked: Boolean,
    userId: Int,
    photoProofUri: String? = null
): Boolean = withContext(Dispatchers.IO) {
    val bodyJson = JSONObject().apply {
        put("entry_id", entryId)
        put("checked", checked)
        put("user_id", userId)
        if (checked && !photoProofUri.isNullOrBlank()) {
            put("photo_proof_uri", photoProofUri)
        }
    }
    val body = bodyJson.toString().toRequestBody(JSON)
    val request = buildPostRequest("$BASE_URL/toggle-entry", body)
    try {
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val jsonObject = JSONObject(response.body.string())
                jsonObject.getInt("status") == 0
            } else false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun parseUserFromJSONObject(jsonObject: JSONObject): User {
    return User(
        id = jsonObject.optInt("id"),
        email = jsonObject.optString("email"),
        username = jsonObject.optString("username"),
        createdAt = jsonObject.optString("created_at"),
        isFirstLogin = jsonObject.optBoolean("is_first_login")
    )
}

private fun parseHabitFromJSONObject(jsonObject: JSONObject): Habit {
    return Habit(
        id = jsonObject.optInt("id"),
        userId = jsonObject.optInt("user_id"),
        name = jsonObject.optString("name"),
        description = jsonObject.optString("description"),
        schedule = jsonObject.getJSONArray("schedule").let { arr ->
            (0 until arr.length()).map { arr.getInt(it) }
        },
        category = normalizedHabitCategory(jsonObject.optString("category")),
        iconKey = normalizedHabitIcon(jsonObject.optString("icon_key")),
        customImageUri = jsonObject.optString("custom_image_uri")
            .takeIf { it.isNotBlank() && it != "null" },
        requiresPhoto = jsonObject.optBoolean("requires_photo", false),
        createdAt = jsonObject.optString("created_at"),
        deletedAt = jsonObject.optString("deleted_at")
    )
}

private fun parseChecklistEntryFromJSONObject(jsonObject: JSONObject): ChecklistEntry {
    return ChecklistEntry(
        id = jsonObject.optInt("id"),
        checklistId = jsonObject.optInt("checklist_id"),
        habitId = jsonObject.optInt("habit_id"),
        checked = jsonObject.optBoolean("checked"),
        checkedAt = jsonObject.optString("checked_at"),
        photoProofUri = jsonObject.optString("photo_proof_uri")
            .takeIf { it.isNotBlank() && it != "null" }
    )
}

data class LLMResult(
    val changed: Boolean = false,
    val requiresConfirmation: Boolean = false,
    val pendingActionDescription: String? = null,
    val pendingAction: PendingChatAction? = null
)

data class PlanLLMActionResult(
    val requiresConfirmation: Boolean = false,
    val pendingActionDescription: String? = null,
    val pendingAction: PendingChatAction? = null,
    val message: String = "변경할 내용을 확인하지 못했어요. 다시 한번 알려주세요."
)

data class PendingChatAction(
    val action: String,
    val actionDescription: String,
    val habitId: Int? = null,
    val entryId: Int? = null,
    val checked: Boolean? = null,
    val name: String = "",
    val habitDescription: String = "",
    val schedule: List<Int> = emptyList(),
    val category: String = "기타",
    val iconKey: String = "check",
    val requiresPhoto: Boolean = false,
    val hasDescription: Boolean = false,
    val hasSchedule: Boolean = false,
    val hasCategory: Boolean = false,
    val hasIconKey: Boolean = false,
    val hasRequiresPhoto: Boolean = false
)

data class ConfirmLLMActionResult(
    val changed: Boolean = false,
    val message: String = "변경을 적용하지 못했어요. 다시 시도해 주세요."
)

suspend fun apiPlanLLMAction(userId: Int, message: String): PlanLLMActionResult =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody("user_id" to userId, "message" to message)
        val request = buildPostRequest("$BASE_URL/plan-llm-action", body)
        try {
            llmClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext PlanLLMActionResult(
                        message = "서버가 아직 AI 변경 확인 기능을 완전히 받지 못했어요. 습관 추가, 수정, 삭제는 확인 절차가 준비된 뒤에만 진행할게요."
                    )
                }
                val json = JSONObject(response.body.string())
                if (json.optInt("status", -1) != 0) {
                    return@withContext PlanLLMActionResult(
                        message = json.optString("message", "변경 계획을 만들지 못했어요. 다시 시도해 주세요.")
                    )
                }
                val description = json.optString("pending_action_description")
                    .takeIf { it.isNotBlank() && it != "null" }
                PlanLLMActionResult(
                    requiresConfirmation = json.optBoolean("requires_confirmation", false),
                    pendingActionDescription = description,
                    pendingAction = json.optJSONObject("pending_action")
                        ?.let { parsePendingChatAction(it, description) },
                    message = json.optString(
                        "message",
                        description?.let { "$it 로 준비했어요. 확인 팝업에서 적용해 주세요." }
                            ?: "변경할 내용을 확인하지 못했어요. 다시 한번 알려주세요."
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            PlanLLMActionResult(
                message = "네트워크 문제로 변경 계획을 만들지 못했어요. 습관 변경은 적용하지 않았습니다."
            )
        }
    }

suspend fun apiQueryLLM(userId: Int, message: String, onToken: (String) -> Unit): LLMResult = withContext(Dispatchers.IO) {
    val body = buildJsonBody("user_id" to userId, "message" to message)
    val request = buildPostRequest("$BASE_URL/query-llm", body)
    try {
        llmClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                onToken("서버에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.")
                return@withContext LLMResult()
            }
            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val payload = line.removePrefix("data: ")
                if (payload == "[DONE]") break
                val json = JSONObject(payload)
                val token = json.optString("t")
                if (token.isNotEmpty()) {
                    onToken(token)
                } else if (json.has("changed")) {
                    val description = json.optString("pending_action_description")
                        .takeIf { it.isNotBlank() && it != "null" }
                    return@withContext LLMResult(
                        changed = json.optBoolean("changed", false),
                        requiresConfirmation = json.optBoolean("requires_confirmation", false),
                        pendingActionDescription = description,
                        pendingAction = json.optJSONObject("pending_action")
                            ?.let { parsePendingChatAction(it, description) }
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onToken("네트워크 오류가 발생했어요. 인터넷 연결을 확인해 주세요.")
    }
    return@withContext LLMResult()
}

suspend fun apiConfirmLLMAction(userId: Int, action: PendingChatAction): ConfirmLLMActionResult =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody(
            "user_id" to userId,
            "message" to "이대로 적용",
            "action" to pendingChatActionToJson(action)
        )
        val request = buildPostRequest("$BASE_URL/confirm-llm-action", body)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ConfirmLLMActionResult(
                        message = "서버가 아직 확인 적용 기능을 받지 못했어요. 서버 배포 후 다시 시도해 주세요."
                    )
                }
                val json = JSONObject(response.body.string())
                if (json.optInt("status", -1) != 0) {
                    return@withContext ConfirmLLMActionResult(
                        message = json.optString("message", "변경을 적용하지 못했어요.")
                    )
                }
                ConfirmLLMActionResult(
                    changed = json.optBoolean("changed", false),
                    message = json.optString("message", "처리했어요.")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ConfirmLLMActionResult(message = "네트워크 오류로 변경을 적용하지 못했어요.")
        }
    }

suspend fun apiCancelLLMAction(userId: Int): ConfirmLLMActionResult =
    withContext(Dispatchers.IO) {
        val body = buildJsonBody("user_id" to userId, "message" to "취소")
        val request = buildPostRequest("$BASE_URL/cancel-llm-action", body)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ConfirmLLMActionResult(
                        changed = false,
                        message = "알겠어요. 변경하지 않았어요."
                    )
                }
                val json = JSONObject(response.body.string())
                ConfirmLLMActionResult(
                    changed = false,
                    message = json.optString("message", "알겠어요. 변경하지 않았어요.")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ConfirmLLMActionResult(changed = false, message = "알겠어요. 변경하지 않았어요.")
        }
    }

data class ChatMessageData(val role: String, val content: String, val createdAt: String)

suspend fun apiGetChatMessages(userId: Int): List<ChatMessageData>? = withContext(Dispatchers.IO) {
    val body = buildJsonBody("user_id" to userId)
    val request = buildPostRequest("$BASE_URL/get-chat-messages", body)
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val json = JSONObject(response.body.string())
            if (json.getInt("status") != 0) return@withContext null
            val arr = json.getJSONArray("messages")
            (0 until arr.length()).map {
                val obj = arr.getJSONObject(it)
                ChatMessageData(
                    role = obj.getString("role"),
                    content = obj.getString("content"),
                    createdAt = obj.getString("created_at")
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun apiClearChat(userId: Int): Boolean = withContext(Dispatchers.IO) {
    val body = buildJsonBody("user_id" to userId)
    val request = buildPostRequest("$BASE_URL/clear-chat", body)
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            JSONObject(response.body.string()).getInt("status") == 0
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun buildJsonBody(vararg pairs: Pair<String, Any>): RequestBody {
    val json = JSONObject().apply {
        pairs.forEach { (key, value) -> put(key, value) }
    }.toString()
    return json.toRequestBody(JSON)
}

private fun buildPostRequest(url: String, body: RequestBody): Request {
    return Request.Builder()
        .url(url)
        .post(body)
        .build()
}

private fun parsePendingChatAction(json: JSONObject, fallbackDescription: String?): PendingChatAction {
    val args = json.optJSONObject("arguments") ?: JSONObject()
    return PendingChatAction(
        action = json.optString("action", "unknown"),
        actionDescription = json.optString("description")
            .takeIf { it.isNotBlank() && it != "null" }
            ?: fallbackDescription
            ?: "요청한 변경",
        habitId = args.optIntOrNull("habit_id"),
        entryId = args.optIntOrNull("entry_id"),
        checked = args.optBooleanOrNull("checked"),
        name = args.optString("name"),
        habitDescription = args.optString("description"),
        schedule = args.optJSONArray("schedule").toIntList(),
        category = args.optString("category", "기타").ifBlank { "기타" },
        iconKey = args.optString("icon_key", "check").ifBlank { "check" },
        requiresPhoto = args.optBoolean("requires_photo", false),
        hasDescription = args.has("description") && !args.isNull("description"),
        hasSchedule = args.has("schedule") && !args.isNull("schedule"),
        hasCategory = args.has("category") && !args.isNull("category"),
        hasIconKey = args.has("icon_key") && !args.isNull("icon_key"),
        hasRequiresPhoto = args.has("requires_photo") && !args.isNull("requires_photo")
    )
}

private fun pendingChatActionToJson(action: PendingChatAction): JSONObject {
    val args = JSONObject()
    when (action.action) {
        "add_habit" -> {
            args.put("name", action.name)
            args.put("description", action.habitDescription)
            args.put("schedule", JSONArray(action.schedule))
            args.put("category", action.category)
            args.put("icon_key", action.iconKey)
            args.put("requires_photo", action.requiresPhoto)
        }
        "edit_habit" -> {
            action.habitId?.let { args.put("habit_id", it) }
            if (action.name.isNotBlank()) args.put("name", action.name)
            if (action.hasDescription) args.put("description", action.habitDescription)
            if (action.hasSchedule && action.schedule.isNotEmpty()) args.put("schedule", JSONArray(action.schedule))
            if (action.hasCategory) args.put("category", action.category)
            if (action.hasIconKey) args.put("icon_key", action.iconKey)
            if (action.hasRequiresPhoto) args.put("requires_photo", action.requiresPhoto)
        }
        "toggle_habit" -> {
            action.entryId?.let { args.put("entry_id", it) }
            action.checked?.let { args.put("checked", it) }
        }
        "remove_habit" -> {
            action.habitId?.let { args.put("habit_id", it) }
        }
    }
    return JSONObject()
        .put("action", action.action)
        .put("arguments", args)
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optBooleanOrNull(name: String): Boolean? =
    if (has(name) && !isNull(name)) optBoolean(name) else null

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optInt(index).takeIf { it in 0..6 }
    }.distinct().sorted()
}
