package com.example.viewer.dataset

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

enum class BookSource (val keyString: String) {
    E("E"),
    Hi("Hi")
}

private val Context.bookDataStore: DataStore<Preferences> by preferencesDataStore(name = "book")

class BookDataset (context: Context): BaseDataset() {
    companion object {
        const val NO_AUTHOR = "NoAuthor"

        @Volatile
        private var instance: BookDataset? = null
        fun getInstance (context: Context) = instance ?: synchronized(this) {
            instance ?: BookDataset(context).also { instance = it }
        }

        data class Book (
            val id: String,
            val url: String,
            val pageNum: Int,
            val source: String,
            val coverPage: Int,
            val skipPages: List<Int>,
            val lastViewTime: Long,
            val p: Int? = null,
            val pageUrls: List<String>? = null,
        )

        data class Author (
            val name: String,
            val bookIds: List<String>
        )
    }

    override val dataStore = context.bookDataStore

    //
    // store keys
    //
    private val storeKeys = object {
        // --------------
        // books
        fun allBookIds () = byteArrayPreferencesKey("bookIds")
        // individual book
        fun bookUrl (bookId: String): Preferences.Key<String> {
            assertBookIdExist(bookId)
            return stringPreferencesKey("${bookId}_url")
        }
        fun bookPageUrls (bookId: String): Preferences.Key<ByteArray> {
            assertBookIdExist(bookId)
            return byteArrayPreferencesKey("${bookId}_pageUrls")
        }
        fun bookP (bookId: String): Preferences.Key<Int> {
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_P")
        }
        fun bookPageNum (bookId: String): Preferences.Key<Int> {
            assertBookIdExist(bookId)
            return intPreferencesKey("${bookId}_pageNum")
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
        fun bookSkipPages (bookId: String): Preferences.Key<ByteArray> {
            // 0 for first page, pageNum - 1 for last page
            assertBookIdExist(bookId)
            return byteArrayPreferencesKey("${bookId}_skipPages")
        }
        fun bookLastViewTime (bookId: String): Preferences.Key<Long> {
            assertBookIdExist(bookId)
            return longPreferencesKey("${bookId}_lastViewTime")
        }
        // -----------
        // author
        fun allAuthors () = byteArrayPreferencesKey("authors")
        // individual author
        fun authorBookIds (author: String): Preferences.Key<ByteArray> {
            if (author != NO_AUTHOR) {
                assertAuthorExist(author)
            }
            return byteArrayPreferencesKey("${author}_bookIds")
        }
    }

    //
    // public methods
    //

    fun addBook (
        id: String,
        url: String,
        source: BookSource,
        pageNum: Int
    ) {
        if (pageNum < 1) {
            throw Exception("Invalid pageNum $pageNum")
        }

        addBookId(id)
        setBookUrl(id, url)
        setBookSource(id, source)
        addAuthorBookId(NO_AUTHOR, id)
        setBookCoverPage(id, 0)
        setBookPageNum(id, pageNum)
        if (source == BookSource.E) {
            setBookP(id, 0)
            setBookPageUrls(id, listOf())
        }
    }

    fun removeBook (id: String, bookAuthor: String): Boolean {
        try {
            removeAuthorBookId(bookAuthor, id)

            removeBookPageNum(id)
            removeBookUrl(id)
            removeBookCoverPage(id)
            removeBookSkipPages(id)
            removeBookLastViewTime(id)

            if (getBookSource(id) == BookSource.E) {
                removeBookPageUrls(id)
                removeBookP(id)
            }
            removeBookSource(id)

            removeBookId(id)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun changeAuthor (bookId: String, oldAuthor: String, newAuthor: String) {
        if (oldAuthor == newAuthor) {
            return
        }

        if (!getAllAuthors().contains(newAuthor)) {
            addAuthor(newAuthor)
        }

        addAuthorBookId(newAuthor, bookId)
        removeAuthorBookId(oldAuthor, bookId)

        if (oldAuthor != NO_AUTHOR && getAuthorBookIds(oldAuthor).isEmpty()) {
            removeAuthor(oldAuthor)
        }
    }

    //
    // getters and setters
    //
    // books
    fun getAllBookIds () = readFromByteArray<List<String>>(storeKeys.allBookIds()) ?: listOf()
    private fun addBookId (id: String) {
        val ids = getAllBookIds().toMutableList()
        if (ids.contains(id)) {
            throw Exception("bookId $id already exist")
        }
        storeAsByteArray(storeKeys.allBookIds(), ids.also { it.add(id) })
    }
    private fun removeBookId (id: String) {
        val ids = getAllBookIds().toMutableSet().also { it.remove(id) }
        storeAsByteArray(storeKeys.allBookIds(), ids.toList())
    }
    private fun assertBookIdExist (bookId: String) {
        if (!getAllBookIds().contains(bookId)) {
            throw Exception("bookId $bookId not exist")
        }
    }

    fun getBookUrl (bookId: String) = read(storeKeys.bookUrl(bookId))!!
    fun setBookUrl (bookId: String, url: String) = store(storeKeys.bookUrl(bookId), url)
    fun removeBookUrl (bookId: String) = remove(storeKeys.bookUrl(bookId))

    fun getBookPageUrls (bookId: String) = readFromByteArray<List<String>>(storeKeys.bookPageUrls(bookId))!!
    fun setBookPageUrls (bookId: String, urls: List<String>) = storeAsByteArray(storeKeys.bookPageUrls(bookId), urls)
    fun removeBookPageUrls (bookId: String) = remove(storeKeys.bookPageUrls(bookId))

    fun getBookP (bookId: String) = read(storeKeys.bookP(bookId))!!
    fun setBookP (bookId: String, v: Int) = store(storeKeys.bookP(bookId), v)
    fun increaseBookP (bookId: String) = store(storeKeys.bookP(bookId), getBookP(bookId) + 1)
    fun removeBookP (bookId: String) = remove(storeKeys.bookP(bookId))

    fun getBookPageNum (bookId: String) = read(storeKeys.bookPageNum(bookId))!!
    fun setBookPageNum (bookId: String, v: Int) = store(storeKeys.bookPageNum(bookId), v)
    fun removeBookPageNum (bookId: String) = remove(storeKeys.bookPageNum(bookId))

    fun getBookSource (bookId: String): BookSource {
        return when (val sourceString = read(storeKeys.bookSource(bookId))!!) {
            BookSource.E.toString() -> BookSource.E
            BookSource.Hi.toString() -> BookSource.Hi
            else -> {
                throw Exception("unknown source $sourceString")
            }
        }
    }
    fun setBookSource (bookId: String, source: BookSource) = store(storeKeys.bookSource(bookId), source.keyString)
    fun removeBookSource (bookId: String) = remove(storeKeys.bookSource(bookId))

    fun getBookCoverPage (bookId: String): Int = read(storeKeys.bookCoverPage(bookId))!!
    fun setBookCoverPage (bookId: String, v: Int) = store(storeKeys.bookCoverPage(bookId), v)
    fun removeBookCoverPage (bookId: String) = remove(storeKeys.bookCoverPage(bookId))

    fun getBookSkipPages (bookId: String) = readFromByteArray<List<Int>>(storeKeys.bookSkipPages(bookId)) ?: listOf()
    fun setBookSkipPages (bookId: String, v: List<Int>) = storeAsByteArray(storeKeys.bookSkipPages(bookId), v.sorted())
    fun removeBookSkipPages (bookId: String) = remove(storeKeys.bookSkipPages(bookId))

    fun getBookLastViewTime (bookId: String) = read(storeKeys.bookLastViewTime(bookId)) ?: 0L
    fun updateBookLastViewTime (bookId: String) = store(storeKeys.bookLastViewTime(bookId), System.currentTimeMillis())
    fun removeBookLastViewTime (bookId: String) = remove(storeKeys.bookLastViewTime(bookId))

    // authors
    fun getAllAuthors (): List<String> = mutableListOf(NO_AUTHOR).also { it.addAll(
        getUserAuthors()
    ) }
    fun getUserAuthors () = readFromByteArray<List<String>>(storeKeys.allAuthors()) ?: listOf() // get authors that user added (without No Author)
    fun addAuthor (name: String) {
        println("[History.addAuthor] $name")
        val authors = (readFromByteArray<List<String>>(storeKeys.allAuthors()) ?: listOf()).toMutableList()
        if (authors.contains(name)) {
            throw Exception("author $name already exist")
        }
        authors.add(name)
        storeAsByteArray(storeKeys.allAuthors(), authors.sorted())
    }
    fun removeAuthor (name: String) {
        println("[History.removeAuthor] $name")
        val authors = (readFromByteArray<List<String>>(storeKeys.allAuthors()) ?: listOf()).toMutableList()
        assertAuthorExist(name)
        authors.remove(name)
        storeAsByteArray(storeKeys.allAuthors(), authors)
    }
    private fun assertAuthorExist (name: String) {
        if (!getAllAuthors().contains(name)) {
            throw Exception("author $name not exist")
        }
    }

    fun getAuthorBookIds (author: String) = readFromByteArray<List<String>>(storeKeys.authorBookIds(author)) ?: listOf()
    fun addAuthorBookId (author: String, bookId: String) {
        val bookIds = getAuthorBookIds(author).toMutableList()
        if (bookIds.contains(bookId)) {
            throw Exception("bookId $bookId already exist in the list of author $author")
        }
        bookIds.add(bookId)
        storeAsByteArray(storeKeys.authorBookIds(author), bookIds.sorted())
    }
    fun removeAuthorBookId (author: String, bookId: String) {
        println("removeAuthorBookId")
        val bookIds = getAuthorBookIds(author).toMutableList()
        if (!bookIds.contains(bookId)) {
            throw Exception("bookId $bookId is not in the list of author $author")
        }
        bookIds.remove(bookId)
        storeAsByteArray(storeKeys.authorBookIds(author), bookIds)
    }
}