package com.example.viewer.struct

enum class BookSource (val keyString: String) {
    E("eHentai"),
    Hi("hitomi"),
    Wn("Wnacg");

    companion object {
        @JvmStatic
        fun fromOrdinal (ordinal: Int) = entries[ordinal]
    }
}