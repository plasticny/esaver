package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.viewer.data.dao.interaction.RuTagDao
import com.example.viewer.data.dao.interaction.RuTagTypeDao
import com.example.viewer.data.struct.interaction.RuTag
import com.example.viewer.data.struct.interaction.RuTagType

@Database(
    entities = [
        RuTag::class, RuTagType::class
    ],
    version = 1,
    exportSchema = false
)
abstract class InteractionDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var instance: InteractionDatabase? = null
        fun getInstance (context: Context): InteractionDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, InteractionDatabase::class.java, "interaction_db")
                    .build().also { instance = it }
            }
    }

    abstract fun ruTagDao (): RuTagDao

    abstract fun ruTagTypeDao (): RuTagTypeDao
}