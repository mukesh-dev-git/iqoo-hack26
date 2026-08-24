package com.limitless.codereview.sample

/**
 * Mirrors /sample-diffs at the repo root, inlined so the dashboard's "Sample 1/2/3" buttons
 * work with zero setup (no assets, no file I/O) — one-tap demo data for recording the walkthrough.
 * Keep these in sync with the diff files under /sample-diffs if those change.
 */
data class SampleDiff(val label: String, val content: String)

object SampleDiffs {
    val ALL = listOf(
        SampleDiff(
            label = "Null pointer",
            content = """
                diff --git a/UserProfile.kt b/UserProfile.kt
                index 1111111..2222222 100644
                --- a/UserProfile.kt
                +++ b/UserProfile.kt
                @@ -10,6 +10,9 @@ class UserProfile(private val repository: UserRepository) {

                     fun displayName(): String {
                         val user = repository.findById(currentUserId)
                -        return user?.name ?: "Unknown"
                +        return user.name
                     }

                     fun updateEmail(newEmail: String) {
            """.trimIndent()
        ),
        SampleDiff(
            label = "Off-by-one",
            content = """
                diff --git a/PageIndexer.kt b/PageIndexer.kt
                index 3333333..4444444 100644
                --- a/PageIndexer.kt
                +++ b/PageIndexer.kt
                @@ -5,7 +5,7 @@ class PageIndexer(private val items: List<String>) {

                     fun pageOf(index: Int): List<String> {
                         val start = index * PAGE_SIZE
                -        val end = minOf(start + PAGE_SIZE, items.size)
                +        val end = minOf(start + PAGE_SIZE, items.size + 1)
                         return items.subList(start, end)
                     }
                 }
            """.trimIndent()
        ),
        SampleDiff(
            label = "Clean",
            content = """
                diff --git a/Logger.kt b/Logger.kt
                index 5555555..6666666 100644
                --- a/Logger.kt
                +++ b/Logger.kt
                @@ -3,6 +3,7 @@ object Logger {

                     fun info(tag: String, message: String) {
                         println("[${'$'}tag] INFO: ${'$'}message")
                +        history.add("${'$'}tag: ${'$'}message")
                     }

                     private val history = mutableListOf<String>()
            """.trimIndent()
        )
    )
}
