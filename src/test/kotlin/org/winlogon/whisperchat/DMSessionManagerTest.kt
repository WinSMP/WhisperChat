package org.winlogon.whisperchat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.UUID

class DMSessionManagerTest : BaseWhisperChatTest() {
    private lateinit var dmSessionManager: DMSessionManager

    @BeforeEach
    override fun setUp() {
        super.setUp()
        dmSessionManager = DMSessionManager()
    }

    @Test
    fun `test updating last interaction time`() {
        val player1 = server.addPlayer()
        val player2 = server.addPlayer()
        val pair = dmSessionManager.sortedPair(player1.uniqueId, player2.uniqueId)

        // No interaction time initially
        assertNull(dmSessionManager.lastInteraction[pair])

        // Update interaction time
        val currentTime = System.currentTimeMillis()
        dmSessionManager.updateLastInteraction(player1, player2)

        // Check if the interaction time is updated
        val lastInteractionTime = dmSessionManager.lastInteraction[pair]
        assertNotNull(lastInteractionTime)
        assertTrue(lastInteractionTime!! >= currentTime)
    }

    @Test
    fun `sortedPair should return a consistent pair regardless of order`() {
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()

        val pair1 = dmSessionManager.sortedPair(uuid1, uuid2)
        val pair2 = dmSessionManager.sortedPair(uuid2, uuid1)

        assertEquals(pair1, pair2)
    }
}
