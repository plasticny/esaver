package com.example.viewer.dataset

import android.content.Context
import android.nfc.Tag
import android.os.Environment
import androidx.compose.runtime.key
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.viewer.R
import java.io.File
import java.io.Serializable

typealias Tags = Map<String, List<String>>

private const val DB_NAME = "search"
private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = DB_NAME)

class SearchDataset (context: Context): BaseDataset() {
    companion object {
        const val TAG = "searchDB"

        @Volatile
        private var instance: SearchDataset? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: SearchDataset(context).also { instance = it }
        }

        data class SearchMark (
            val name: String,
            val categories: List<Category>,
            val keyword: String,
            val tags: Tags
        )

        // NOTE: be very careful on arrange the order of entries
        enum class Category {
            Doujinshi {
                override val color = R.color.doujinshi_red
                override val value = 2
            },
            Manga {
                override val color = R.color.manga_orange
                override val value = 4
            },
            ArtistCG {
                override val color = R.color.artistCG_yellow
                override val value = 8
            },
            NonH {
                override val color = R.color.nonH_blue
                override val value = 256
            };

            abstract val color: Int
            abstract val value: Int

            companion object {
                fun fromString (string: String): Category = when (string) {
                    "Doujinshi" -> categoryEntries[0]
                    "Manga" -> categoryEntries[1]
                    "Artist CG" -> categoryEntries[2]
                    "Non-H" -> categoryEntries[3]
                    else -> throw Exception("unexpected string $string")
                }
            }
        }
        private val categoryEntries = Category.entries
    }

    override val dataStore = context.searchDataStore

    private val keys = object {
        fun nextId () = intPreferencesKey("${TAG}_nextSearchMarkId")
        fun allSearchMarkIds () = byteArrayPreferencesKey("${TAG}_searchMarkIds")
        fun searchMarkName (id: Int) = stringPreferencesKey("${TAG}_searchMarkName_$id")
        /**
         * list of integer, the category object is saved as its ordinal
         */
        fun searchMarkCats (id: Int) = byteArrayPreferencesKey("${TAG}_searchMarkCats_$id")
        fun searchMarkKeyword (id: Int) = stringPreferencesKey("${TAG}_searchMarkKeyword_$id")
        fun searchMarkTags (id: Int) = byteArrayPreferencesKey("${TAG}_searchMarkTags_$id")
        fun excludeTags () = byteArrayPreferencesKey("${TAG}_excludeTags")
    }

    private fun getNextId (): Int {
        val id = read(keys.nextId()) ?: 1
        store(keys.nextId(), id + 1)
        return id
    }

    /**
     * get all search mark id in the dataset
     *
     * @return a list of search mark id which also reflects the sorting arrangement of user
     */
    fun getAllSearchMarkIds () = readFromByteArray<List<Int>>(keys.allSearchMarkIds()) ?: listOf()
    fun moveSearchMarkPosition (id: Int, toId: Int) {
        val ids = getAllSearchMarkIds().toMutableList()
        val from = ids.indexOf(id)
        val to = ids.indexOf(toId)
        if (from == -1 || to == -1) {
            throw Exception("invalid search mark id $id")
        }
        ids.removeAt(from)
        ids.add(to, id)
        storeAsByteArray(keys.allSearchMarkIds(), ids)
    }

    //
    // search mark
    //
    private fun storeSearchMark (id: Int, searchMark: SearchMark) {
        store(keys.searchMarkName(id), searchMark.name)
        storeAsByteArray(keys.searchMarkCats(id), searchMark.categories.map { it.ordinal })
        store(keys.searchMarkKeyword(id), searchMark.keyword)
        storeAsByteArray(keys.searchMarkTags(id), searchMark.tags)
    }
    fun removeSearchMark (id: Int) {
        if (!isKeyExist(keys.searchMarkName(id))) {
            throw Exception("no search mark with id $id")
        }
        remove(keys.searchMarkName(id))
        remove(keys.searchMarkCats(id))
        remove(keys.searchMarkKeyword(id))
        remove(keys.searchMarkTags(id))
        storeAsByteArray(
            keys.allSearchMarkIds(),
            getAllSearchMarkIds().toMutableList().also { it.remove(id) }
        )
    }
    fun addSearchMark (searchMark: SearchMark): Int {
        val id = getNextId()
        storeSearchMark(id, searchMark)
        storeAsByteArray(keys.allSearchMarkIds(), getAllSearchMarkIds().toMutableList().apply { add(id) })
        return id
    }
    fun getSearchMark (id: Int): SearchMark = SearchMark(
        name = read(keys.searchMarkName(id))!!,
        categories = readFromByteArray<List<Int>>(keys.searchMarkCats(id))!!.map { categoryEntries[it] },
        keyword = read(keys.searchMarkKeyword(id)) ?: "",
        tags = readFromByteArray<Tags>(keys.searchMarkTags(id))!!
    )
    fun modifySearchMark (id: Int, searchMark: SearchMark) {
        if (!isKeyExist(keys.searchMarkName(id))) {
            throw Exception("no search mark with id $id")
        }
        storeSearchMark(id, searchMark)
    }

    //
    // exclude tag
    //
    fun getExcludeTag () = readFromByteArray<Tags>(keys.excludeTags()) ?: mapOf()
    fun storeExcludeTag (v: Tags) = storeAsByteArray(keys.excludeTags(), v)

    //
    // backup
    //
    fun backup (context: Context) {
        val dbFile = File("${context.filesDir}/datastore", "${DB_NAME}.preferences_pb")
        val backupFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "eSaver")
        if (!backupFolder.exists()) {
            backupFolder.mkdirs()
        }

        val backupFile = File(backupFolder, "search")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        dbFile.copyTo(backupFile)
    }
}