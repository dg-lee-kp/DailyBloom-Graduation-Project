package com.example.dailybloom

data class ChecklistEntry(
    val id: Int,
    val checklistId: Int,
    val habitId: Int,
    val checked: Boolean,
    val checkedAt: String?,
    val photoProofUri: String? = null
)
