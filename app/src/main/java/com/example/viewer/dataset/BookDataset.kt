package com.example.viewer.dataset

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

enum class BookSource (val keyString: String) {
    E("E"),
    Hi("Hi")
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

class BookDataset {
    companion object {
        const val NO_AUTHOR = "NoAuthor"

        //
        // dataclasses
        //
        data class SearchMark (
            val name: String,
            val categories: List<Category>,
            val tags: List<Pair<String, String>>
        ): Serializable {
            companion object {
                enum class Category {
                    Doujinshi { override val value = 2 },
                    Manga { override val value = 4 },
                    ArtistCG { override val value = 8 };
                    abstract val value: Int;
                }
            }
        }

        //
        // set data store
        //
        private lateinit var dataStore: DataStore<Preferences>
        fun init (context: Context) {
            if (!Companion::dataStore.isInitialized) {
                dataStore = context.dataStore
            }
        }

        //
        // store keys
        //
        private class StoreKeys {
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
        private val storeKeys = StoreKeys()

        //
        // getters and setters
        //
        // books
        fun getAllBookIds () = readList<String>(storeKeys.allBookIds()) ?: listOf()
        fun addBookId (id: String) {
            // NOTE: make sure the new id is stored in the last of the list
            val ids = getAllBookIds().toMutableList()
            if (ids.contains(id)) {
                throw Exception("bookId $id already exist")
            }
            ids.add(id)
            store(storeKeys.allBookIds(), ids)
        }
        fun removeBookId (id: String) {
            val ids = getAllBookIds().toMutableSet()
            ids.remove(id)
            store(storeKeys.allBookIds(), ids.toList())
        }
        private fun assertBookIdExist (bookId: String) {
            if (!getAllBookIds().contains(bookId)) {
                throw Exception("bookId $bookId not exist")
            }
        }

        fun getBookUrl (bookId: String) = read(storeKeys.bookUrl(bookId))!!
        fun setBookUrl (bookId: String, url: String) = store(storeKeys.bookUrl(bookId), url)
        fun removeBookUrl (bookId: String) = remove(storeKeys.bookUrl(bookId))

        fun getBookPageUrls (bookId: String) = readList<String>(storeKeys.bookPageUrls(bookId))!!
        fun setBookPageUrls (bookId: String, urls: List<String>) = store(storeKeys.bookPageUrls(bookId), urls)
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

        fun getBookSkipPages (bookId: String) = readList<Int>(storeKeys.bookSkipPages(bookId)) ?: listOf()
        fun setBookSkipPages (bookId: String, v: List<Int>) = store(storeKeys.bookSkipPages(bookId), v.sorted())
        fun removeBookSkipPages (bookId: String) = remove(storeKeys.bookSkipPages(bookId))

        fun getBookLastViewTime (bookId: String) = read(storeKeys.bookLastViewTime(bookId)) ?: 0L
        fun updateBookLastViewTime (bookId: String) = store(storeKeys.bookLastViewTime(bookId), System.currentTimeMillis())
        fun removeBookLastViewTime (bookId: String) = remove(storeKeys.bookLastViewTime(bookId))

        // authors
        fun getAllAuthors (): List<String> = mutableListOf(NO_AUTHOR).also { it.addAll(
            getUserAuthors()
        ) }
        fun getUserAuthors (): List<String> = readList(storeKeys.allAuthors()) ?: listOf() // get authors that user added (without No Author)
        fun addAuthor (name: String) {
            println("[History.addAuthor] $name")
            val authors = (readList<String>(storeKeys.allAuthors()) ?: listOf()).toMutableList()
            if (authors.contains(name)) {
                throw Exception("author $name already exist")
            }
            authors.add(name)
            store(storeKeys.allAuthors(), authors.sorted())
        }
        fun removeAuthor (name: String) {
            println("[History.removeAuthor] $name")
            val authors = (readList<String>(storeKeys.allAuthors()) ?: listOf()).toMutableList()
            assertAuthorExist(name)
            authors.remove(name)
            store(storeKeys.allAuthors(), authors)
        }
        private fun assertAuthorExist (name: String) {
            if (!getAllAuthors().contains(name)) {
                throw Exception("author $name not exist")
            }
        }

        fun getAuthorBookIds (author: String) = readList<String>(storeKeys.authorBookIds(author)) ?: listOf()
        fun addAuthorBookId (author: String, bookId: String) {
            val bookIds = getAuthorBookIds(author).toMutableList()
            if (bookIds.contains(bookId)) {
                throw Exception("bookId $bookId already exist in the list of author $author")
            }
            bookIds.add(bookId)
            store(storeKeys.authorBookIds(author), bookIds.sorted())
        }
        fun removeAuthorBookId (author: String, bookId: String) {
            println("removeAuthorBookId")
            val bookIds = getAuthorBookIds(author).toMutableList()
            if (!bookIds.contains(bookId)) {
                throw Exception("bookId $bookId is not in the list of author $author")
            }
            bookIds.remove(bookId)
            store(storeKeys.authorBookIds(author), bookIds)
        }

        //
        // data store modification functions
        //
        private fun<T> store (key: Preferences.Key<T> , v: T) {
            runBlocking { dataStore.edit { it[key] = v } }
        }
        private fun<T> store (key: Preferences.Key<ByteArray>, v: List<T>) {
            val outputStream = ByteArrayOutputStream()
            ObjectOutputStream(outputStream).writeObject(v)
            store(key, outputStream.toByteArray())
        }
        private fun<T> read (key: Preferences.Key<T>): T? {
            var res: T? = null
            runBlocking {
                res = dataStore.data.map { it[key] }.first()
            }
            return res
        }
        private fun<T> readList (key: Preferences.Key<ByteArray>): List<T>? {
            val data = read(key) ?: return null
            return ObjectInputStream(ByteArrayInputStream(data)).readObject() as List<T>
        }
        private fun<T> remove (key: Preferences.Key<T>) {
            runBlocking { dataStore.edit { it.remove(key) } }
        }

        //
        //  backup
        //
        private fun backup (context: Context) {
            val datastore = File(context.filesDir, "datastore")
            val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "eSaver.preferences_pb")
            File(datastore, "history.preferences_pb").copyTo(target, true)
            Toast.makeText(context, "存檔已另存至Documents", Toast.LENGTH_SHORT).show()
        }
    }
}