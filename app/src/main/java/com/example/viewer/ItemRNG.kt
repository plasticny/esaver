package com.example.viewer

import android.content.Context
import com.example.viewer.data.repository.ItemRepository
//import com.example.viewer.data.repository.BookRepository
import com.example.viewer.data.struct.item.Item
//import com.example.viewer.data.struct.Book
import kotlinx.coroutines.runBlocking
import kotlin.math.floor
import kotlin.math.min

class ItemRNG private constructor (context: Context) {
    companion object {
        private const val POOL_SIZE = 5

        @Volatile
        private var instance: ItemRNG? = null

        private fun getInstance (context: Context): ItemRNG = synchronized(this) {
            instance ?: ItemRNG(context).also { instance = it }
        }

        fun next (context: Context): Long = getInstance(context).next()

        fun getPoolStatus (context: Context): Pair<Boolean, Boolean> =
            getInstance(context).let { Pair(it.pullH, it.pullNH) }

        fun changePool (context: Context, pullH: Boolean, pullNH: Boolean) {
            getInstance(context).apply {
                this.pullH = pullH
                this.pullNH = pullNH
            }
        }
    }

    private val itemRepo: ItemRepository by lazy { ItemRepository(context) }

    private val bookIdSequenceH: MutableList<Item.Companion.SequenceItem> by lazy {
        runBlocking { itemRepo.getIdSeqH().toMutableList() }
    }
    private val bookIdSequenceNH: MutableList<Item.Companion.SequenceItem> by lazy {
        runBlocking { itemRepo.getIdSeqNH().toMutableList() }
    }

    private var pullH = true
    private var pullNH = false

    private fun next (): Long {
        if (!pullH && !pullNH) {
            throw IllegalStateException()
        }

        val seqSize = (if (pullH) bookIdSequenceH.size else 0) + (if (pullNH) bookIdSequenceNH.size else 0)
        val seqToPick = floor(Math.random() * min(POOL_SIZE, seqSize)).toInt()

        if (!pullH) {
            return pullIdFromSeq(bookIdSequenceNH, seqToPick)
        }
        if (!pullNH) {
            return pullIdFromSeq(bookIdSequenceH, seqToPick)
        }
        return pullIdFromAllSeq(seqToPick)
    }

    private fun pullIdFromSeq (seq: MutableList<Item.Companion.SequenceItem>, toPick: Int): Long {
        val ret = seq.removeAt(toPick)
        seq.add(Item.Companion.SequenceItem(
            id = ret.id,
            lastViewTime = seq.last().lastViewTime + 1
        ))
        return ret.id
    }

    private fun pullIdFromAllSeq (toPick: Int): Long {
        var idxH = 0
        var idxNH = 0
        var j = 0
        while (j < toPick && idxH <= bookIdSequenceH.lastIndex && idxNH <= bookIdSequenceNH.lastIndex) {
            if (bookIdSequenceH[idxH].lastViewTime < bookIdSequenceNH[idxNH].lastViewTime) {
                idxH++
            } else {
                idxNH++
            }
            j++
        }

        if (idxH > bookIdSequenceH.lastIndex || bookIdSequenceH[idxH].lastViewTime < bookIdSequenceNH[idxNH].lastViewTime) {
            return pullIdFromSeq(bookIdSequenceNH, idxNH)
        }
        return pullIdFromSeq(bookIdSequenceH, idxH)
    }
}