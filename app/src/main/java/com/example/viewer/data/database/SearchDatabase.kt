package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.viewer.data.dao.ExcludeTagDao
import com.example.viewer.data.dao.SearchMarkDao
import com.example.viewer.data.struct.ExcludeTag
import com.example.viewer.data.struct.SearchMark

@Database(
    entities = [SearchMark::class, ExcludeTag::class],
    version = 1,
    exportSchema = false
)
abstract class SearchDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var instance: SearchDatabase? = null
        fun getInstance (context: Context): SearchDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context, SearchDatabase::class.java, "search_db"
                ).build().also { instance = it }
            }
    }

    abstract fun searchMarkDao (): SearchMarkDao
    abstract fun excludeTagDao (): ExcludeTagDao
}