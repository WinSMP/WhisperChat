package org.winlogon.whisperchat

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.winlogon.asynccraftr.AsyncCraftr
import org.winlogon.whisperchat.ActiveConversation
import org.winlogon.whisperchat.group.GroupManager

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class DirectMessageListenerTest : BaseWhisperChatTest() {

    private lateinit var dmSessionManager: DMSessionManager
    private lateinit var formatter: MessageFormatter
    private lateinit var config: FileConfiguration
    private lateinit var groupManager: GroupManager
    private lateinit var logger: Logger
    private lateinit var directMessageListener: DirectMessageHandler

    private lateinit var activeDMsMap: ConcurrentHashMap<UUID, ActiveConversation>
    private lateinit var dmSessionsMap: ConcurrentHashMap<UUID, MutableSet<UUID>>
    private lateinit var lastSendersMap: ConcurrentHashMap<UUID, UUID>
    private lateinit var lastInteractionMap: ConcurrentHashMap<Pair<UUID, UUID>, Long>

    private val plainSerializer = PlainTextComponentSerializer.plainText()

    @BeforeEach
    override fun setUp() {
        super.setUp()
        dmSessionManager = mock()
        activeDMsMap = ConcurrentHashMap()
        dmSessionsMap = ConcurrentHashMap()
        lastSendersMap = ConcurrentHashMap()
        lastInteractionMap = ConcurrentHashMap()

        whenever(dmSessionManager.activeDMs).thenReturn(activeDMsMap)
        whenever(dmSessionManager.dmSessions).thenReturn(dmSessionsMap)
        whenever(dmSessionManager.lastSenders).thenReturn(lastSendersMap)
        whenever(dmSessionManager.lastInteraction).thenReturn(lastInteractionMap)

        formatter = mock()
        config = mock()
        groupManager = mock()
        logger = mock()
        plugin = mock()

        directMessageListener = DirectMessageHandler(
            plugin = plugin,
            dmSessionManager = dmSessionManager,
            formatter = formatter,
            config = config,
            groupManager = groupManager,
            logger = logger
        )

        whenever(config.getString("public-prefix")).thenReturn("!")
    }

    @Test
    fun `handlePlayerDM should inform sender if target is offline and clear session`() {
        val sender = server.addPlayer()
        val targetId = UUID.randomUUID()
        val message = "Hello offline player"
        val chatEvent: AsyncChatEvent = mock()
        whenever(chatEvent.player).thenReturn(sender)
        whenever(chatEvent.message()).thenReturn(Component.text(message))
        whenever(chatEvent.isCancelled).thenReturn(false)

        // Simulate active DM session
        activeDMsMap[sender.uniqueId] = ActiveConversation.PlayerTarget(targetId)
        dmSessionsMap.computeIfAbsent(sender.uniqueId) { mutableSetOf() }.add(targetId)

        // Mock Bukkit.getPlayer to return null for the targetId
        mockStatic(Bukkit::class.java).use {
            whenever(Bukkit.getPlayer(targetId)).thenReturn(null)

            mockStatic(AsyncCraftr::class.java).use {
                // Mock AsyncCraftr.runEntityTask to execute the runnable immediately for testing
                whenever(AsyncCraftr.runEntityTask(any(), any(), any())).thenAnswer { invocation ->
                    val runnable = invocation.arguments[2] as Runnable
                    runnable.run()
                    null
                }

                directMessageListener.onChat(chatEvent)

                // Verify that the sender is informed
                verify(formatter).sendLegacyMessage(eq(sender), eq("messages.target-offline"), any())

                // Verify that the active DM session is cleared
                assertTrue(activeDMsMap.isEmpty())

                // Verify that the DM session with the target is cleared
                assertFalse(dmSessionsMap.containsKey(sender.uniqueId))
            }
        }
    }
}
