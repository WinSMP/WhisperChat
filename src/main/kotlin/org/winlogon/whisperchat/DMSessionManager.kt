package org.winlogon.whisperchat

import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import org.bukkit.entity.Player
import java.util.logging.Logger

class DMSessionManager {
    val activeDMs = ConcurrentHashMap<UUID, ActiveConversation>()
    val dmSessions = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // Whisper reply targets, keyed by recipient. Used only by `/r`, which is scoped
    // to one-off whispers and never mixes with DM threads.
    val lastWhisperSenders = ConcurrentHashMap<UUID, UUID>()

    // DM reply targets, keyed by recipient. Used only by `/dm reply`, which stays
    // independent of sessions so a reply never starts or redirects a conversation.
    val lastDmSenders = ConcurrentHashMap<UUID, UUID>()

    val lastInteraction = ConcurrentHashMap<Pair<UUID, UUID>, Long>()

    /**
     * Updates the last interaction time between two players.
     */
    fun updateLastInteraction(sender: Player, receiver: Player) {
        val pair = sortedPair(sender.uniqueId, receiver.uniqueId)
        lastInteraction[pair] = System.currentTimeMillis()
    }

    // Creates a sorted pair of UUIDs, based on the byte representation
    internal fun sortedPair(a: UUID, b: UUID) =
        if (a < b) Pair(a, b) else Pair(b, a)

    /**
     * Drops the whisper and DM reply targets that point across a pair whose inactivity
     * window just elapsed, so an expired conversation can never be resumed with `/r`.
     * Only clears entries that actually reference the other player, leaving any newer
     * thread one of them started in the meantime intact.
     */
    fun clearReplyTargets(a: UUID, b: UUID) {
        if (lastWhisperSenders[a] == b) lastWhisperSenders.remove(a)
        if (lastWhisperSenders[b] == a) lastWhisperSenders.remove(b)
        if (lastDmSenders[a] == b) lastDmSenders.remove(a)
        if (lastDmSenders[b] == a) lastDmSenders.remove(b)
    }

    /**
     * Removes all data associated with a player when they leave the server.
     */
    // TODO: should this be moved to the only unit test it's implemented in, or
    // make a new listener marking users as gone, and after a certain time call
    // this function?
    fun removePlayer(player: Player) {
        val playerUUID = player.uniqueId

        // Remove from activeDMs
        activeDMs.remove(playerUUID)
        activeDMs.entries.removeIf { (_, conversation) ->
            conversation is ActiveConversation.PlayerTarget && conversation.target == playerUUID
        }

        // Remove from dmSessions
        dmSessions.remove(playerUUID)
        dmSessions.forEach { _, sessions ->
            sessions.remove(playerUUID)
        }

        // Remove from reply targets
        lastWhisperSenders.remove(playerUUID)
        lastWhisperSenders.entries.removeIf { (_, senderUUID) ->
            senderUUID == playerUUID
        }
        lastDmSenders.remove(playerUUID)
        lastDmSenders.entries.removeIf { (_, senderUUID) ->
            senderUUID == playerUUID
        }

        // Remove from lastInteraction
        lastInteraction.entries.removeIf { (pair, _) ->
            pair.first == playerUUID || pair.second == playerUUID
        }
    }
}
