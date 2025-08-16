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
    version = 9,
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
                ).addMigrations(migration_5_6)
                    .addMigrations(migration_6_7)
                    .addMigrations(migration_7_8)
                    .addMigrations(migration_8_9)
                    .build().also { instance = it }
            }

        private val migration_5_6 = object: Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE BookGroups ADD COLUMN itemOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE BookGroups SET itemOrder = (SELECT id From BookGroups AS bg WHERE bg.id = BookGroups.id)")
            }
        }

        private val migration_6_7 = object: Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // backup relations
                db.execSQL("" +
                    "CREATE TABLE BookWithGroups_backup (" +
                        "bookId TEXT, " +
                        "groupId INTEGER" +
                    ")" +
                "")
                db.execSQL("" +
                    "INSERT INTO BookWithGroups_backup (bookId, groupId) " +
                    "SELECT bookId, groupId FROM BookWithGroups" +
                "")
                db.execSQL("DELETE FROM BookWithGroups")

                // create new book group table
                db.execSQL("" +
                    "CREATE TABLE BookGroups_new (" +
                        "id INTEGER PRIMARY KEY NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "itemOrder INTEGER UNIQUE NOT NULL" +
                    ")" +
                "")
                db.execSQL("" +
                    "INSERT INTO BookGroups_new (id, name, itemOrder) " +
                    "SELECT id, name, itemOrder FROM BookGroups" +
                "")
                db.execSQL("DROP TABLE BookGroups")
                db.execSQL("ALTER TABLE BookGroups_new RENAME TO BookGroups")
                db.execSQL("CREATE INDEX index_BookGroups_id ON BookGroups(id)")
                db.execSQL("" +
                    "CREATE UNIQUE INDEX index_BookGroups_itemOrder " +
                    "ON BookGroups(itemOrder)" +
                "")

                // insert back relationships
                db.execSQL("" +
                    "INSERT INTO BookWithGroups (bookId, groupId) " +
                    "SELECT bookId, groupId FROM BookWithGroups_backup" +
                "")
                db.execSQL("DROP TABLE BookWithGroups_backup")
            }
        }

        private val migration_7_8 = object: Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Books ADD COLUMN customTitle TEXT")
            }
        }

        private val migration_8_9 = object: Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Books ADD COLUMN coverCropPositionString TEXT DEFAULT NULL")
            }
        }
    }

    abstract fun bookDao (): BookDao
    abstract fun bookWithGroupDao (): BookWithGroupDao
    abstract fun groupDao (): GroupDao
}