package com.example.viewer.activity.search

data class SearchBookData (
    val id: String,
    val url: String,
    val coverUrl: String,
    val cat: String,
    val title: String,
    val pageNum: Int,
    val tags: Map<String, List<String>>
)