package com.example.viewer.struct

enum class ItemType {
    Book, Video;

    companion object {
        @JvmStatic
        fun fromOrdinal (ordinal: Int) = ItemType.entries[ordinal]
    }
}