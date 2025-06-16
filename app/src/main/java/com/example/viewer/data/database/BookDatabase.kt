package com.example.viewer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.viewer.data.dao.BookDao
import com.example.viewer.data.dao.BookWithGroupDao
import com.example.viewer.data.dao.GroupDao
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.data.struct.Group

@Database (
    entities = [Book::class, BookWithGroup::class, Group::class],
    version = 5,
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
                ).fallbackToDestructiveMigration(false).build().also { instance = it }
            }
    }

    abstract fun bookDao (): BookDao
    abstract fun bookWithGroupDao (): BookWithGroupDao
    abstract fun groupDao (): GroupDao
}