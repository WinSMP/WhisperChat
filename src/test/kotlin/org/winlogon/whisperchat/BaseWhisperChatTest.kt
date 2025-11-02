package org.winlogon.whisperchat

import dev.jorel.commandapi.MockCommandAPIPlugin

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

open class BaseWhisperChatTest {
    protected lateinit var server: ServerMock
    protected lateinit var plugin: WhisperChatPlugin

    @BeforeEach
    open fun setUp() {
        server = MockBukkit.mock()
        MockCommandAPIPlugin.load()
        plugin = MockBukkit.load(WhisperChatPlugin::class.java)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }
}
