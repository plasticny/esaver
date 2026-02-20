package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.viewer.data.dao.search.ExcludeTagDao
import com.example.viewer.data.dao.search.SearchHistoryDao
import com.example.viewer.data.dao.search.SearchMarkDao
import com.example.viewer.data.struct.search.ExcludeTag
import com.example.viewer.data.struct.search.SearchHistory
import com.example.viewer.data.struct.search.SearchMark

@Database(
    entities = [SearchMark::class, ExcludeTag::class, SearchHistory::class],
    version = 3,
    exportSchema = false
)
abstract class SearchDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var instance: SearchDatabase? = null
        fun getInstance (context: Context): SearchDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, SearchDatabase::class.java, "search_db")
                    .addMigrations(migration_1_2)
                    .addMigrations(migration_2_3)
                    .build().also { instance = it }
            }

        private val migration_1_2 = object: Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("" +
                    "CREATE TABLE SearchHistories (" +
                        "searchMarkId INTEGER PRIMARY KEY NOT NULL," +
                        "lastNext TEXT," +
                        "CONSTRAINT fk_searchMarkID " +
                            "FOREIGN KEY(searchMarkId) REFERENCES SearchMarks(id) " +
                            "ON DELETE CASCADE" +
                    ")"
                )
                db.execSQL("" +
                    "INSERT INTO SearchHistories (searchMarkId)" +
                        "SELECT id FROM SearchMarks"
                )
            }
        }

        private val migration_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SearchMarks ADD COLUMN sourceOrdinal INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    abstract fun searchMarkDao (): SearchMarkDao
    abstract fun excludeTagDao (): ExcludeTagDao
    abstract fun searchHistoryDao (): SearchHistoryDao
}