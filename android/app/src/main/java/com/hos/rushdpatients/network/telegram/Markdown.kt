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

    fun italic(text: String?): String = "_${escape(text)}_"

    fun underline(text: String?): String = "__${escape(text)}__"

    fun strikethrough(text: String?): String = "~${escape(text)}~"

    /**
     * Build a clickable link. Label is escaped; URL is passed through.
     */
    fun link(label: String?, url: String): String = "[${escape(label)}]($url)"

    /**
     * Mention a Telegram user by numeric id (works for users the bot can see).
     */
    fun userLink(label: String?, userId: Long): String = link(label, "tg://user?id=$userId")

    /**
     * Escape a whole message body that contains no markup.
     */
    fun escapeBody(text: String?): String = escape(text)

    /** Convert the subset emitted by report builders into readable preview text. */
    fun toPlainText(markdown: String): String {
        val withoutLinks = markdown.replace(
            Regex("""\[([^]]+)]\([^)]*\)"""),
            "\$1"
        )
        val withoutMarkers = withoutLinks
            .replace("__", "")
            .replace("*", "")
            .replace("~", "")
        return buildString(withoutMarkers.length) {
            var index = 0
            while (index < withoutMarkers.length) {
                val char = withoutMarkers[index]
                if (char == '\\' && index + 1 < withoutMarkers.length) {
                    append(withoutMarkers[index + 1])
                    index += 2
                } else {
                    append(char)
                    index++
                }
            }
        }
    }
}
