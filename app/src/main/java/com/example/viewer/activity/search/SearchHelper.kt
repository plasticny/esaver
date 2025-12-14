package com.example.viewer.activity.search

import android.content.Context
import com.example.viewer.struct.BookSource
import org.jsoup.nodes.Document

abstract class SearchHelper (
    protected val searchMarkData: SearchMarkData
) {
    companion object {
        const val NOT_SET = -1
        const val ENDED = -2

        fun getSearchHelper (context: Context, searchMarkData: SearchMarkData): SearchHelper {
            return when (BookSource.fromOrdinal(searchMarkData.sourceOrdinal)) {
                BookSource.E -> ESearchHelper(searchMarkData)
                BookSource.Wn -> WnSearchHelper(searchMarkData)
                BookSource.Ru -> RuSearchHelper(context, searchMarkData)
                BookSource.Hi -> throw NotImplementedError("unexpected source")
            }
        }
    }

    protected var next = NOT_SET
    protected var prev = NOT_SET

    val hasNextBlock: Boolean
        get() = next != ENDED
    val hasPrevBlock: Boolean
        get() = prev != ENDED
    val nextToStore: String?
        get() = if (this.next == NOT_SET) null else this.next.toString()

    var searchResultString: String? = null

    abstract fun getNextBlockSearchUrl (): String
    abstract fun getPrevBlockSearchUrl (): String
    abstract suspend fun fetchBooks (searchUrl: String, isSearchMarkChanged: () -> Boolean): List<SearchBookData>?
    abstract suspend fun storeDetailAsTmpBook (searchBookData: SearchBookData): Boolean

    fun loadSearchHistory (next: Int) {
        this.next = next
        prev = NOT_SET
    }
}