package com.example.fakeocat.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @Test
    fun migrate_v1_to_v2_splits_bookmarks_and_preserves_data() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("ocat_database.db")
        if (dbFile.exists()) dbFile.delete()
        dbFile.parentFile?.mkdirs()

        val v1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1.execSQL(
            """
            CREATE TABLE messages (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              text TEXT NOT NULL,
              is_user INTEGER NOT NULL DEFAULT 0,
              timestamp INTEGER NOT NULL,
              mode TEXT NOT NULL,
              is_bookmarked INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        v1.execSQL("INSERT INTO messages (text,is_user,timestamp,mode,is_bookmarked) VALUES ('A',0,100,'FreeChat',1)")
        v1.execSQL("INSERT INTO messages (text,is_user,timestamp,mode,is_bookmarked) VALUES ('B',1,200,'FreeChat',0)")
        v1.execSQL("INSERT INTO messages (text,is_user,timestamp,mode,is_bookmarked) VALUES ('C',0,300,'HowToSay',1)")
        v1.version = 1
        v1.close()

        val helper = DatabaseHelper(context)
        helper.readableDatabase.close()

        val db = helper.readableDatabase
        val c1 = db.rawQuery("SELECT COUNT(*) FROM bookmarks", null)
        val bookmarksCount = c1.use { it.moveToFirst(); it.getLong(0) }
        assertEquals(2L, bookmarksCount)

        val c2 = db.rawQuery("PRAGMA table_info(messages)", null)
        val hasIsBookmarkedCol = c2.use {
            var found = false
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow("name"))
                if (name == "is_bookmarked") {
                    found = true
                    break
                }
            }
            found
        }
        assertFalse(hasIsBookmarkedCol)

        val c3 = db.rawQuery(
            """
            SELECT m.id,
                   CASE WHEN b.id IS NULL THEN 0 ELSE 1 END AS is_bookmarked
            FROM messages m
            LEFT JOIN bookmarks b ON b.source_message_id = m.id
            ORDER BY m.id ASC
            """.trimIndent(),
            null
        )
        val flags = c3.use {
            val list = ArrayList<Pair<Long, Int>>()
            while (it.moveToNext()) {
                list.add(it.getLong(0) to it.getInt(1))
            }
            list
        }
        assertEquals(listOf(1L to 1, 2L to 0, 3L to 1), flags)

        helper.clearHistory()
        val c4 = db.rawQuery("SELECT COUNT(*) FROM bookmarks", null)
        val afterClearBookmarks = c4.use { it.moveToFirst(); it.getLong(0) }
        assertEquals(2L, afterClearBookmarks)

        db.close()
        helper.close()
    }

    @Test
    fun bookmark_crud_is_independent_from_history() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("ocat_database.db")
        if (dbFile.exists()) dbFile.delete()

        val helper = DatabaseHelper(context)
        val msgId = helper.insertMessage(
            com.example.fakeocat.data.db.entity.MessageEntity(
                text = "hello",
                isUser = true,
                timestamp = 1234L,
                mode = "FreeChat"
            )
        )
        assertTrue(msgId > 0)
        assertFalse(helper.isMessageBookmarked(msgId))

        helper.addBookmarkFromMessage(
            com.example.fakeocat.data.db.entity.MessageEntity(
                id = msgId,
                text = "hello",
                isUser = true,
                timestamp = 1234L,
                mode = "FreeChat",
                isBookmarked = false
            ),
            createdAt = 9999L
        )
        assertTrue(helper.isMessageBookmarked(msgId))

        helper.clearHistory()
        val db = helper.readableDatabase
        val c1 = db.rawQuery("SELECT COUNT(*) FROM bookmarks", null)
        val count = c1.use { it.moveToFirst(); it.getLong(0) }
        assertEquals(1L, count)

        val c2 = db.rawQuery("SELECT source_message_id FROM bookmarks LIMIT 1", null)
        val sourceIsNull = c2.use { it.moveToFirst(); it.isNull(0) }
        assertTrue(sourceIsNull)

        db.close()
        helper.close()
    }
}

