package com.example.dailybloom

data class Habit(
    val id: Int,
    val userId: Int,
    val name: String,
    val description: String?,
    val schedule: List<Int>,
    val category: String = "기타",
    val iconKey: String = "check",
    val customImageUri: String? = null,
    val requiresPhoto: Boolean = false,
    val createdAt: String,
    val deletedAt: String?
)
