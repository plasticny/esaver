package com.example.viewer.struct

enum class ItemSource (val keyString: String) {
    E("eHentai"),
    Hi("hitomi"),
    Wn("Wnacg"),
    Ru("Rule34");

    companion object {
        @JvmStatic
        fun fromOrdinal (ordinal: Int) = entries[ordinal]
    }
}