package org.winlogon.whisperchat

import io.papermc.paper.event.player.AsyncChatEvent

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.winlogon.asynccraftr.AsyncCraftr
import org.winlogon.whisperchat.group.GroupManager

import java.util.UUID
import java.util.logging.Logger

class DirectMessageHandler(
    private val plugin: WhisperChatPlugin,
    private val dmSessionManager: DMSessionManager,
    private val formatter: MessageFormatter,
    private val config: FileConfiguration,
    private val groupManager: GroupManager,
    private val logger: Logger,
) : Listener {
    val plainSerializer = PlainTextComponentSerializer.plainText()

    /**
     * Handles the public-prefix escape hatch: if the message starts with the public prefix,
     * the original chat event continues to public chat and this handler backs out.
     * Returns true when the message was dispatched to public chat (caller should do nothing
     * further); false when it stays private (caller should cancel the event and route it).
     */
    private fun maybeSendPublic(event: AsyncChatEvent, player: Player): Boolean {
        val msg = plainSerializer.serialize(event.message())
        val prefix = config.getString("public-prefix") ?: "!"

        if (msg.startsWith(prefix) && msg.length > prefix.length && !msg[prefix.length].isWhitespace()) {
            val newMessage = msg.substring(prefix.length).trim()
            event.message(formatter.parseMessage(newMessage))
            event.isCancelled = false
            return true
        }
        return false
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        logger.fine("Handling chat event for ${event.player.name}")
        val player = event.player
        val activeConv = dmSessionManager.activeDMs[player.uniqueId] ?: return

        when (activeConv) {
            is ActiveConversation.PlayerTarget -> handlePlayerDM(event, player, activeConv.target)
            is ActiveConversation.GroupTarget -> handleGroupDM(event, player, activeConv.groupName)
        }
    }

    private fun handlePlayerDM(event: AsyncChatEvent, player: Player, targetId: UUID) {
        if (maybeSendPublic(event, player)) return

        event.isCancelled = true

        val targetPlayer = Bukkit.getPlayer(targetId)
        val runnable = Runnable {
            val target = targetPlayer ?: run {
                formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.")

                dmSessionManager.apply {
                    activeDMs.remove(player.uniqueId)
                    dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                        sessions.apply {
                            remove(targetId)
                        }
                    }
                }

                // Explicitly remove the key if the set becomes empty, as computeIfPresent might not reliably do so
                dmSessionManager.dmSessions[player.uniqueId]?.let { sessions ->
                    if (sessions.isEmpty()) {
                        dmSessionManager.dmSessions.remove(player.uniqueId)
                    }
                }
                return@Runnable
            }

            val msg = plainSerializer.serialize(event.message())
            formatter.sendFormattedMessage(player, target, msg, MessageType.DM)
            dmSessionManager.lastDmSenders[target.uniqueId] = player.uniqueId
            dmSessionManager.updateLastInteraction(player, target)
        }

        AsyncCraftr.runEntityTask(plugin, player, runnable)
    }

    private fun handleGroupDM(event: AsyncChatEvent, player: Player, groupName: String) {
        val group = groupManager.groups[groupName] ?: run {
            formatter.sendLegacyMessage(player, "messages.group-not-exist", "Group doesn't exist")
            dmSessionManager.activeDMs.remove(player.uniqueId)
            return
        }

        if (player.uniqueId !in group.members) {
            formatter.sendLegacyMessage(player, "messages.not-in-group", "You're not in this group", "{group}" to groupName)
            dmSessionManager.activeDMs.remove(player.uniqueId)
            return
        }

        if (maybeSendPublic(event, player)) return

        event.isCancelled = true

        val runnable = Runnable {
            val msg = plainSerializer.serialize(event.message())
            val groupMessageFormat = config.getString("formats.group-dm") ?:
                "&8[&3{group}&8]&7 &2{sender}&8: &f{message}"

            val formattedMessage = formatter.parseMessage(
                groupMessageFormat,
                "{group}" to groupName,
                "{sender}" to player.name,
                "{message}" to msg
            )

            group.sendMessage(formattedMessage)
        }
        AsyncCraftr.runEntityTask(plugin, player, runnable)
    }
}
