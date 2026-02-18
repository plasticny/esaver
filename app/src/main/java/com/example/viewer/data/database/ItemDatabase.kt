package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.viewer.data.dao.item.ItemCommonCustomDao
import com.example.viewer.data.dao.item.ItemDao
import com.example.viewer.data.dao.item.ItemGroupDao
import com.example.viewer.data.dao.item.SourceDataEDao
import com.example.viewer.data.dao.item.SourceDataRuDao
import com.example.viewer.data.dao.item.SourceDataWnDao
import com.example.viewer.data.struct.item.Item
import com.example.viewer.data.struct.item.ItemCommonCustom
import com.example.viewer.data.struct.item.ItemGroup
import com.example.viewer.data.struct.item.SourceDataE
import com.example.viewer.data.struct.item.SourceDataRu
import com.example.viewer.data.struct.item.SourceDataWn

@Database(
    entities = [
        Item::class, ItemGroup::class, ItemCommonCustom::class,
        SourceDataE::class, SourceDataWn::class, SourceDataRu::class
   ],
    version = 2,
    exportSchema = false
)
abstract class ItemDatabase: RoomDatabase() {
    companion object {
        @Volatile
        private var instance: ItemDatabase? = null
        fun getInstance (context: Context): ItemDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(context, ItemDatabase::class.java, "item_db")
                    .addMigrations(migration_1_2)
                    .build().also { instance = it }
            }

        private val migration_1_2 = object: Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("" +
                    "CREATE TABLE SourceDataRus (" +
                        "internalId INTEGER PRIMARY KEY NOT NULL," +
                        "videoId TEXT NOT NULL," +
                        "videoUrl TEXT NOT NULL," +
                        "uploader TEXT NOT NULL," +
                        "title TEXT NOT NULL," +
                        "tagsJson TEXT NOT NULL," +
                        "CONSTRAINT fk_internalId " +
                            "FOREIGN KEY(internalId) REFERENCES Items(id) " +
                            "ON DELETE CASCADE" +
                    ")"
                )
            }
        }
    }

    abstract fun itemDao (): ItemDao
    abstract fun itemGroupDao (): ItemGroupDao
    abstract fun itemCommonCustomDao (): ItemCommonCustomDao
    abstract fun sourceDataEDao (): SourceDataEDao
    abstract fun sourceDataWnDao (): SourceDataWnDao
    abstract fun sourceDataRuDao (): SourceDataRuDao
}