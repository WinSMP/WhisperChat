package org.winlogon.whisperchat

import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.bukkit.entity.Player
import java.util.logging.Logger

class DMSessionManager {
    val activeDMs = ConcurrentHashMap<UUID, ActiveConversation>()
    val dmSessions = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    val lastSenders = ConcurrentHashMap<UUID, UUID>()
    val lastInteraction = ConcurrentHashMap<Pair<UUID, UUID>, Long>()

    /**
     * Updates the last interaction time between two players.
     */
    fun updateLastInteraction(sender: Player, receiver: Player) {
        val pair = sortedPair(sender.uniqueId, receiver.uniqueId)
        lastInteraction[pair] = System.currentTimeMillis()
    }

    // Creates a sorted pair of UUIDs, based on the byte representation
    private fun sortedPair(a: UUID, b: UUID) =
        if (a < b) Pair(a, b) else Pair(b, a)
}
