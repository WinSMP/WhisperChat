package org.winlogon.whisperchat

import io.papermc.paper.event.player.AsyncChatEvent

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.whisperchat.MessageFormatter
import org.winlogon.whisperchat.WhisperChatPlugin
import org.winlogon.asynccraftr.AsyncCraftr

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DirectMessageHandler(
    private val plugin: WhisperChatPlugin,
    private val activeDMs: ConcurrentHashMap<UUID, UUID>,
    private val dmSessions: ConcurrentHashMap<UUID, MutableSet<UUID>>,
    private val lastSenders: ConcurrentHashMap<UUID, UUID>,
    private val formatter: MessageFormatter,
    private val config: FileConfiguration,
) : Listener {
    val plainSerializer = PlainTextComponentSerializer.plainText()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val targetId = activeDMs[player.uniqueId] ?: return

        val msg = plainSerializer.serialize(event.message())
        val prefix = config.getString("public-prefix") ?: "!"

        if (msg.startsWith(prefix) && msg.length > prefix.length && !msg[prefix.length].isWhitespace()) {
            val newMessage = msg.substring(prefix.length).trim()
            val component = formatter.parseMessage(newMessage)
            event.message(component)
            event.isCancelled = false
            return
        }

        event.isCancelled = true

        val targetPlayer = Bukkit.getPlayer(targetId)
        val runnable = Runnable {
            val target = targetPlayer ?: run {
                formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.")
                activeDMs.remove(player.uniqueId)
                dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                    sessions.apply { remove(targetId) }
                }
                return@Runnable
            }

            formatter.sendFormattedMessage(player, target, msg, MessageType.DM)
            lastSenders[target.uniqueId] = player.uniqueId
        }

        AsyncCraftr.runEntityTask(plugin, player, runnable)

        targetPlayer?.let { plugin.updateLastInteraction(player, it) }
    }
}
