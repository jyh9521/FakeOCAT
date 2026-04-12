package com.example.fakeocat.data.db.entity

data class BookmarkEntity(
    val id: Long = 0,
    val sourceMessageId: Long?,
    val text: String,
    val isUser: Boolean,
    val messageTimestamp: Long,
    val mode: String,
    val createdAt: Long = System.currentTimeMillis()
)
