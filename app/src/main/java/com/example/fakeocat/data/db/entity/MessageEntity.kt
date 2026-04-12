package com.example.fakeocat.data.db.entity

data class MessageEntity(
    val id: Long = 0,
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String,
    val isBookmarked: Boolean = false
)
