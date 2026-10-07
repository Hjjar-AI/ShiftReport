package com.hos.rushdpatients.network.telegram

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {

    @Test
    fun `plain text is unchanged`() {
        assertEquals("hello world", Markdown.escape("hello world"))
    }

    @Test
    fun `underscore is escaped`() {
        assertEquals("hello\\_world", Markdown.escape("hello_world"))
    }

    @Test
    fun `asterisk is escaped`() {
        assertEquals("a\\*b", Markdown.escape("a*b"))
    }

    @Test
    fun `brackets are escaped`() {
        assertEquals("\\[x\\]", Markdown.escape("[x]"))
    }

    @Test
    fun `parentheses are escaped`() {
        assertEquals("\\(x\\)", Markdown.escape("(x)"))
    }

    @Test
    fun `backslash is escaped first`() {
        assertEquals("a\\\\b", Markdown.escape("a\\b"))
    }

    @Test
    fun `dot is escaped`() {
        assertEquals("1\\.2\\.3", Markdown.escape("1.2.3"))
    }

    @Test
    fun `exclamation is escaped`() {
        assertEquals("hi\\!", Markdown.escape("hi!"))
    }

    @Test
    fun `hash and plus are escaped`() {
        assertEquals("\\# tag \\+ 1", Markdown.escape("# tag + 1"))
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals("", Markdown.escape(""))
    }

    @Test
    fun `null returns empty`() {
        assertEquals("", Markdown.escape(null))
    }

    @Test
    fun `bold wraps escaped text in asterisks`() {
        assertEquals("*a\\_b*", Markdown.bold("a_b"))
    }

    @Test
    fun `link escapes only the label`() {
        val label = "click_me"
        val url = "https://example.com/path?x=1&y=2"
        assertEquals("[click\\_me]($url)", Markdown.link(label, url))
    }

    @Test
    fun `user link builds tg url`() {
        assertEquals("[د\\. أحمد](tg://user?id=123)",
            Markdown.userLink("د. أحمد", 123L))
    }
}