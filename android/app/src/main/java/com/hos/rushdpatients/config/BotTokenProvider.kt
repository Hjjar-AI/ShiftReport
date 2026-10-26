package com.hos.rushdpatients.config

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BotTokenProvider @Inject constructor(
    private val projectConfigStore: ProjectConfigStore
) {

    val botToken: String
        get() = projectConfigStore.current().botToken

    fun maskedToken(): String {
        val t = botToken
        return if (t.length < 12) "***" else "${t.take(6)}…${t.takeLast(4)}"
    }
}
