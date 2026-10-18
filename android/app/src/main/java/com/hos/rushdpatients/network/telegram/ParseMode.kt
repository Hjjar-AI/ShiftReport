package com.hos.rushdpatients.network.telegram

enum class ParseMode(val wire: String) {
    MARKDOWN_V1("Markdown"),
    MARKDOWN_V2("MarkdownV2"),
    HTML("HTML")
}