package com.example.dailybloom

data class User(
    val id: Int,
    val email: String,
    val username: String,
    val createdAt: String,
    val isFirstLogin: Boolean = true
)