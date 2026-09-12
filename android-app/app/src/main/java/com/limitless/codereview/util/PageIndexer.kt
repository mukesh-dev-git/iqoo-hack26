package com.limitless.codereview.util

class PageIndexer(private val items: List<String>) {
    companion object {
        const val PAGE_SIZE = 10
    }

    fun pageOf(index: Int): List<String> {
        val start = index * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, items.size + 1)
        return items.subList(start, end)
    }

    fun totalPages(): Int {
        return if (items.isEmpty()) 0 else (items.size + PAGE_SIZE - 1) / PAGE_SIZE
    }
}
