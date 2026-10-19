package org.winlogon.whisperchat

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.util.UUID
import org.winlogon.whisperchat.ActiveConversation

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

    @Test
    fun `removePlayer should clear all data associated with the player`() {
        val player1 = server.addPlayer()
        val player2 = server.addPlayer()
        val player3 = server.addPlayer()

        // Simulate player1 having an active DM with player2
        dmSessionManager.activeDMs[player1.uniqueId] = ActiveConversation.PlayerTarget(player2.uniqueId)
        dmSessionManager.activeDMs[player2.uniqueId] = ActiveConversation.PlayerTarget(player1.uniqueId)

        // Simulate player1 having a DM session with player2 and player3
        dmSessionManager.dmSessions.computeIfAbsent(player1.uniqueId) { mutableSetOf() }.add(player2.uniqueId)
        dmSessionManager.dmSessions.computeIfAbsent(player1.uniqueId) { mutableSetOf() }.add(player3.uniqueId)
        dmSessionManager.dmSessions.computeIfAbsent(player2.uniqueId) { mutableSetOf() }.add(player1.uniqueId)

        // Simulate player1 being the last whisper and DM sender for player2
        dmSessionManager.lastWhisperSenders[player2.uniqueId] = player1.uniqueId
        dmSessionManager.lastDmSenders[player2.uniqueId] = player1.uniqueId

        // Simulate last interaction between player1 and player2, and player1 and player3
        dmSessionManager.updateLastInteraction(player1, player2)
        dmSessionManager.updateLastInteraction(player1, player3)

        // Verify initial state
        assertTrue(dmSessionManager.activeDMs.containsKey(player1.uniqueId), "player1 should be in activeDMs initially")
        assertTrue(dmSessionManager.activeDMs.containsKey(player2.uniqueId), "player2 should be in activeDMs initially")
        assertTrue(dmSessionManager.dmSessions.containsKey(player1.uniqueId), "player1 should have DM sessions initially")
        assertTrue(dmSessionManager.dmSessions[player1.uniqueId]!!.contains(player2.uniqueId), "player1 should have a DM session with player2 initially")
        assertTrue(dmSessionManager.dmSessions[player1.uniqueId]!!.contains(player3.uniqueId), "player1 should have a DM session with player3 initially")
        assertTrue(dmSessionManager.dmSessions.containsKey(player2.uniqueId), "player2 should have DM sessions initially")
        assertTrue(dmSessionManager.dmSessions[player2.uniqueId]!!.contains(player1.uniqueId), "player2 should have a DM session with player1 initially")
        assertTrue(dmSessionManager.lastWhisperSenders.containsKey(player2.uniqueId), "player2 should have a whisper reply target initially")
        assertTrue(dmSessionManager.lastDmSenders.containsKey(player2.uniqueId), "player2 should have a DM reply target initially")
        assertNotNull(dmSessionManager.lastInteraction[dmSessionManager.sortedPair(player1.uniqueId, player2.uniqueId)], "last interaction between player1 and player2 should exist initially")
        assertNotNull(dmSessionManager.lastInteraction[dmSessionManager.sortedPair(player1.uniqueId, player3.uniqueId)], "last interaction between player1 and player3 should exist initially")

        // Remove player1
        dmSessionManager.removePlayer(player1)

        // Verify player1's data is cleared
        assertFalse(dmSessionManager.activeDMs.containsKey(player1.uniqueId), "player1 should not be in activeDMs after removal")
        assertFalse(dmSessionManager.dmSessions.containsKey(player1.uniqueId), "player1 should not have DM sessions after removal")
        assertFalse(dmSessionManager.lastWhisperSenders.containsValue(player1.uniqueId), "player1 should not be a whisper sender for anyone after removal")
        assertFalse(dmSessionManager.lastWhisperSenders.containsKey(player1.uniqueId), "player1 should not have a whisper reply target after removal")
        assertFalse(dmSessionManager.lastDmSenders.containsValue(player1.uniqueId), "player1 should not be a DM sender for anyone after removal")
        assertFalse(dmSessionManager.lastDmSenders.containsKey(player1.uniqueId), "player1 should not have a DM reply target after removal")
        assertNull(dmSessionManager.lastInteraction[dmSessionManager.sortedPair(player1.uniqueId, player2.uniqueId)], "last interaction between player1 and player2 should be null after removal")
        assertNull(dmSessionManager.lastInteraction[dmSessionManager.sortedPair(player1.uniqueId, player3.uniqueId)], "last interaction between player1 and player3 should be null after removal")

        // Verify other players' data is updated correctly
        assertFalse(dmSessionManager.activeDMs.containsKey(player2.uniqueId), "player2's active DM with player1 should be removed")
        assertFalse(dmSessionManager.dmSessions[player2.uniqueId]!!.contains(player1.uniqueId), "player2's DM session with player1 should be removed")
    }

    @Test
    fun `whisper and dm reply targets are tracked independently`() {
        val alice = server.addPlayer()
        val bob = server.addPlayer()
        val trudy = server.addPlayer()

        // Alice and Bob are talking in a DM session, so Bob's messages make Alice `/dm reply` to Bob
        dmSessionManager.lastDmSenders[alice.uniqueId] = bob.uniqueId

        // Eve then whispers Alice, which makes Eve Alice's `/r` target
        dmSessionManager.lastWhisperSenders[alice.uniqueId] = trudy.uniqueId

        // The two commands must not see each other's target, otherwise Eve's whisper
        // would redirect a DM reply that was meant for Bob
        assertEquals(bob.uniqueId, dmSessionManager.lastDmSenders[alice.uniqueId], "/dm reply should target Bob")
        assertEquals(trudy.uniqueId, dmSessionManager.lastWhisperSenders[alice.uniqueId], "/r should target Eve")
    }

    @Test
    fun `clearReplyTargets drops targets pointing across the expired pair`() {
        val alice = server.addPlayer()
        val bob = server.addPlayer()
        val trudy = server.addPlayer()

        dmSessionManager.lastWhisperSenders[bob.uniqueId] = alice.uniqueId
        dmSessionManager.lastDmSenders[bob.uniqueId] = alice.uniqueId
        // Alice started a newer whisper thread with Eve, which must survive Bob expiring
        dmSessionManager.lastWhisperSenders[alice.uniqueId] = trudy.uniqueId

        dmSessionManager.clearReplyTargets(alice.uniqueId, bob.uniqueId)

        assertNull(dmSessionManager.lastWhisperSenders[bob.uniqueId], "Bob's whisper reply target should be cleared")
        assertNull(dmSessionManager.lastDmSenders[bob.uniqueId], "Bob's DM reply target should be cleared")
        assertEquals(trudy.uniqueId, dmSessionManager.lastWhisperSenders[alice.uniqueId], "Eve thread should be untouched")
    }
}
