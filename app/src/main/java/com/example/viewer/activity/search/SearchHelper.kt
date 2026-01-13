package com.example.viewer.activity.search

import android.content.Context
import com.example.viewer.struct.ItemSource

abstract class SearchHelper (
    protected val context: Context,
    protected val searchMarkData: SearchMarkData
) {
    companion object {
        const val NOT_SET = -1
        const val ENDED = -2

        fun getSearchHelper (context: Context, searchMarkData: SearchMarkData): SearchHelper {
            return when (ItemSource.fromOrdinal(searchMarkData.sourceOrdinal)) {
                ItemSource.E -> ESearchHelper(context, searchMarkData)
                ItemSource.Wn -> WnSearchHelper(context, searchMarkData)
                ItemSource.Ru -> RuSearchHelper(context, searchMarkData)
                ItemSource.Hi -> throw NotImplementedError("unexpected source")
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
    abstract suspend fun fetchItems (searchUrl: String, isSearchMarkChanged: () -> Boolean): List<SearchItemData>?
    abstract suspend fun storeDetailAsTmpProfileItem (searchItemData: SearchItemData): Boolean

    fun loadSearchHistory (next: Int) {
        this.next = next
        prev = NOT_SET
    }
}