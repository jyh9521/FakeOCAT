package com.example.fakeocat.network

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * AI 响应缓存。
 *
 * 适用场景：
 * - "WhatMeans" 模式的短查询（如查词释义），结果较稳定，缓存 30 分钟。
 * - 不缓存 "HowToSay" 和 "FreeChat" 模式（结果多变）。
 *
 * 缓存层级：
 * 1. 内存 LRU 缓存（最多 128 条，最快访问）
 * 2. 磁盘缓存（app cache 目录，进程重启后可用）
 */
class ResponseCache(
    private val context: Context
) {

    private val TAG = "ResponseCache"

    /** 磁盘缓存目录，由构造函数从 Context 派生 */
    private val cacheDir: File = File(context.cacheDir, CACHE_DIR_NAME)

    /** 内存 LRU 缓存：最多 128 条，访问顺序排列（access-order） */
    private val memCache = object : LinkedHashMap<String, CacheEntry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > 128
        }
    }

    /** 磁盘缓存最大大小：50 MB */
    private val maxDiskCacheBytes: Long = MAX_DISK_CACHE_BYTES

    data class CacheEntry(
        val response: String,
        val timestamp: Long,
        val ttl: Long = DEFAULT_TTL_MS
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > ttl
    }

    /**
     * 根据缓存键获取缓存的响应。
     * 先查内存（O(1)），再查磁盘。
     */
    fun get(cacheKey: String): String? {
        // 1. 查内存
        memCache[cacheKey]?.let { entry ->
            if (!entry.isExpired()) {
                Log.d(TAG, "Memory cache hit for key=$cacheKey")
                return entry.response
            }
            memCache.remove(cacheKey)
        }

        // 2. 查磁盘
        try {
            val file = cacheFile(cacheKey)
            if (file.exists()) {
                val json = JSONObject(file.readText())
                val entry = CacheEntry(
                    response = json.getString("response"),
                    timestamp = json.getLong("timestamp"),
                    ttl = json.optLong("ttl", DEFAULT_TTL_MS)
                )
                if (!entry.isExpired()) {
                    memCache[cacheKey] = entry
                    Log.d(TAG, "Disk cache hit for key=$cacheKey")
                    return entry.response
                }
                // 过期则删除
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Disk cache read failed for key=$cacheKey: ${e.message}")
        }

        return null
    }

    /**
     * 写入缓存。
     * 同步写入内存，异步写磁盘。
     */
    fun put(cacheKey: String, response: String) {
        val entry = CacheEntry(response, System.currentTimeMillis())
        memCache[cacheKey] = entry

        // 异步写磁盘
        Thread {
            try {
                cacheDir.mkdirs()
                val file = cacheFile(cacheKey)
                file.writeText(JSONObject().apply {
                    put("response", response)
                    put("timestamp", entry.timestamp)
                    put("ttl", DEFAULT_TTL_MS)
                }.toString())
                Log.d(TAG, "Cached to disk: key=$cacheKey")
            } catch (e: Exception) {
                Log.w(TAG, "Disk cache write failed: ${e.message}")
            }
        }.apply {
            isDaemon = true
            name = "cache-writer"
        }.start()

        // 写入后检查磁盘缓存大小，超出限制则清理最旧文件
        enforceDiskCacheLimit()
    }

    /**
     * 确保磁盘缓存总大小不超过 MAX_DISK_CACHE_BYTES。
     * 超出限制时按最后修改时间升序删除最旧文件，直到低于 80% 阈值。
     */
    private fun enforceDiskCacheLimit() {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }
            if (totalSize > maxDiskCacheBytes) {
                files.sortedBy { it.lastModified() }
                    .forEach { file ->
                        if (totalSize <= maxDiskCacheBytes * 0.8) return
                        totalSize -= file.length()
                        file.delete()
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "磁盘缓存清理失败: ${e.message}")
        }
    }

    /** 清空所有缓存（内存 + 磁盘）。 */
    fun clear() {
        memCache.clear()
        try {
            cacheDir.deleteRecursively()
        } catch (_: Exception) { }
    }

    private fun cacheFile(key: String): File = cacheDir.resolve("$key.json")

    companion object {
        private const val DEFAULT_TTL_MS = 30 * 60 * 1000L // 30 分钟
        private const val MAX_DISK_CACHE_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val CACHE_DIR_NAME = "ai_response_cache"

        /**
         * 生成缓存键。
         * 包含 provider、model、mode 和 messages 的 SHA-256 哈希。
         */
        fun buildCacheKey(provider: String, model: String, mode: String, messages: String): String {
            val input = "$provider|$model|$mode|$messages"
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
