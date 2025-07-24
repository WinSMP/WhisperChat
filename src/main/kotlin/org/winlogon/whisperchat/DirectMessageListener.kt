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

class DirectMessageHandler(
    private val plugin: WhisperChatPlugin,
    private val dmSessionManager: DMSessionManager,
    private val formatter: MessageFormatter,
    private val config: FileConfiguration,
    private val groupManager: GroupManager
) : Listener {
    val plainSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val activeConv = dmSessionManager.activeDMs[player.uniqueId] ?: return

        when (activeConv) {
            is ActiveConversation.PlayerTarget -> handlePlayerDM(event, player, activeConv.target)
            is ActiveConversation.GroupTarget -> handleGroupDM(event, player, activeConv.groupName)
        }
    }

    private fun handlePlayerDM(event: AsyncChatEvent, player: Player, targetId: UUID) {
        val msg = plainSerializer.serialize(event.message())
        val prefix = config.getString("public-prefix") ?: "!"

        if (msg.startsWith(prefix) && msg.length > prefix.length && !msg[prefix.length].isWhitespace()) {
            val newMessage = msg.substring(prefix.length).trim()
            event.message(formatter.parseMessage(newMessage))
            event.isCancelled = false
            return
        }

        event.isCancelled = true

        val targetPlayer = Bukkit.getPlayer(targetId)
        val runnable = Runnable {
            val target = targetPlayer ?: run {
                formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.")
                dmSessionManager.activeDMs.remove(player.uniqueId)
                dmSessionManager.dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                    sessions.apply { remove(targetId) }
                }
                return@Runnable
            }

            formatter.sendFormattedMessage(player, target, msg, MessageType.DM)
            dmSessionManager.lastSenders[target.uniqueId] = player.uniqueId
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
            formatter.sendLegacyMessage(player, "messages.not-in-group", "You're not in this group")
            dmSessionManager.activeDMs.remove(player.uniqueId)
            return
        }

        val msg = plainSerializer.serialize(event.message())
        val prefix = config.getString("public-prefix") ?: "!"

        if (msg.startsWith(prefix) && msg.length > prefix.length && !msg[prefix.length].isWhitespace()) {
            val newMessage = msg.substring(prefix.length).trim()
            event.message(formatter.parseMessage(newMessage))
            event.isCancelled = false
            return
        }

        event.isCancelled = true
    
        val runnable = Runnable {
            group.members.forEach { memberId ->
                if (memberId != player.uniqueId) {
                    Bukkit.getPlayer(memberId)?.sendMessage(
                        formatter.parseMessage(
                            (config.getString("formats.group-dm") 
                                ?: "<gray>[<aqua>{group}<gray>] <dark_aqua>{sender}<gray>: <white>{message}")
                                .replace("{group}", groupName)
                                .replace("{sender}", player.name)
                                .replace("{message}", msg)
                        )
                    )
                }
            }
        }
        AsyncCraftr.runEntityTask(plugin, player, runnable)
    }
}
