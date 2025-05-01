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
        /**
         * store page of the book mark, start from 0
         */
        fun bookBookMarks (bookId: String): CustomPreferencesKey<List<Int>> {
            assertBookIdExist(bookId)
            return CustomPreferencesKey("${bookId}_bookmarks")
        }
        // -----------
        // author
        fun allAuthors () = CustomPreferencesKey<List<String>>("authors")
        // individual author
        fun authorBookIds (author: String): CustomPreferencesKey<List<String>> {
            if (author != NO_AUTHOR) {
                assertAuthorExist(author)
            }
            return CustomPreferencesKey("${author}_bookIds")
        }
        fun authorListUpdateTime () = longPreferencesKey("authorListUpdateTime")
    }

    //
    // public methods
    //

    fun addBook (
        id: String,
        url: String,
        category: SearchDatabase.Companion.Category,
        title: String,
        pageNum: Int,
        tags: Map<String, List<String>>,
        source: BookSource
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
        // total page
        store(storeKeys.bookPageNum(id), pageNum)
        // tags
        store(storeKeys.bookTags(id), tags)

        addAuthorBookId(NO_AUTHOR, id)
        store(storeKeys.bookSource(id), source.keyString)
        if (source == BookSource.E) {
            store(storeKeys.bookP(id), 0)
            setBookPageUrls(id, listOf())
        }
    }

    /**
     * @param author author of the return book record;
     * this function doesn't read book's author from db, so the author of returned book record will be null if this is not provided
     */
    fun getBook (context: Context, id: String, author: String? = null): BookRecord {
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
            pageNum = getBookPageNum(id),
            tags = read(storeKeys.bookTags(id)) ?: mapOf(),
            author = author
        )
    }

    /**
     * @param bookAuthor
     * This function has O(n) complexity on searching who is the author if this is null.
     * Provide it to gain better performance
     */
    fun removeBook (id: String, bookAuthor: String?): Boolean {
        try {
            removeAuthorBookId(
                bookAuthor ?: findBookAuthor(id)!!,
                id
            )

            remove(storeKeys.bookUrl(id))
            remove(storeKeys.bookTitle(id))
            remove(storeKeys.bookPageNum(id))
            remove(storeKeys.bookCatOrdinal(id))
            remove(storeKeys.bookTags(id))
            remove(storeKeys.bookCoverPage(id))
            remove(storeKeys.bookSkipPages(id))
            remove(storeKeys.bookLastViewTime(id))
            remove(storeKeys.bookBookMarks(id))

            if (getBookSource(id) == BookSource.E) {
                remove(storeKeys.bookPageUrls(id))
                remove(storeKeys.bookP(id))
            }
            remove(storeKeys.bookSource(id))

            removeBookId(id)
            return true
        } catch (e: Exception) {
            println("[${this::class.simpleName}.${this::removeBook.name}] exception")
            e.printStackTrace()
            return false
        }
    }

    fun isBookStored (id: String): Boolean = getAllBookIds().contains(id)

    fun changeAuthor (bookId: String, oldAuthor: String, newAuthor: String) {
        if (oldAuthor == newAuthor) {
            return
        }

        var authorListUpdated = false

        if (!getAllAuthors().contains(newAuthor)) {
            println("[BookDataset] addAuthor $newAuthor")
            val authors = (read(storeKeys.allAuthors()) ?: listOf()).toMutableList()
            if (authors.contains(newAuthor)) {
                throw Exception("author $newAuthor already exist")
            }
            authors.add(newAuthor)
            store(storeKeys.allAuthors(), authors.sorted())
            authorListUpdated = true
        }

        addAuthorBookId(newAuthor, bookId)
        removeAuthorBookId(oldAuthor, bookId)

        if (authorListUpdated) {
            store(storeKeys.authorListUpdateTime(), System.currentTimeMillis())
        }
    }
    /**
     * update: instead or remove author
     */
    fun authorListUpdateTime () = read(storeKeys.authorListUpdateTime()) ?: 0

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

    // authors
    fun getAllAuthors (): List<String> = mutableListOf(NO_AUTHOR).also { it.addAll(
        getUserAuthors()
    ) }
    fun getUserAuthors () = read(storeKeys.allAuthors()) ?: listOf() // get authors that user added (without No Author)
    private fun findBookAuthor (bookId: String): String? {
        for (author in getAllAuthors()) {
            if (getAuthorBookIds(author).contains(bookId)) {
                return author
            }
        }
        println("[${this::class.simpleName}.${this::findBookAuthor.name}] book author not found for $bookId")
        return null
    }
    private fun removeAuthor (name: String) {
        println("[BookDataset] removeAuthor $name")

        assertAuthorExist(name)

        remove(storeKeys.authorBookIds(name))

        val authors = (read(storeKeys.allAuthors()) ?: listOf()).toMutableList()
        authors.remove(name)
        store(storeKeys.allAuthors(), authors)

        store(storeKeys.authorListUpdateTime(), System.currentTimeMillis())
    }
    private fun assertAuthorExist (name: String) {
        if (!getAllAuthors().contains(name)) {
            throw Exception("author $name not exist")
        }
    }

    fun getAuthorBookIds (author: String) = read(storeKeys.authorBookIds(author)) ?: listOf()
    /**
     * @param author this function supposes this author is exist in the database
     */
    private fun addAuthorBookId (author: String, bookId: String) {
        val bookIds = getAuthorBookIds(author).toMutableList()
        if (bookIds.contains(bookId)) {
            throw Exception("bookId $bookId already exist in the list of author $author")
        }
        bookIds.add(bookId)
        store(storeKeys.authorBookIds(author), bookIds.sorted())
    }
    private fun removeAuthorBookId (author: String, bookId: String) {
        println("[BookDataset] removeAuthorBookId")
        val bookIds = getAuthorBookIds(author).toMutableList()
        if (!bookIds.contains(bookId)) {
            throw Exception("bookId $bookId is not in the list of author $author")
        }
        bookIds.remove(bookId)

        if (author != NO_AUTHOR && bookIds.isEmpty()) {
            removeAuthor(author)
        } else {
            store(storeKeys.authorBookIds(author), bookIds)
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

        val backupFile = File(backupFolder, "book")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        dbFile.copyTo(backupFile)
    }
}