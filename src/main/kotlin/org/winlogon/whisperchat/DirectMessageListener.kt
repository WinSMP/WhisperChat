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

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DirectMessageHandler(
    private val plugin: JavaPlugin,
    private val activeDMs: ConcurrentHashMap<UUID, UUID>,
    private val dmSessions: ConcurrentHashMap<UUID, MutableSet<UUID>>,
    private val lastSenders: ConcurrentHashMap<UUID, UUID>,
    private val formatter: MessageFormatter,
    private val config: FileConfiguration,
    private val isFolia: Boolean
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val targetId = activeDMs[player.uniqueId] ?: return

        val msg = PlainTextComponentSerializer.plainText().serialize(event.message())
        val prefix = config.getString("public-prefix") ?: "!"

        if (msg.startsWith(prefix) && msg.length > prefix.length && !msg[prefix.length].isWhitespace()) {
            val newMessage = msg.substring(prefix.length).trim()
            val component = formatter.parseMessage(newMessage)
            event.message(component)
            event.isCancelled = false
            return
        }

        event.isCancelled = true

        val runnable = Runnable {
            val target = Bukkit.getPlayer(targetId) ?: run {
                formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.", null)
                activeDMs.remove(player.uniqueId)
                dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                    sessions.apply { remove(targetId) }
                }
                return@Runnable
            }

            formatter.sendFormattedMessage(player, target, msg, MessageType.DM)
            lastSenders[target.uniqueId] = player.uniqueId
        }

        if (isFolia) {
            player.scheduler.run(plugin, { _ -> runnable.run() }, null)
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable)
        }
    }
}
