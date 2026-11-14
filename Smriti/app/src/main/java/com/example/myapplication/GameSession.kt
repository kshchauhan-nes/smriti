package com.example.myapplication

data class GameSession(
    val score: Int,
    val level: Int,
    val responseTime: Long,
    val correctAnswers: Int,
    val totalObjects: Int,
    val timestamp: Long = System.currentTimeMillis()
)