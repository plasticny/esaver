package com.example.viewer.database

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.key
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.viewer.R
import java.io.File
import java.io.FileOutputStream

private const val DB_NAME = "group"
private val Context.groupDatabase: DataStore<Preferences> by preferencesDataStore(name = DB_NAME)

class GroupDatabase (context: Context) : BaseDatabase() {
    companion object {
        const val TAG = "groupDB"
        const val DEFAULT_GROUP_ID = 0

        @Volatile
        private var instance: GroupDatabase? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: GroupDatabase(context).also { instance = it }
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

    //
    // backup
    //

    fun backup (context: Context) {
        val dbFile = File("${context.filesDir}/datastore", "${DB_NAME}.preferences_pb")
        val backupFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "eSaver")
        if (!backupFolder.exists()) {
            backupFolder.mkdirs()
        }

        val backupFile = File(backupFolder, "group")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        dbFile.copyTo(backupFile)
    }

    fun importDb (context: Context, uri: Uri) {
        val folder = File("${context.filesDir}/datastore")
        val dbFile = File(folder, "${DB_NAME}.preferences_pb")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        if (!dbFile.exists()) {
            dbFile.createNewFile()
        }

        FileOutputStream(dbFile).use { fos ->
            context.contentResolver.openInputStream(uri)?.use { ins ->
                fos.write(ins.readAllBytes())
            }
        }
    }
}