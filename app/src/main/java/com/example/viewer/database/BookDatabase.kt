package com.example.viewer.database

import android.content.Context
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.viewer.struct.BookRecord
import com.example.viewer.Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

enum class BookSource (val keyString: String) {
    E("E"),
    Hi("Hi")
}

private const val DB_NAME = "book"
private val Context.bookDataStore: DataStore<Preferences> by preferencesDataStore(name = DB_NAME)

class BookDatabase (context: Context): BaseDatabase() {
    companion object {
        const val NO_AUTHOR = "NoAuthor"

        @Volatile
        private var instance: BookDatabase? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: BookDatabase(context).also { instance = it }
        }
    }

    override val dataStore = context.bookDataStore

    //
    // store keys
    //
    private val storeKeys = object {
        // --------------
        // books
        fun allBookIds () = CustomPreferencesKey<List<String>>("bookIds")
        // individual book
        fun bookUrl (bookId: String): Preferences.Key<String> {
            assertBookIdExist(bookId)
            return stringPreferencesKey("${bookId}_url")
        }
        fun bookTitle (bookId: String): Preferences.Key<String> {
            assertBookIdExist(bookId)
            return stringPreferencesKey("${bookId}_title")
        }
        fun bookSubTitle (bookId: String): Preferences.Key<String> {
            assertBookIdExist(bookId)
            return stringPreferencesKey("${bookId}_subTitle")
        }
        fun bookPageUrls (bookId: String): CustomPreferencesKey<List<String>> {
            assertBookIdExist(bookId)
            return CustomPreferencesKey("${bookId}_pageUrls")
        }
        fun bookPageNum (bookId: String): Preferences.Key<Int> {
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_pageNum")
        }
        fun bookCatOrdinal (bookId: String): Preferences.Key<Int> {
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_catOrdinal")
        }
        fun bookTags (bookId: String): CustomPreferencesKey<Map<String, List<String>>> {
            assertBookIdExist(bookId)
            return CustomPreferencesKey("${bookId}_tags")
        }
        fun bookSource (bookId: String): Preferences.Key<String> {
            assertBookIdExist(bookId)
            return stringPreferencesKey("${bookId}_source")
        }
        fun bookCoverPage (bookId: String): Preferences.Key<Int> {
            // 0 for first page, pageNum - 1 for last page
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_coverPage")
        }
        fun bookP (bookId: String): Preferences.Key<Int> {
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_P")
        }
        fun bookSkipPages (bookId: String): CustomPreferencesKey<List<Int>> {
            // 0 for first page, pageNum - 1 for last page
            assertBookIdExist(bookId)
            return CustomPreferencesKey("${bookId}_skipPages")
        }
        fun bookLastViewTime (bookId: String): Preferences.Key<Long> {
            assertBookIdExist(bookId)
            return longPreferencesKey("${bookId}_lastViewTime")
        }
        fun bookGroupId (bookId: String): Preferences.Key<Int> {
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_groupId")
        }
        fun bookUploader (bookId: String): Preferences.Key<String> {
            assertBookIdExist(bookId)
            return stringPreferencesKey("${bookId}_uploader")
        }
        /**
         * store page of the book mark, start from 0
         */
        fun bookBookMarks (bookId: String): CustomPreferencesKey<List<Int>> {
            assertBookIdExist(bookId)
            return CustomPreferencesKey("${bookId}_bookmarks")
        }
    }

    //
    // public methods
    //

    fun addBook (
        id: String,
        url: String,
        category: SearchDatabase.Companion.Category,
        title: String,
        subtitle: String = "",
        pageNum: Int,
        tags: Map<String, List<String>>,
        source: BookSource,
        groupId: Int,
        uploader: String?
    ) {
        if (pageNum < 1) {
            throw Exception("Invalid pageNum $pageNum")
        }

        // id
        addBookId(id)
        // url
        store(storeKeys.bookUrl(id), url)
        // cover
        setBookCoverPage(id, 0)
        // category
        store(storeKeys.bookCatOrdinal(id), category.ordinal)
        // title
        store(storeKeys.bookTitle(id), title)
        // subtitle
        if (subtitle.isNotEmpty()) {
            store(storeKeys.bookSubTitle(id), subtitle)
        }
        // total page
        store(storeKeys.bookPageNum(id), pageNum)
        // tags
        store(storeKeys.bookTags(id), tags)
        // group id
        store(storeKeys.bookGroupId(id), groupId)
        // uploader
        uploader?.let {
            store(storeKeys.bookUploader(id), uploader)
        }

        store(storeKeys.bookSource(id), source.keyString)
        if (source == BookSource.E) {
            store(storeKeys.bookP(id), 0)
            setBookPageUrls(id, listOf())
        }
    }

    fun getBook (context: Context, id: String): BookRecord {
        assertBookIdExist(id)
        return BookRecord(
            id = id,
            url = getBookUrl(id),
            coverUrl = getBookCoverPage(id).let { page ->
                val folder = File(context.getExternalFilesDir(null), id)
                val coverPageFile = File(folder, page.toString())
                if (coverPageFile.exists()) {
                    coverPageFile.path
                } else {
                    File(folder, "0").path
                }
            },
            cat = Util.categoryFromOrdinal(read(storeKeys.bookCatOrdinal(id))!!).name,
            title = read(storeKeys.bookTitle(id))!!,
            subtitle = read(storeKeys.bookSubTitle(id)) ?: "",
            pageNum = getBookPageNum(id),
            tags = read(storeKeys.bookTags(id)) ?: mapOf(),
            groupId = read(storeKeys.bookGroupId(id))!!,
            uploader = read(storeKeys.bookUploader(id))
        )
    }

    fun removeBook (id: String) {
        remove(storeKeys.bookUrl(id))
        remove(storeKeys.bookTitle(id))
        remove(storeKeys.bookSubTitle(id))
        remove(storeKeys.bookPageNum(id))
        remove(storeKeys.bookCatOrdinal(id))
        remove(storeKeys.bookTags(id))
        remove(storeKeys.bookCoverPage(id))
        remove(storeKeys.bookSkipPages(id))
        remove(storeKeys.bookLastViewTime(id))
        remove(storeKeys.bookBookMarks(id))
        remove(storeKeys.bookGroupId(id))
        remove(storeKeys.bookUploader(id))

        if (getBookSource(id) == BookSource.E) {
            remove(storeKeys.bookPageUrls(id))
            remove(storeKeys.bookP(id))
        }
        remove(storeKeys.bookSource(id))

        removeBookId(id)
    }

    fun isBookStored (id: String): Boolean = getAllBookIds().contains(id)

    fun getBookMarks (bookId: String) = read(storeKeys.bookBookMarks(bookId)) ?: listOf()

    fun addBookMark (bookId: String, page: Int) {
        val bookmarks = getBookMarks(bookId).toMutableList()
        bookmarks.add(page)
        store(storeKeys.bookBookMarks(bookId), bookmarks.sorted())
    }

    fun removeBookMark (bookId: String, page: Int) {
        val bookmarks = getBookMarks(bookId).toMutableList()
        bookmarks.remove(page).let { retFlag ->
            if (!retFlag) {
                throw Exception("the bookmark page $page is not exist")
            }
            store(storeKeys.bookBookMarks(bookId), bookmarks)
        }
    }

    fun changeBookGroup (bookId: String, groupId: Int) {
        store(storeKeys.bookGroupId(bookId), groupId)
    }

    //
    // getters and setters
    //

    // books
    fun getAllBookIds () = read(storeKeys.allBookIds()) ?: listOf()
    private fun addBookId (id: String) {
        val ids = getAllBookIds().toMutableList()
        if (ids.contains(id)) {
            throw Exception("bookId $id already exist")
        }
        store(storeKeys.allBookIds(), ids.also { it.add(id) })
    }
    private fun removeBookId (id: String) {
        val ids = getAllBookIds().toMutableSet().also { it.remove(id) }
        store(storeKeys.allBookIds(), ids.toList())
    }
    private fun assertBookIdExist (bookId: String) {
        if (!isBookStored(bookId)) {
            throw Exception("bookId $bookId not exist")
        }
    }

    fun getBookUrl (bookId: String) = read(storeKeys.bookUrl(bookId))!!

    fun getBookPageUrls (bookId: String) = read(storeKeys.bookPageUrls(bookId))!!
    fun setBookPageUrls (bookId: String, urls: List<String>) = store(storeKeys.bookPageUrls(bookId), urls)

    fun getBookP (bookId: String) = read(storeKeys.bookP(bookId))!!
    fun increaseBookP (bookId: String): Int {
        val p = getBookP(bookId) + 1
        store(storeKeys.bookP(bookId), p)
        return p
    }

    fun getBookPageNum (bookId: String) = read(storeKeys.bookPageNum(bookId))!!

    fun getBookSource (bookId: String): BookSource {
        return when (val sourceString = read(storeKeys.bookSource(bookId))!!) {
            BookSource.E.toString() -> BookSource.E
            BookSource.Hi.toString() -> BookSource.Hi
            else -> {
                throw Exception("unknown source $sourceString")
            }
        }
    }

    fun getBookCoverPage (bookId: String): Int = read(storeKeys.bookCoverPage(bookId))!!
    fun setBookCoverPage (bookId: String, v: Int) = store(storeKeys.bookCoverPage(bookId), v)

    fun getBookSkipPages (bookId: String) = read(storeKeys.bookSkipPages(bookId)) ?: listOf()
    fun setBookSkipPages (bookId: String, v: List<Int>) = store(storeKeys.bookSkipPages(bookId), v.sorted())

    fun getBookLastViewTime (bookId: String) = read(storeKeys.bookLastViewTime(bookId)) ?: 0L
    fun updateBookLastViewTime (bookId: String) = store(storeKeys.bookLastViewTime(bookId), System.currentTimeMillis())

    //
    // backup
    //
    fun backup (context: Context) {
        val dbFile = File("${context.filesDir}/datastore", "${DB_NAME}.preferences_pb")
        val backupFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "eSaver")
        if (!backupFolder.exists()) {
            backupFolder.mkdirs()
        }

        val backupFile = File(backupFolder, "book")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        dbFile.copyTo(backupFile)
    }
}