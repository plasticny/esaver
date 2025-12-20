package com.example.viewer.activity.search

import com.example.viewer.struct.Category

data class SearchBookData (
    val id: String,
    val url: String,
    val coverUrl: String,
    val cat: Category,
    val title: String,
    val pageNum: Int,
    val tags: Map<String, List<String>>,
    val rating: Float?
)