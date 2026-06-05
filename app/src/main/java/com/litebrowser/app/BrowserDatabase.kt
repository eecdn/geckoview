package com.litebrowser.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 浏览器本地数据库 - 管理书签和历史记录
 */
class BrowserDatabase(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    companion object {
        private const val DATABASE_NAME = "lite_browser.db"
        private const val DATABASE_VERSION = 1

        // 书签表
        private const val TABLE_BOOKMARKS = "bookmarks"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_URL = "url"
        private const val COL_CREATED = "created"

        // 历史记录表
        private const val TABLE_HISTORY = "history"
        private const val COL_HISTORY_ID = "id"
        private const val COL_HISTORY_TITLE = "title"
        private const val COL_HISTORY_URL = "url"
        private const val COL_HISTORY_DATE = "date"
    }

    data class BookmarkItem(val id: Long, val title: String, val url: String)
    data class HistoryItem(val id: Long, val title: String, val url: String, val date: Long)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_BOOKMARKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_URL TEXT NOT NULL UNIQUE,
                $COL_CREATED INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_HISTORY (
                $COL_HISTORY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_HISTORY_TITLE TEXT NOT NULL,
                $COL_HISTORY_URL TEXT NOT NULL,
                $COL_HISTORY_DATE INTEGER NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_BOOKMARKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    // ===== 书签操作 =====

    suspend fun addBookmark(title: String, url: String): Long = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            TABLE_BOOKMARKS, null,
            ContentValues().apply {
                put(COL_TITLE, title)
                put(COL_URL, url)
                put(COL_CREATED, System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    suspend fun removeBookmark(id: Long): Int = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_BOOKMARKS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    suspend fun removeBookmarkByUrl(url: String): Int = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_BOOKMARKS, "$COL_URL = ?", arrayOf(url))
    }

    suspend fun getAllBookmarks(): List<BookmarkItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BookmarkItem>()
        readableDatabase.query(
            TABLE_BOOKMARKS, null, null, null, null, null,
            "$COL_CREATED DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    BookmarkItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        url = cursor.getString(cursor.getColumnIndexOrThrow(COL_URL))
                    )
                )
            }
        }
        list
    }

    suspend fun isBookmarked(url: String): Boolean = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_BOOKMARKS, arrayOf(COL_ID),
            "$COL_URL = ?", arrayOf(url), null, null, null
        ).use { it.count > 0 }
    }

    // ===== 历史记录操作 =====

    suspend fun addHistory(title: String, url: String) = withContext(Dispatchers.IO) {
        writableDatabase.insert(
            TABLE_HISTORY, null,
            ContentValues().apply {
                put(COL_HISTORY_TITLE, title)
                put(COL_HISTORY_URL, url)
                put(COL_HISTORY_DATE, System.currentTimeMillis())
            }
        )
    }

    suspend fun removeHistory(id: Long): Int = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, "$COL_HISTORY_ID = ?", arrayOf(id.toString()))
    }

    suspend fun clearHistory(): Int = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_HISTORY, null, null)
    }

    suspend fun getAllHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<HistoryItem>()
        readableDatabase.query(
            TABLE_HISTORY, null, null, null, null, null,
            "$COL_HISTORY_DATE DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    HistoryItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HISTORY_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_TITLE)),
                        url = cursor.getString(cursor.getColumnIndexOrThrow(COL_HISTORY_URL)),
                        date = cursor.getLong(cursor.getColumnIndexOrThrow(COL_HISTORY_DATE))
                    )
                )
            }
        }
        list
    }
}
