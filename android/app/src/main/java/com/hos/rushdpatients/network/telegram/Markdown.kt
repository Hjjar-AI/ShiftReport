package com.hos.rushdpatients.network.telegram

/**
 * Telegram MarkdownV2 escaping.
 *
 * Special characters that must be escaped outside of code blocks and
 * pre-formatted entities: _ * [ ] ( ) ~ ` > # + - = | { } . !
 *
 * The backslash itself must be escaped first.
 *
 * URLs inside [label](url) are NOT escaped — only the label is.
 */
object Markdown {

    private val V2_SPECIALS = setOf(
        '_', '*', '[', ']', '(', ')', '~', '`', '>',
        '#', '+', '-', '=', '|', '{', '}', '.', '!'
    )

    fun escape(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        return buildString(text.length + 8) {
            for (c in text) {
                if (c == '\\') {
                    append("\\\\")
                } else {
                    if (c in V2_SPECIALS) append('\\')
                    append(c)
                }
            }
        }
    }

    fun bold(text: String?): String = "*${escape(text)}*"

    fun underline(text: String?): String = "__${escape(text)}__"

    /**
     * Build a clickable link. Label is escaped; URL is passed through.
     */
    fun link(label: String?, url: String): String = "[${escape(label)}]($url)"

    /** Convert the subset emitted by report builders into readable preview text. */
    fun toPlainText(markdown: String): String {
        return buildString(markdown.length) {
            var index = 0
            while (index < markdown.length) {
                val char = markdown[index]
                when {
                    char == '\\' && index + 1 < markdown.length -> {
                        append(markdown[index + 1])
                        index += 2
                    }
                    char == '[' -> {
                        var end = index + 1
                        while (end < markdown.length && markdown[end] != ']') {
                            end += if (markdown[end] == '\\' && end + 1 < markdown.length) 2 else 1
                        }
                        val urlEnd = if (markdown.startsWith("](", end)) markdown.indexOf(')', end + 2) else -1
                        if (urlEnd >= 0) {
                            append(toPlainText(markdown.substring(index + 1, end)))
                            index = urlEnd + 1
                        } else {
                            append(char)
                            index++
                        }
                    }
                    char == '*' || char == '~' || char == '_' -> index++
                    else -> {
                        append(char)
                        index++
                    }
                }
            }
        }
    }
}
