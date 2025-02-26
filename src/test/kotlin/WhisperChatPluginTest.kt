package org.winlogon.whisperchat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import org.mockito.Mockito.*

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WhisperChatPluginTest {
    private lateinit var server: ServerMock
    private lateinit var plugin: WhisperChatPlugin
    private lateinit var player1: PlayerMock
    private lateinit var player2: PlayerMock
    private lateinit var player3: PlayerMock

    @BeforeEach
    fun setUp() {
        // Start the mock server
        server = MockBukkit.mock()
        
        // Load the plugin
        plugin = MockBukkit.load(WhisperChatPlugin::class.java)
        
        // Add test players
        player1 = server.addPlayer("Player1")
        player2 = server.addPlayer("Player2")
        player3 = server.addPlayer("Player3")
    }

    @AfterEach
    fun tearDown() {
        // Stop the mock server
        MockBukkit.unmock()
    }

    // Helper method to access private fields
    @Suppress("UNCHECKED_CAST")
    private fun <T> getPrivateField(instance: Any, fieldName: String): T {
        val field: Field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(instance)
    }

    @Test
    fun testDMStartCommand() {
        // Execute the DM start command
        server.dispatchCommand(player1, "dm start Player2")
        
        // Verify activeDMs contains the correct mapping
        val activeDMs = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "activeDMs")
        assertEquals(player2.uniqueId, activeDMs[player1.uniqueId])
        
        // Verify dmSessions contains the correct mapping
        val dmSessions = getPrivateField<ConcurrentHashMap<UUID, MutableSet<UUID>>>(plugin, "dmSessions")
        assertTrue(dmSessions[player1.uniqueId]?.contains(player2.uniqueId) ?: false)
        
        // Verify lastSenders contains the correct mapping
        val lastSenders = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "lastSenders")
        assertEquals(player1.uniqueId, lastSenders[player2.uniqueId])
        
        // Verify player1 received a message
        player1.assertSaid { message -> 
            message.contains("DM started with Player2") 
        }
    }

    @Test
    fun testDMStartWithSelf() {
        // Try to start a DM with yourself
        server.dispatchCommand(player1, "dm start Player1")
        
        // Verify no active DM was created
        val activeDMs = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "activeDMs")
        assertNull(activeDMs[player1.uniqueId])
        
        // Verify player received an error message
        player1.assertSaid { message -> 
            message.contains("You cannot whisper yourself") 
        }
    }

    @Test
    fun testDMSwitch() {
        // Setup: Start DMs with player2 and player3
        server.dispatchCommand(player1, "dm start Player2")
        server.dispatchCommand(player1, "dm start Player3")
        
        // Verify initial active DM is with player3
        val activeDMs = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "activeDMs")
        assertEquals(player3.uniqueId, activeDMs[player1.uniqueId])
        
        // Switch back to player2
        server.dispatchCommand(player1, "dm switch Player2")
        
        // Verify active DM is now with player2
        assertEquals(player2.uniqueId, activeDMs[player1.uniqueId])
        
        // Verify player1 received a message about the switch
        player1.assertSaid { message -> 
            message.contains("Switched DM to Player2") 
        }
    }

    @Test
    fun testDMSwitchInvalidTarget() {
        // Setup: Start DM with player2 only
        server.dispatchCommand(player1, "dm start Player2")
        
        // Try to switch to player3 (no DM session exists)
        server.dispatchCommand(player1, "dm switch Player3")
        
        // Verify active DM is still with player2
        val activeDMs = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "activeDMs")
        assertEquals(player2.uniqueId, activeDMs[player1.uniqueId])
        
        // Verify player1 received an error message
        player1.assertSaid { message -> 
            message.contains("Invalid DM target") 
        }
    }

    @Test
    fun testDMList() {
        // Setup: Start DMs with player2 and player3
        server.dispatchCommand(player1, "dm start Player2")
        server.dispatchCommand(player1, "dm start Player3")
        
        // Execute list command
        server.dispatchCommand(player1, "dm list")
        
        // Verify player1 received a list containing both players
        player1.assertSaid { message -> 
            message.contains("Active DMs") 
        }
        player1.assertSaid { message -> 
            message.contains("Player2") 
        }
        player1.assertSaid { message -> 
            message.contains("Player3") 
        }
    }

    @Test
    fun testDMLeave() {
        // Setup: Start DM with player2
        server.dispatchCommand(player1, "dm start Player2")
        
        // Execute leave command
        server.dispatchCommand(player1, "dm leave")
        
        // Verify activeDMs no longer contains the mapping
        val activeDMs = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "activeDMs")
        assertNull(activeDMs[player1.uniqueId])
        
        // Verify dmSessions no longer contains player2
        val dmSessions = getPrivateField<ConcurrentHashMap<UUID, MutableSet<UUID>>>(plugin, "dmSessions")
        assertFalse(dmSessions[player1.uniqueId]?.contains(player2.uniqueId) ?: false)
        
        // Verify player1 received a message
        player1.assertSaid { message -> 
            message.contains("Left DM with Player2") 
        }
    }

    @Test
    fun testWhisperCommand() {
        // Execute whisper command
        server.dispatchCommand(player1, "w Player2 Hello there!")
        
        // Verify both players received the message
        player1.assertSaid { message -> 
            message.contains("WHISPER") && message.contains("Player1") && 
            message.contains("Player2") && message.contains("Hello there!") 
        }
        player2.assertSaid { message -> 
            message.contains("WHISPER") && message.contains("Player1") && 
            message.contains("Player2") && message.contains("Hello there!") 
        }
        
        // Verify lastSenders was updated
        val lastSenders = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "lastSenders")
        assertEquals(player1.uniqueId, lastSenders[player2.uniqueId])
    }

    @Test
    fun testMsgCommand() {
        // Execute msg command (should function like whisper)
        server.dispatchCommand(player1, "msg Player2 Test message")
        
        // Verify both players received the message
        player1.assertSaid { message -> 
            message.contains("WHISPER") && message.contains("Player1") && 
            message.contains("Player2") && message.contains("Test message") 
        }
        player2.assertSaid { message -> 
            message.contains("WHISPER") && message.contains("Player1") && 
            message.contains("Player2") && message.contains("Test message") 
        }
        
        // Verify lastSenders was updated
        val lastSenders = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "lastSenders")
        assertEquals(player1.uniqueId, lastSenders[player2.uniqueId])
    }

    @Test
    fun testReplyCommand() {
        // Setup: player1 sends message to player2
        server.dispatchCommand(player1, "w Player2 Hi there")
        
        // Clear messages
        player1.nextMessage()
        player2.nextMessage()
        
        // player2 replies
        server.dispatchCommand(player2, "r Reply message")
        
        // Verify both players received the reply message
        player1.assertSaid { message -> 
            message.contains("REPLY") && message.contains("Player2") && 
            message.contains("Player1") && message.contains("Reply message") 
        }
        player2.assertSaid { message -> 
            message.contains("REPLY") && message.contains("Player2") && 
            message.contains("Player1") && message.contains("Reply message") 
        }
        
        // Verify lastSenders was updated
        val lastSenders = getPrivateField<ConcurrentHashMap<UUID, UUID>>(plugin, "lastSenders")
        assertEquals(player2.uniqueId, lastSenders[player1.uniqueId])
    }

    @Test
    fun testReplyWithNoTarget() {
        // Try to reply without any previous messages
        server.dispatchCommand(player1, "r No target")
        
        // Verify player1 received an error message
        player1.assertSaid { message -> 
            message.contains("No reply target found") 
        }
    }

    @Test
    fun testChatInterceptionInDM() {
        // Setup: Start DM with player2
        server.dispatchCommand(player1, "dm start Player2")
        
        // Create chat event
        val chatMessage = Component.text("This is a chat message")
        val chatEvent = MockAsyncChatEvent(player1, chatMessage)
        
        // Trigger chat event
        server.pluginManager.callEvent(chatEvent)
        
        // Run scheduled tasks
        server.scheduler.performTicks(1)
        
        // Verify event was cancelled
        assertTrue(chatEvent.isCancelled)
        
        // Verify message was sent to both players
        player1.assertSaid { message -> 
            message.contains("DM") && message.contains("Player1") && 
            message.contains("Player2") && message.contains("This is a chat message") 
        }
        player2.assertSaid { message -> 
            message.contains("DM") && message.contains("Player1") && 
            message.contains("Player2") && message.contains("This is a chat message") 
        }
    }

    @Test
    fun testLegacyToMiniMessage() {
        // Access private method via reflection
        val method = plugin.javaClass.getDeclaredMethod("legacyToMiniMessage", String::class.java)
        method.isAccessible = true
        
        // Test color code conversion
        val result = method.invoke(plugin, "&aGreen &cRed &bBlue") as String
        
        // Verify conversion
        assertEquals("<green>Green <red>Red <aqua>Blue", result)
    }

    @Test
    fun testParseMessage() {
        // Access private method via reflection
        val method = plugin.javaClass.getDeclaredMethod("parseMessage", String::class.java)
        method.isAccessible = true
        
        // Test message parsing
        val component = method.invoke(plugin, "&aGreen text") as Component
        
        // Verify the component has the correct content
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)
        assertEquals("Green text", plainText)
    }

    // Custom AsyncChatEvent implementation for testing
    private class MockAsyncChatEvent(player: Player, private val messageComponent: Component) : AsyncChatEvent(false, player, mutableSetOf(), messageComponent, messageComponent) {
        override fun message(): Component = messageComponent
        override fun originalMessage(): Component = messageComponent
    }
}
