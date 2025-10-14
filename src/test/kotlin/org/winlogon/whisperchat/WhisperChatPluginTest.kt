package org.winlogon.whisperchat

import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import dev.jorel.commandapi.MockCommandAPIPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull

class WhisperChatPluginTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: WhisperChatPlugin

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()

        MockCommandAPIPlugin.load();

        plugin = MockBukkit.load(WhisperChatPlugin::class.java)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun testPluginLoads() {
        assertNotNull(plugin)
        // Add more assertions here to check if onEnable was successful
        // For example, check if commands are registered, or if listeners are registered
    }
}
