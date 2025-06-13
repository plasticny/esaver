package com.example.viewer.struct

import com.example.viewer.R

enum class Category {
    Doujinshi {
        override val color = R.color.doujinshi_red
        override val value = 2
    },
    Manga {
        override val color = R.color.manga_orange
        override val value = 4
    },
    ArtistCG {
        override val color = R.color.artistCG_yellow
        override val value = 8
    },
    NonH {
        override val color = R.color.nonH_blue
        override val value = 256
    };

    abstract val color: Int
    abstract val value: Int
}