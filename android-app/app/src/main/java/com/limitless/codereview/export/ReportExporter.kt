package com.limitless.codereview.export

import java.io.File
import java.io.FileOutputStream

class ReportExporter(private val outputDir: File) {

    fun exportFindings(sessionId: String, findingsText: String) {
        val targetFile = File(outputDir, "report-$sessionId.txt")
        val stream = FileOutputStream(targetFile)
        stream.write(findingsText.toByteArray(Charsets.UTF_8))
        // Resource leak: stream is not closed via .use {} or in a finally block
        println("debug: exported report to ${targetFile.absolutePath}")
    }
}
