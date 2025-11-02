package org.winlogon.whisperchat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

import org.bukkit.configuration.file.FileConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.winlogon.retrohue.RetroHue

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageFormatterTest : BaseWhisperChatTest() {
    private lateinit var config: FileConfiguration
    private lateinit var socialSpyLogger: MessageLogger
    private lateinit var miniMessage: MiniMessage
    private lateinit var lastInteraction: ConcurrentHashMap<Pair<UUID, UUID>, Long>
    private lateinit var messageFormatter: MessageFormatter
    private lateinit var formatter: RetroHue

    @BeforeEach
    override fun setUp() {
        super.setUp()
        config = mock(FileConfiguration::class.java)
        socialSpyLogger = mock(MessageLogger::class.java)
        miniMessage = MiniMessage.miniMessage()
        lastInteraction = ConcurrentHashMap()
        formatter = RetroHue(miniMessage)

        `when`(config.getString("formats.whisper")).thenReturn("&7[WHISPER] {sender} &7-> &6{receiver}&7: &f{message}")
        `when`(config.getString("formats.reply")).thenReturn("&7[REPLY] {sender} &7-> &6{receiver}&7: &f{message}")
        `when`(config.getString("formats.dm")).thenReturn("&7[DM] {sender} &7-> &6{receiver}&7: &f{message}")
        `when`(config.getString("test.legacy.message")).thenReturn("&aHello &b{player}!")
        `when`(config.getString("test.legacy.fallback")).thenReturn("&cFallback message")


        messageFormatter = MessageFormatter(lastInteraction, config, socialSpyLogger, miniMessage)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Test
    fun `sendFormattedMessage sends correct whisper message`() {
        val sender = server.addPlayer("Sender")
        val receiver = server.addPlayer("Receiver")
        val message = "Test whisper message"

        messageFormatter.sendFormattedMessage(sender, receiver, message, MessageType.WHISPER)
        val expectedComponent = formatter.convertToComponent("&7[WHISPER] Sender &7-> &6Receiver&7: &fTest whisper message", '&')

        assertEquals(expectedComponent, formatter.convertToComponent(sender.nextMessage() ?: "", '§'))
        verify(socialSpyLogger, never()).log(any<Pair<String, String>>(), any<Component>())
        assertTrue(lastInteraction.containsKey(Pair(sender.uniqueId, receiver.uniqueId)) || lastInteraction.containsKey(Pair(receiver.uniqueId, sender.uniqueId)))
    }

    @Test
    fun `sendFormattedMessage sends correct reply message`() {
        val sender = server.addPlayer("Sender")
        val receiver = server.addPlayer("Receiver")
        val message = "Test reply message"

        messageFormatter.sendFormattedMessage(sender, receiver, message, MessageType.REPLY)

        val expectedComponent = formatter.convertToComponent("&7[REPLY] Sender &7-> &6Receiver&7: &fTest reply message", '&')

        assertEquals(expectedComponent, formatter.convertToComponent(sender.nextMessage() ?: "", '§'))
        assertEquals(expectedComponent, formatter.convertToComponent(receiver.nextMessage() ?: "", '§'))

        verify(socialSpyLogger, never()).log(any<Pair<String, String>>(), any<Component>())
        assertTrue(lastInteraction.containsKey(Pair(sender.uniqueId, receiver.uniqueId)) || lastInteraction.containsKey(Pair(receiver.uniqueId, sender.uniqueId)))
    }

    @Test
    fun `sendFormattedMessage sends correct DM message and logs it`() {
        val sender = server.addPlayer("Sender")
        val receiver = server.addPlayer("Receiver")
        val message = "Test DM message"

        messageFormatter.sendFormattedMessage(sender, receiver, message, MessageType.DM)

        val expectedComponent = formatter.convertToComponent("&7[DM] Sender &7-> &6Receiver&7: &fTest DM message", '&')

        assertEquals(expectedComponent, formatter.convertToComponent(sender.nextMessage() ?: "", '§'))

        assertEquals(expectedComponent, formatter.convertToComponent(receiver.nextMessage() ?: "", '§'))

        verify(socialSpyLogger, times(1)).log(any<Pair<String, String>>(), any<Component>())
        assertTrue(lastInteraction.containsKey(Pair(sender.uniqueId, receiver.uniqueId)) || lastInteraction.containsKey(Pair(receiver.uniqueId, sender.uniqueId)))
    }

    @Test
    fun `sendLegacyMsg sends message with replacements`() {
        val player = server.addPlayer("TestPlayer")
        val expectedComponent = formatter.convertToComponent("&aHello &bTestPlayer!", '&')
        messageFormatter.sendLegacyMsg(player, "test.legacy.message", "&cFallback", listOf(Pair("{player}", "TestPlayer")))
        assertEquals(expectedComponent, formatter.convertToComponent(player.nextMessage() ?: "", '§'))
    }

    @Test
    fun `sendLegacyMsg sends fallback message if key not found`() {
        val player = server.addPlayer("TestPlayer")

        messageFormatter.sendLegacyMsg(player, "non.existent.key", "&cFallback message", null)

        val expectedComponent = formatter.convertToComponent("&cFallback message", '&')
        assertEquals(expectedComponent, formatter.convertToComponent(player.nextMessage() ?: "", '§'))
    }

    @Test
    fun `sendLegacyMessage sends message with vararg replacements`() {
        val player = server.addPlayer("TestPlayer")

        messageFormatter.sendLegacyMessage(player, "test.legacy.message", "&cFallback", Pair("{player}", "TestPlayer"))

        val expectedComponent = formatter.convertToComponent("&aHello &bTestPlayer!", '&')
        assertEquals(expectedComponent, formatter.convertToComponent(player.nextMessage() ?: "", '§'))
    }

    @Test
    fun `updateLastInteraction updates map correctly`() {
        val sender = server.addPlayer("Sender")
        val receiver = server.addPlayer("Receiver")
        val message = "Test whisper message"

        messageFormatter.sendFormattedMessage(sender, receiver, message, MessageType.WHISPER)

        assertTrue(lastInteraction.isNotEmpty())
        val key = if (sender.uniqueId < receiver.uniqueId) Pair(sender.uniqueId, receiver.uniqueId) else Pair(receiver.uniqueId, sender.uniqueId)
        assertNotNull(lastInteraction[key])
    }
}
