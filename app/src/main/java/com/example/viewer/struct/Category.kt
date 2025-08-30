package com.example.viewer.struct

import com.example.viewer.R

/**
 * NOTE: do not change the ordering
 */
enum class Category {
    Doujinshi {
        override val color = R.color.doujinshi_red
        override val value = 2
        override val displayText = R.string.doujinshi
    },
    Manga {
        override val color = R.color.manga_orange
        override val value = 4
        override val displayText = R.string.manga
    },
    ArtistCG {
        override val color = R.color.artistCG_yellow
        override val value = 8
        override val displayText = R.string.artist_cg
    },
    NonH {
        override val color = R.color.nonH_blue
        override val value = 256
        override val displayText = R.string.non_h
    },
    All {
        override val color = R.color.nonH_blue
        override val value = -1
        override val displayText = R.string.category_all
    },
    Magazine {
        override val color = R.color.artistCG_yellow
        override val value = -1
        override val displayText = R.string.magazine
    },
    Cosplay {
        override val color = R.color.cosplay_purple
        override val value = 64
        override val displayText = R.string.cosplay
    };

    abstract val color: Int
    abstract val value: Int
    abstract val displayText: Int

    companion object {
        @JvmStatic
        fun fromOrdinal (ordinal: Int) = entries[ordinal]

        @JvmStatic
        fun fromName (name: String) = when (name) {
            Doujinshi.name -> Doujinshi
            Manga.name -> Manga
            ArtistCG.name -> ArtistCG
            "Artist CG" -> ArtistCG
            NonH.name -> NonH
            "Non-H" -> NonH
            else -> throw Exception("unexpected string $name")
        }

        @JvmStatic
        val ECategories = arrayOf(Doujinshi, Manga, ArtistCG, NonH)
        @JvmStatic
        val WnCategories = arrayOf(All, Doujinshi, Manga, Magazine)
    }
}