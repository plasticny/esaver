package com.example.viewer.data.repository

import android.content.Context
import androidx.room.Transaction
import com.example.viewer.R
import com.example.viewer.data.dao.BookWithGroupDao
import com.example.viewer.data.dao.GroupDao
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.struct.Book
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.data.struct.Group
import kotlinx.coroutines.runBlocking

class GroupRepository (context: Context) {
    companion object {
        const val DEFAULT_GROUP_ID = 0
        private var latestUpdateTime = 0L
    }

    private val groupDao: GroupDao
    private val bookWithGroupDao: BookWithGroupDao

    init {
        BookDatabase.getInstance(context).run {
            groupDao = this.groupDao()
            bookWithGroupDao = this.bookWithGroupDao()
        }
        confirmDefaultGroupExist(context)
    }

    fun addGroupFromPreference (
        id: Int, name: String
    ) {
        if (id == 0) {
            throw Exception("do not insert group 0")
        }
        runBlocking {
            groupDao.insert(Group(id, name))
        }
    }

    fun getAllGroupIds (): List<Int> = runBlocking { groupDao.queryAllIds() }

    fun getGroupName (id: Int): String = runBlocking { groupDao.queryName(id) }

    fun getGroupBookIds (id: Int): List<String> =
        runBlocking { bookWithGroupDao.queryBookIds(id) }

    fun getLastUpdateTime (): Long = latestUpdateTime

    fun getGroupIdFromName (name: String): Int? =
        runBlocking { groupDao.queryId(name) }

    @Transaction
    fun changeGroup (bookId: String, oldGroupId: Int, newGroupId: Int) = runBlocking {
        bookWithGroupDao.updateGroupId(bookId, newGroupId)
        if (oldGroupId != DEFAULT_GROUP_ID && getGroupBookIds(oldGroupId).isEmpty()) {
            removeGroup(oldGroupId)
        }
    }

    fun addBookIdToGroup (groupId: Int, bookId: String) = runBlocking {
        bookWithGroupDao.insert(BookWithGroup(bookId, groupId))
        latestUpdateTime = System.currentTimeMillis()
    }

    fun createGroup (name: String): Int {
        val dao = groupDao
        val id = runBlocking {
            val id = dao.getNextId()
            dao.insert(Group(id, name))
            id
        }
        latestUpdateTime = System.currentTimeMillis()
        return id
    }

    private fun removeGroup (id: Int) {
        if (id == 0) {
            throw Exception("cannot remove default group")
        }
        runBlocking {
            if (bookWithGroupDao.countByGroupId(id) != 0) {
                throw Exception("Cannot delete group if some book in the group")
            }
            groupDao.delete(id)
        }
        latestUpdateTime = System.currentTimeMillis()
    }

    private fun confirmDefaultGroupExist (context: Context) {
        val dao = groupDao
        runBlocking {
            if (dao.countId(0) == 0) {
                dao.insert(Group(0,  context.getString(R.string.noGroup)))
            }
        }
    }
}