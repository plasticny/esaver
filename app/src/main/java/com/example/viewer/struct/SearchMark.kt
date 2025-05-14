package com.example.viewer.struct

import com.example.viewer.database.SearchDatabase.Companion.Category

data class SearchMark (
    val name: String,
    val categories: List<Category>,
    val keyword: String,
    val tags: Map<String, List<String>>,
    val uploader: String
) {
    fun getSearchUrl (next: String? = null): String {
        val fCatsValue = if (categories.isNotEmpty()) {
            1023 - categories.sumOf { it.value }
        } else null

        // f search
        var fSearch = ""
        if (keyword.isNotEmpty()) {
            fSearch += "$keyword+"
        }
        if (tags.isNotEmpty() || uploader.isNotEmpty()) {
            val tokens = mutableListOf<String>()
            tags.forEach { entry ->
                val cat = entry.key
                for (value in entry.value) {
                    tokens.add(buildTagValueString(cat, value))
                }
            }
            if (uploader.isNotEmpty()) {
                tokens.add(buildTagValueString("uploader", uploader))
            }
            fSearch += tokens.joinToString(" ")
        }

        var ret = "https://e-hentai.org/"
        if (fCatsValue != null || fSearch.isNotEmpty()) {
            ret += "?"
        }
        fCatsValue?.let { ret += "f_cats=$it&" }
        if (fSearch.isNotEmpty()) {
            ret += "f_search=$fSearch&"
        }
        ret += "inline_set=dm_e&"
        next?.let { ret += "next=$next" }

        return ret
    }

    private fun buildTagValueString (category: String, value: String): String {
        return if (value.contains(' ')) {
            "${category}%3A\"${value}%24\""
        } else {
            "${category}%3A${value}%24"
        }
    }
}
