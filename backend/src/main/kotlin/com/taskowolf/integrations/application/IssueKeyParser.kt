package com.taskowolf.integrations.application

import org.springframework.stereotype.Component

@Component
class IssueKeyParser {
    private val pattern = Regex("""\b([A-Z][A-Z0-9]+-\d+)\b""")

    fun parseKeys(text: String): List<String> =
        pattern.findAll(text).map { it.groupValues[1] }.distinct().toList()
}
