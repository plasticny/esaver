package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.viewer.data.dao.BookDao
import com.example.viewer.data.dao.BookWithGroupDao
import com.example.viewer.data.dao.GroupDao
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.data.struct.Group

@Database (
    entities = [Book::class, BookWithGroup::class, Group::class],
    version = 6,
    exportSchema = false
)
abstract class BookDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var instance: BookDatabase? = null
        fun getInstance (context: Context): BookDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context, BookDatabase::class.java, "book_db"
                ).addMigrations(migration_5_6).build().also { instance = it }
            }

        private val migration_5_6 = object: Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE BookGroups ADD COLUMN itemOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE BookGroups SET itemOrder = (SELECT id From BookGroups AS bg WHERE bg.id = BookGroups.id)")
            }
        }
    }

    abstract fun bookDao (): BookDao
    abstract fun bookWithGroupDao (): BookWithGroupDao
    abstract fun groupDao (): GroupDao
}