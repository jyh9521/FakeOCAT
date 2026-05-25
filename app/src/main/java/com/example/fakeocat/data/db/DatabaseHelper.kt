package com.example.fakeocat.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import com.example.fakeocat.data.db.entity.BookmarkEntity
import com.example.fakeocat.data.db.entity.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 标准 SQLite 数据库助手，使用 Base64 编码对 text 列做轻量混淆存储。
 *
 * 所有 INSERT/UPDATE 写入前对 [MessageEntity.text] 调用 [obfuscate] 编码，
 * 所有 SELECT 读取后调用 [deobfuscate] 解码，避免明文落盘。
 *
 * 继承自标准 [SQLiteOpenHelper]，无外部加密依赖。
 */
class DatabaseHelper(
    context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val appContext = context.applicationContext

    companion object {
        private const val DATABASE_NAME = "ocat_database.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_MESSAGES = "messages"
        private const val TABLE_BOOKMARKS = "bookmarks"
        private const val COL_ID = "id"
        private const val COL_TEXT = "text"
        private const val COL_IS_USER = "is_user"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_MODE = "mode"
        private const val COL_IS_BOOKMARKED = "is_bookmarked"

        private const val COL_SOURCE_MESSAGE_ID = "source_message_id"
        private const val COL_MESSAGE_TIMESTAMP = "message_timestamp"
        private const val COL_CREATED_AT = "created_at"

        /**
         * 对明文文本做 Base64 编码混淆，避免数据库文件直接暴露明文消息内容。
         * 这不是真正的加密，仅用于防止最基础的明文泄露。
         */
        private fun obfuscate(text: String): String {
            return Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        /**
         * 对 Base64 编码的文本解码还原为明文。
         */
        private fun deobfuscate(encoded: String): String {
            return String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        }
    }

    // ═══════════════════════════════════════════════════
    // 变更通知（与原有逻辑一致）
    // ═══════════════════════════════════════════════════

    /** 用于变更通知的监听器集合 */
    private val changeListeners = mutableSetOf<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        synchronized(changeListeners) { changeListeners.add(listener) }
    }

    fun removeChangeListener(listener: () -> Unit) {
        synchronized(changeListeners) { changeListeners.remove(listener) }
    }

    private fun notifyChange() {
        synchronized(changeListeners) {
            changeListeners.forEach { it.invoke() }
        }
    }

    // ═══════════════════════════════════════════════════
    // 数据库生命周期回调
    // ═══════════════════════════════════════════════════

    init {
        // 启用 WAL 模式：写操作不阻塞读操作，大幅提升并发性能
        // Android 9+ 已默认 WAL，但显式调用确保兼容
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_MESSAGES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TEXT TEXT NOT NULL,
                $COL_IS_USER INTEGER NOT NULL DEFAULT 0,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_MODE TEXT NOT NULL
            )
        """)

        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SOURCE_MESSAGE_ID INTEGER,
                $COL_TEXT TEXT NOT NULL,
                $COL_IS_USER INTEGER NOT NULL DEFAULT 0,
                $COL_MESSAGE_TIMESTAMP INTEGER NOT NULL,
                $COL_MODE TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY($COL_SOURCE_MESSAGE_ID) REFERENCES $TABLE_MESSAGES($COL_ID) ON DELETE SET NULL
            )
        """)

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_created_at ON $TABLE_BOOKMARKS($COL_CREATED_AT DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_source_message_id ON $TABLE_BOOKMARKS($COL_SOURCE_MESSAGE_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            backupDatabaseFile(oldVersion)
            migrate1To2(db)
        }
    }

    private fun backupDatabaseFile(oldVersion: Int) {
        runCatching {
            val src = appContext.getDatabasePath(DATABASE_NAME)
            if (!src.exists()) return

            val dstName = "ocat_database_v${oldVersion}_backup.db"
            val dst: File = appContext.getDatabasePath(dstName)
            if (dst.exists()) return

            dst.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dst.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun migrate1To2(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.beginTransaction()
        try {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_BOOKMARKS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_SOURCE_MESSAGE_ID INTEGER,
                    $COL_TEXT TEXT NOT NULL,
                    $COL_IS_USER INTEGER NOT NULL DEFAULT 0,
                    $COL_MESSAGE_TIMESTAMP INTEGER NOT NULL,
                    $COL_MODE TEXT NOT NULL,
                    $COL_CREATED_AT INTEGER NOT NULL,
                    FOREIGN KEY($COL_SOURCE_MESSAGE_ID) REFERENCES $TABLE_MESSAGES($COL_ID) ON DELETE SET NULL
                )
            """)

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_created_at ON $TABLE_BOOKMARKS($COL_CREATED_AT DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookmarks_source_message_id ON $TABLE_BOOKMARKS($COL_SOURCE_MESSAGE_ID)")

            db.execSQL("""
                INSERT INTO $TABLE_BOOKMARKS ($COL_SOURCE_MESSAGE_ID, $COL_TEXT, $COL_IS_USER, $COL_MESSAGE_TIMESTAMP, $COL_MODE, $COL_CREATED_AT)
                SELECT $COL_ID, $COL_TEXT, $COL_IS_USER, $COL_TIMESTAMP, $COL_MODE, $COL_TIMESTAMP
                FROM $TABLE_MESSAGES
                WHERE $COL_IS_BOOKMARKED = 1
            """)

            db.execSQL("""
                CREATE TABLE messages_new (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_TEXT TEXT NOT NULL,
                    $COL_IS_USER INTEGER NOT NULL DEFAULT 0,
                    $COL_TIMESTAMP INTEGER NOT NULL,
                    $COL_MODE TEXT NOT NULL
                )
            """)

            db.execSQL("""
                INSERT INTO messages_new ($COL_ID, $COL_TEXT, $COL_IS_USER, $COL_TIMESTAMP, $COL_MODE)
                SELECT $COL_ID, $COL_TEXT, $COL_IS_USER, $COL_TIMESTAMP, $COL_MODE
                FROM $TABLE_MESSAGES
            """)

            db.execSQL("DROP TABLE $TABLE_MESSAGES")
            db.execSQL("ALTER TABLE messages_new RENAME TO $TABLE_MESSAGES")

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    // ═══════════════════════════════════════════════════
    // CRUD 操作（写入前编码，读取后解码）
    // ═══════════════════════════════════════════════════

    suspend fun insertMessage(message: MessageEntity): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_TEXT, obfuscate(message.text))
            put(COL_IS_USER, if (message.isUser) 1 else 0)
            put(COL_TIMESTAMP, message.timestamp)
            put(COL_MODE, message.mode)
        }
        val id = writableDatabase.insert(TABLE_MESSAGES, null, values)
        notifyChange()
        id
    }

    suspend fun updateMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_TEXT, obfuscate(message.text))
            put(COL_IS_USER, if (message.isUser) 1 else 0)
            put(COL_TIMESTAMP, message.timestamp)
            put(COL_MODE, message.mode)
        }
        writableDatabase.update(TABLE_MESSAGES, values, "$COL_ID = ?", arrayOf(message.id.toString()))
        notifyChange()
    }

    suspend fun deleteMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_MESSAGES, "$COL_ID = ?", arrayOf(message.id.toString()))
        notifyChange()
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_MESSAGES, null, null)
        notifyChange()
    }

    fun getAllMessagesFlow(limit: Int = 200): Flow<List<MessageEntity>> = callbackFlow {
        val sendData = {
            val list = queryMessages("""
                SELECT
                    m.$COL_ID,
                    m.$COL_TEXT,
                    m.$COL_IS_USER,
                    m.$COL_TIMESTAMP,
                    m.$COL_MODE,
                    CASE WHEN b.$COL_ID IS NULL THEN 0 ELSE 1 END AS $COL_IS_BOOKMARKED
                FROM $TABLE_MESSAGES m
                LEFT JOIN $TABLE_BOOKMARKS b
                ON b.$COL_SOURCE_MESSAGE_ID = m.$COL_ID
                ORDER BY m.$COL_TIMESTAMP ASC
                LIMIT $limit
            """.trimIndent())
            trySend(list)
            Unit
        }
        sendData()
        val listener: () -> Unit = { sendData() }
        addChangeListener(listener)

        awaitClose { removeChangeListener(listener) }
    }

    fun getBookmarkedMessagesFlow(): Flow<List<BookmarkEntity>> = callbackFlow {
        val sendData = {
            val list = queryBookmarks("""
                SELECT 
                    $COL_ID,
                    $COL_SOURCE_MESSAGE_ID,
                    $COL_TEXT,
                    $COL_IS_USER,
                    $COL_MESSAGE_TIMESTAMP,
                    $COL_MODE,
                    $COL_CREATED_AT
                FROM $TABLE_BOOKMARKS
                ORDER BY $COL_CREATED_AT DESC
            """.trimIndent())
            trySend(list)
            Unit
        }
        sendData()
        val listener: () -> Unit = { sendData() }
        addChangeListener(listener)

        awaitClose { removeChangeListener(listener) }
    }

    suspend fun addBookmarkFromMessage(message: MessageEntity, createdAt: Long = System.currentTimeMillis()): Long =
        withContext(Dispatchers.IO) {
            if (message.id <= 0) return@withContext -1L

            val values = ContentValues().apply {
                put(COL_SOURCE_MESSAGE_ID, message.id)
                put(COL_TEXT, obfuscate(message.text))
                put(COL_IS_USER, if (message.isUser) 1 else 0)
                put(COL_MESSAGE_TIMESTAMP, message.timestamp)
                put(COL_MODE, message.mode)
                put(COL_CREATED_AT, createdAt)
            }
            val id = writableDatabase.insert(TABLE_BOOKMARKS, null, values)
            notifyChange()
            id
        }

    suspend fun removeBookmarkBySourceMessageId(messageId: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_BOOKMARKS, "$COL_SOURCE_MESSAGE_ID = ?", arrayOf(messageId.toString()))
        notifyChange()
    }

    suspend fun removeBookmarkById(bookmarkId: Long) = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_BOOKMARKS, "$COL_ID = ?", arrayOf(bookmarkId.toString()))
        notifyChange()
    }

    suspend fun isMessageBookmarked(messageId: Long): Boolean = withContext(Dispatchers.IO) {
        val cursor = readableDatabase.rawQuery(
            "SELECT 1 FROM $TABLE_BOOKMARKS WHERE $COL_SOURCE_MESSAGE_ID = ? LIMIT 1",
            arrayOf(messageId.toString())
        )
        cursor.use { it.moveToFirst() }
    }

    // ═══════════════════════════════════════════════════
    // 私有查询辅助方法（读取后解码）
    // ═══════════════════════════════════════════════════

    private fun queryMessages(sql: String): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        val cursor: Cursor = readableDatabase.rawQuery(sql, null)
        cursor.use {
            while (it.moveToNext()) {
                messages.add(
                    MessageEntity(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        text = deobfuscate(it.getString(it.getColumnIndexOrThrow(COL_TEXT))),
                        isUser = it.getInt(it.getColumnIndexOrThrow(COL_IS_USER)) == 1,
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP)),
                        mode = it.getString(it.getColumnIndexOrThrow(COL_MODE)),
                        isBookmarked = it.getInt(it.getColumnIndexOrThrow(COL_IS_BOOKMARKED)) == 1
                    )
                )
            }
        }
        return messages
    }

    private fun queryBookmarks(sql: String): List<BookmarkEntity> {
        val bookmarks = mutableListOf<BookmarkEntity>()
        val cursor: Cursor = readableDatabase.rawQuery(sql, null)
        cursor.use {
            val sourceIdIndex = it.getColumnIndexOrThrow(COL_SOURCE_MESSAGE_ID)
            while (it.moveToNext()) {
                val sourceMessageId = if (it.isNull(sourceIdIndex)) null else it.getLong(sourceIdIndex)
                bookmarks.add(
                    BookmarkEntity(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        sourceMessageId = sourceMessageId,
                        text = deobfuscate(it.getString(it.getColumnIndexOrThrow(COL_TEXT))),
                        isUser = it.getInt(it.getColumnIndexOrThrow(COL_IS_USER)) == 1,
                        messageTimestamp = it.getLong(it.getColumnIndexOrThrow(COL_MESSAGE_TIMESTAMP)),
                        mode = it.getString(it.getColumnIndexOrThrow(COL_MODE)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED_AT))
                    )
                )
            }
        }
        return bookmarks
    }
}
