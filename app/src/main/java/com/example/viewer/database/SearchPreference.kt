package com.example.viewer.database

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.viewer.Util
import com.example.viewer.data.repository.ExcludeTagRepository
import com.example.viewer.data.repository.SearchRepository
import com.example.viewer.struct.Category
import com.example.viewer.struct.ExcludeTagRecord
import com.example.viewer.struct.SearchMark

typealias Tags = Map<String, List<String>>

private const val DB_NAME = "search"
private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = DB_NAME)

class SearchPreference (context: Context): BaseDatabase() {
    companion object {
        const val NAME = DB_NAME
        const val TAG = "searchDB"
        const val TEMP_SEARCH_MARK_ID = -1

        @Volatile
        private var instance: SearchPreference? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: SearchPreference(context).also { instance = it }
        }
    }

    override val dataStore = context.searchDataStore

    private val keys = object {
        fun nextSearchMarkId () = intPreferencesKey("${TAG}_nextSearchMarkId")
        /**
         * search mark with id -1 is temporary search mark
         */
        fun allSearchMarkIds () = CustomPreferencesKey<List<Int>>("${TAG}_searchMarkIds")

        fun searchMarkName (id: Int) = stringPreferencesKey("${TAG}_searchMarkName_$id")
        /**
         * the category object is saved as its ordinal
         */
        fun searchMarkCats (id: Int) = CustomPreferencesKey<List<Int>>("${TAG}_searchMarkCats_$id")
        fun searchMarkKeyword (id: Int) = stringPreferencesKey("${TAG}_searchMarkKeyword_$id")
        fun searchMarkTags (id: Int) = CustomPreferencesKey<Tags>("${TAG}_searchMarkTags_$id")
        fun searchMarkUploader (id: Int) = stringPreferencesKey("${TAG}_searchMarkUploader_$id")
        // do apply exclude tag
        fun searchMarkDoExclude (id: Int) = booleanPreferencesKey("${TAG}_searchMarkDoExclude_$id")
        fun searchMarkListLastUpdate () = longPreferencesKey("${TAG}_searchMarkListLastUpdate")

        fun nextExcludeTagId () = intPreferencesKey("${TAG}_nextExcludeTagId")
        fun allExcludeTagIds () = CustomPreferencesKey<List<Int>>("${TAG}_excludeTagIds")
        fun excludeTagTags (id: Int) = CustomPreferencesKey<Tags>("${TAG}_excludeTagTags_$id")
        fun excludeTagCats (id: Int) = CustomPreferencesKey<Set<Int>>("${TAG}_excludeTagCats_$id")
        fun excludeTagLastUpdate () = longPreferencesKey("${TAG}_excludeTagLastUpdate")
    }

    fun syncToRoom (context: Context) {
        val searchRepo = SearchRepository(context)
        for (id in getAllSearchMarkIds()) {
            getSearchMark(id).run {
                searchRepo.addSearchMark(
                    name = name,
                    categories = categories,
                    keyword = keyword,
                    tags = tags,
                    uploader = uploader.ifEmpty { null },
                    doExclude = doExclude
                )
            }
        }
        val excludeRepo = ExcludeTagRepository(context)
        for (item in getAllExcludeTag()) {
            item.second.run {
                excludeRepo.addExcludeTag(
                    tags = tags,
                    categories = categories.toList()
                )
            }
        }
    }

    //
    // search mark
    //

    /**
     * get all search mark id in the dataset
     *
     * @return a list of search mark id which also reflects the sorting arrangement of user
     */
    fun getAllSearchMarkIds () = read(keys.allSearchMarkIds()) ?: listOf()
    fun moveSearchMarkPosition (id: Int, toId: Int) {
        val ids = getAllSearchMarkIds().toMutableList()
        val from = ids.indexOf(id)
        val to = ids.indexOf(toId)
        if (from == -1 || to == -1) {
            throw Exception("invalid search mark id $id")
        }
        ids.removeAt(from)
        ids.add(to, id)
        store(keys.allSearchMarkIds(), ids)
    }
    fun removeSearchMark (id: Int) {
        if (!isKeyExist(keys.searchMarkName(id))) {
            throw Exception("no search mark with id $id")
        }
        remove(keys.searchMarkName(id))
        remove(keys.searchMarkCats(id))
        remove(keys.searchMarkKeyword(id))
        remove(keys.searchMarkTags(id))
        remove(keys.searchMarkUploader(id))
        remove(keys.searchMarkDoExclude(id))
        store(
            keys.allSearchMarkIds(),
            getAllSearchMarkIds().toMutableList().also { it.remove(id) }
        )

        store(keys.searchMarkListLastUpdate(), System.currentTimeMillis())
    }
    /**
     * @return id of added search mark
     */
    fun addSearchMark (searchMark: SearchMark): Int {
        val id = getNextSearchMarkId()
        storeSearchMark(id, searchMark)
        store(keys.allSearchMarkIds(), getAllSearchMarkIds().toMutableList().apply { add(id) })
        store(keys.searchMarkListLastUpdate(), System.currentTimeMillis())
        return id
    }
    fun getSearchMark (id: Int): SearchMark = SearchMark(
        name = read(keys.searchMarkName(id))!!,
        categories = read(keys.searchMarkCats(id))!!.map { Util.categoryFromOrdinal(it) },
        keyword = read(keys.searchMarkKeyword(id)) ?: "",
        tags = read(keys.searchMarkTags(id))!!,
        uploader = read(keys.searchMarkUploader(id)) ?: "",
        doExclude = read(keys.searchMarkDoExclude(id)) == true
    )
    fun modifySearchMark (id: Int, searchMark: SearchMark) {
        if (!isKeyExist(keys.searchMarkName(id))) {
            throw Exception("no search mark with id $id")
        }
        storeSearchMark(id, searchMark)
        store(keys.searchMarkListLastUpdate(), System.currentTimeMillis())
    }
    fun setTmpSearchMark (searchMark: SearchMark) = storeSearchMark(TEMP_SEARCH_MARK_ID, searchMark)
    /**
     * update: insert, remove, or modify
     */
    fun getSearchMarkListUpdateTime () = read(keys.searchMarkListLastUpdate()) ?: 0
    private fun storeSearchMark (id: Int, searchMark: SearchMark) {
        store(keys.searchMarkName(id), searchMark.name)
        store(keys.searchMarkCats(id), searchMark.categories.map { it.ordinal })
        store(keys.searchMarkKeyword(id), searchMark.keyword)
        store(keys.searchMarkTags(id), searchMark.tags)
        store(keys.searchMarkUploader(id), searchMark.uploader)
        store(keys.searchMarkDoExclude(id), searchMark.doExclude)
    }
    private fun getNextSearchMarkId (): Int {
        val id = read(keys.nextSearchMarkId()) ?: 1
        store(keys.nextSearchMarkId(), id + 1)
        return id
    }

    //
    // exclude tag
    //
    fun getAllExcludeTag (): List<Pair<Int, ExcludeTagRecord>> {
        val ids = read(keys.allExcludeTagIds()) ?: listOf()
        return ids.map { id ->
            id to ExcludeTagRecord(
                read(keys.excludeTagTags(id))!!,
                read(keys.excludeTagCats(id))!!.map {
                        ordinal -> Category.entries[ordinal]
                }.toSet()
            )
        }
    }
    fun getExcludeTag (id: Int) =
        ExcludeTagRecord(
            read(keys.excludeTagTags(id))!!,
            read(keys.excludeTagCats(id))!!.map {
                    ordinal -> Category.entries[ordinal]
            }.toSet()
        )
    fun addExcludeTag (excludeTagRecord: ExcludeTagRecord) {
        val id = read(keys.nextExcludeTagId()) ?: 1
        store(keys.nextExcludeTagId(), id + 1)
        store(
            keys.allExcludeTagIds(),
            getAllExcludeIds().toMutableList().apply { add(id) }
        )
        storeExcludeTag(id, excludeTagRecord)
    }
    fun modifyExcludeTag (id: Int, excludeTagRecord: ExcludeTagRecord) =
        storeExcludeTag(id, excludeTagRecord)
    fun lastExcludeTagUpdateTime () = read(keys.excludeTagLastUpdate()) ?: 0
    private fun getAllExcludeIds () = read(keys.allExcludeTagIds()) ?: listOf()
    private fun storeExcludeTag (id: Int, excludeTagRecord: ExcludeTagRecord) {
        store(keys.excludeTagTags(id), excludeTagRecord.tags)
        store(keys.excludeTagCats(id), excludeTagRecord.categories.map { it.ordinal }.toSet())
        store(keys.excludeTagLastUpdate(), System.currentTimeMillis())
    }
}