package com.example.viewer.database

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.viewer.R
import com.example.viewer.data.repository.GroupRepository

private const val DB_NAME = "group"
private val Context.groupDatabase: DataStore<Preferences> by preferencesDataStore(name = DB_NAME)

class GroupPreferences (context: Context) : BaseDatabase() {
    companion object {
        const val NAME = DB_NAME
        const val TAG = "groupDB"
        const val DEFAULT_GROUP_ID = 0

        @Volatile
        private var instance: GroupPreferences? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: GroupPreferences(context).also { instance = it }
        }

        private var defaultGroupChecked = false
    }

    override val dataStore = context.groupDatabase

    private val keys = object {
        fun allGroupIds () = CustomPreferencesKey<List<Int>>("${TAG}_groupIds")
        fun nextGroupId () = intPreferencesKey("${TAG}_nextGroupId")
        fun groupName (id: Int) = stringPreferencesKey("${TAG}_groupName_$id")
        fun groupBookIds (id: Int) = CustomPreferencesKey<List<String>>("${TAG}_groupBookIds_$id")
        fun groupListUpdateTime () = longPreferencesKey("${TAG}_groupListUpdateTime")
    }

    init {
        // ensure the default group exist
        if (!defaultGroupChecked) {
            if (getAllGroupIds().isEmpty()) {
                createGroup(ContextCompat.getString(context, R.string.noGroup))
            }
            defaultGroupChecked = true
        }
    }

    fun syncToRoom (context: Context) {
        val roomRp = GroupRepository(context)
        for (id in getAllGroupIds()) {
            if (id == 0) {
                continue
            }
            println(id)
            roomRp.addGroupFromPreference(id, getGroupName(id))
        }
    }

    fun getAllGroupIds (): List<Int> = read(keys.allGroupIds()) ?: listOf()

    fun getGroupName (id: Int): String = read(keys.groupName(id))!!

    fun getGroupBookIds (groupId: Int): List<String> = read(keys.groupBookIds(groupId)) ?: listOf()

    fun getLastUpdateTime (): Long = read(keys.groupListUpdateTime()) ?: 0

    /**
     * @return group id if found, else null
     */
    fun getGroupIdFromName (name: String): Int? =
        getAllGroupIds().firstOrNull { name == getGroupName(it) }

    fun changeGroup (bookId: String, oldGroupId: Int, newGroupId: Int) {
        if (oldGroupId == newGroupId) {
            return
        }
        addBookIdToGroup(newGroupId, bookId)
        removeBookIdFromGroup(oldGroupId, bookId)
    }

    fun addBookIdToGroup (groupId: Int, bookId: String) {
        assertGroupIdExist(groupId)

        val bookIds = read(keys.groupBookIds(groupId)) ?: listOf()
        if (bookIds.contains(bookId)) {
            throw Exception("bookId $bookId already exist in the group with id $groupId")
        }
        store(
            keys.groupBookIds(groupId),
            bookIds.toMutableList().apply { add(bookId) }
        )
        store(keys.groupListUpdateTime(), System.currentTimeMillis())
    }

    fun removeBookIdFromGroup (groupId: Int, bookId: String) {
        assertGroupIdExist(groupId)

        val bookIds = read(keys.groupBookIds(groupId))?.toMutableList() ?: mutableListOf()
        if (!bookIds.contains(bookId)) {
            throw Exception("bookId $bookId is not in the group with id $groupId")
        }

        bookIds.remove(bookId)
        if (groupId != DEFAULT_GROUP_ID && bookIds.isEmpty()) {
            removeGroup(groupId)
        }
        store(keys.groupBookIds(groupId), bookIds)
        store(keys.groupListUpdateTime(), System.currentTimeMillis())
    }

    /**
     * @return new group id
     */
    fun createGroup (name: String): Int {
        val id = read(keys.nextGroupId()) ?: 0
        store(
            keys.allGroupIds(),
            getAllGroupIds().toMutableList().apply { add(id) }
        )
        store(keys.groupName(id), name)
        store(keys.nextGroupId(), id + 1)

        store(keys.groupListUpdateTime(), System.currentTimeMillis())

        return id
    }

    private fun removeGroup (id: Int) {
        if (id == 0) {
            throw Exception("cannot remove default group")
        }

        assertGroupIdExist(id)

        remove(keys.groupName(id))
        remove(keys.groupBookIds(id))

        store(
            keys.allGroupIds(),
            getAllGroupIds().toMutableList().apply { remove(id) }
        )

        store(keys.groupListUpdateTime(), System.currentTimeMillis())
    }

    private fun assertGroupIdExist (id: Int) {
        if (!getAllGroupIds().contains(id)) {
            throw Exception("group with id $id not exist")
        }
    }
}