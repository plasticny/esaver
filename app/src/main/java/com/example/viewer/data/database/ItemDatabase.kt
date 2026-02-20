package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.viewer.data.dao.item.ItemCommonCustomDao
import com.example.viewer.data.dao.item.ItemDao
import com.example.viewer.data.dao.item.ItemGroupDao
import com.example.viewer.data.dao.item.SourceDataEDao
import com.example.viewer.data.dao.item.SourceDataWnDao
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.ItemCommonCustom
import com.example.viewer.data.struct.item.ItemGroup
import com.example.viewer.data.struct.item.SourceDataE
import com.example.viewer.data.struct.item.SourceDataWn

@Database(
    entities = [
        Item::class, ItemGroup::class, ItemCommonCustom::class,
        SourceDataE::class, SourceDataWn::class
   ],
    version = 1,
    exportSchema = false
)
abstract class ItemDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var instance: ItemDatabase? = null
        fun getInstance (context: Context): ItemDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context, ItemDatabase::class.java, "item_db"
                ).build().also { instance = it }
            }
    }

    abstract fun itemDao (): ItemDao
    abstract fun itemGroupDao (): ItemGroupDao
    abstract fun itemCommonCustomDao (): ItemCommonCustomDao
    abstract fun sourceDataEDao (): SourceDataEDao
    abstract fun sourceDataWnDao (): SourceDataWnDao
}