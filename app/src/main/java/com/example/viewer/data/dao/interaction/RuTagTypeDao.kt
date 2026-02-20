package com.example.viewer.data.dao.interaction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.viewer.data.struct.interaction.RuTagType

@Dao
interface RuTagTypeDao {
    @Insert
    suspend fun insert (v: RuTagType): Long

    @Query("SELECT * FROM RuTagTypes")
    suspend fun queryAll (): List<RuTagType>
}