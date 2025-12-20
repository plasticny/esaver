package com.example.viewer.data.repository

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Transaction
import com.example.viewer.R
import com.example.viewer.data.dao.BookWithGroupDao
import com.example.viewer.data.dao.GroupDao
import com.example.viewer.data.database.BookDatabase
import com.example.viewer.data.struct.BookWithGroup
import com.example.viewer.data.struct.Group
import kotlinx.coroutines.runBlocking

class GroupRepository (context: Context) {
    companion object {
        const val DEFAULT_GROUP_ID = 0
        private var latestUpdateTime = 0L
        fun getLastUpdateTime (): Long = latestUpdateTime
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
            throw IllegalArgumentException("do not insert group 0")
        }
        runBlocking {
            groupDao.insert(id, name)
        }
    }

    fun getAllGroupIdsInOrder (): List<Int> = runBlocking { groupDao.queryAllIdsInOrder() }

    fun getAllGroupsInOrder (): List<Group> = runBlocking { groupDao.queryAllInOrder() }

    fun getGroupName (id: Int): String = runBlocking { groupDao.queryName(id) }

    fun getGroupBookIdentifies (id: Int): List<BookWithGroup.Companion.BookIdentify> =
        runBlocking { bookWithGroupDao.queryBookIdentifyInGroup(id) }

    fun getLastUpdateTime (): Long = latestUpdateTime

    fun getGroupIdFromName (name: String): Int? =
        runBlocking { groupDao.queryId(name) }

    @Transaction
    fun changeGroup (bookId: String, oldGroupId: Int, newGroupId: Int) = runBlocking {
        bookWithGroupDao.updateGroupId(bookId, newGroupId)
        if (oldGroupId != DEFAULT_GROUP_ID && getGroupBookIdentifies(oldGroupId).isEmpty()) {
            removeGroup(oldGroupId)
        }
    }

    fun changeGroupName (id: Int, name: String) = runBlocking {
        groupDao.updateName(id, name)
    }

    fun addBookIdToGroup (groupId: Int, bookId: String, sourceOrdinal: Int) = runBlocking {
        bookWithGroupDao.insert(BookWithGroup(bookId, sourceOrdinal, groupId))
        latestUpdateTime = System.currentTimeMillis()
    }

    fun createGroup (name: String): Int {
        val dao = groupDao
        val id = runBlocking {
            val id = dao.getNextId()
            dao.insert(id, name)
            id
        }
        latestUpdateTime = System.currentTimeMillis()
        return id
    }

    fun moveGroupBefore (id: Int, toId: Int) = runBlocking {
        moveGroup(groupDao.queryItemOrder(id), groupDao.queryItemOrder(toId))
    }

    fun moveGroupAfter (id: Int, toId: Int) = runBlocking {
        val from = groupDao.queryItemOrder(id)
        val to = groupDao.queryItemOrder(toId)
        if (from == to) {
            return@runBlocking
        }
        moveGroup(from, to)
    }

    fun removeIfEmpty (id: Int) = runBlocking {
        if (id == DEFAULT_GROUP_ID) {
            return@runBlocking
        }
        if(getGroupBookIdentifies(id).isEmpty()) {
            removeGroup(id)
        }
    }

    @Transaction
    private suspend fun moveGroup (fromOrder: Int, toOrder: Int) {
        if (fromOrder == toOrder) {
            throw IllegalArgumentException("fromOrder == toOrder, something went wrong")
        }

        groupDao.updateItemOrderByOrder(fromOrder, -1)
        try {
            if (fromOrder < toOrder) {
                for (order in fromOrder + 1 .. toOrder) {
                    groupDao.updateItemOrderByOrder(order, order - 1)
                }
            } else {
                for (order in (toOrder until fromOrder).reversed()) {
                    groupDao.updateItemOrderByOrder(order, order + 1)
                }
            }
        } catch (e: SQLiteConstraintException) {
            groupDao.updateItemOrderByOrder(-1, fromOrder)
            throw e
        }
        groupDao.updateItemOrderByOrder(-1, toOrder)

        latestUpdateTime = System.currentTimeMillis()
    }

    @Transaction
    private fun removeGroup (id: Int) {
        if (id == 0) {
            throw IllegalArgumentException("cannot remove default group")
        }
        runBlocking {
            if (bookWithGroupDao.countByGroupId(id) != 0) {
                throw IllegalStateException("Cannot delete group if some book in the group")
            }
            val deletedOrder = groupDao.queryItemOrder(id)
            groupDao.delete(id)
            for (order in deletedOrder + 1 .. groupDao.getMaxItemOrder()) {
                groupDao.decreaseItemOrder(order)
            }
        }
        latestUpdateTime = System.currentTimeMillis()
    }

    private fun confirmDefaultGroupExist (context: Context) {
        val dao = groupDao
        runBlocking {
            if (dao.countId(0) == 0) {
                dao.insert(0,  context.getString(R.string.noGroup))
            }
        }
    }
}