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
    version = 10,
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
                    .addMigrations(migration_9_10)
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

        private val migration_9_10 = object: Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // books
                db.execSQL("" +
                    "CREATE TABLE Books_new (" +
                        "id TEXT NOT NULL, " +
                        "sourceOrdinal INTEGER NOT NULL, " +
                        "url TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "subTitle TEXT NOT NULL, " +
                        "pageNum INTEGER NOT NULL, " +
                        "categoryOrdinal INTEGER NOT NULL, " +
                        "uploader TEXT, " +
                        "tagsJson TEXT NOT NULL, " +
                        "coverPage INTEGER NOT NULL, " +
                        "skipPagesJson TEXT NOT NULL, " +
                        "lastViewTime INTEGER NOT NULL, " +
                        "bookMarksJson TEXT NOT NULL, " +
                        "customTitle TEXT, " +
                        "coverCropPositionString TEXT, " +
                        "pageUrlsJson TEXT, " +
                        "p INTEGER, " +
                        "PRIMARY KEY(id, sourceOrdinal)" +
                    ")" +
                "")
                db.execSQL("" +
                    "INSERT INTO Books_new (" +
                        "id, sourceOrdinal, url, title, subTitle, pageNum, categoryOrdinal, " +
                        "uploader, tagsJson, coverPage, skipPagesJson, lastViewTime, bookMarksJson, " +
                        "customTitle, coverCropPositionString, pageUrlsJson, p" +
                    ") " +
                    "SELECT " +
                        "id, sourceOrdinal, url, title, subTitle, pageNum, categoryOrdinal, " +
                        "uploader, tagsJson, coverPage, skipPagesJson, lastViewTime, bookMarksJson, " +
                        "customTitle, coverCropPositionString, pageUrlsJson, p " +
                    "FROM Books" +
                "")

                db.execSQL("DROP TABLE Books")
                db.execSQL("ALTER TABLE Books_new RENAME TO Books")

                db.execSQL("CREATE INDEX index_Books_id ON Books(id)")

                // bookWithGroups
                db.execSQL("" +
                    "CREATE TABLE BookWithGroups_new (" +
                        "bookId TEXT NOT NULL, " +
                        "bookSourceOrdinal INTEGER NOT NULL, " +
                        "groupId INTEGER NOT NULL, " +
                        "PRIMARY KEY(bookId, bookSourceOrdinal), " +
                        "FOREIGN KEY(groupId) REFERENCES BookGroups(id) ON DELETE RESTRICT, " +
                        "FOREIGN KEY(bookId, bookSourceOrdinal) REFERENCES Books(id, sourceOrdinal) ON DELETE CASCADE" +
                    ")" +
                "")
                db.execSQL("" +
                    "INSERT INTO " +
                        "BookWithGroups_new (bookId, bookSourceOrdinal, groupId) " +
                    "SELECT " +
                        "bookId, " +
                        "(SELECT sourceOrdinal FROM Books WHERE bookId = id), " +
                        "groupId FROM BookWithGroups" +
                "")

                db.execSQL("DROP TABLE BookWithGroups")
                db.execSQL("ALTER TABLE BookWithGroups_new RENAME TO BookWithGroups")

                db.execSQL("CREATE INDEX index_BookWithGroups_groupId ON BookWithGroups(groupId)")

                // remove starting
                db.execSQL("UPDATE BookWithGroups SET bookId = substr(bookId, 3) WHERE bookId LIKE 'wn%'")
                db.execSQL("UPDATE Books SET id = substr(id, 3) WHERE id LIKE 'wn%'")
            }
        }
    }

    abstract fun bookDao (): BookDao
    abstract fun bookWithGroupDao (): BookWithGroupDao
    abstract fun groupDao (): GroupDao
}