package org.winlogon.whisperchat

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.PlayerArgument

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import io.papermc.paper.event.player.AsyncChatEvent

import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WhisperChatPlugin : JavaPlugin() {
    private val activeDMs = ConcurrentHashMap<UUID, UUID>()
    private val dmSessions = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val lastSenders = ConcurrentHashMap<UUID, UUID>()
    private var isFolia = false
    private lateinit var config: FileConfiguration

    override fun onEnable() {
        saveDefaultConfig()
        config = getConfig()
        isFolia = checkFolia()

        registerCommands()
        server.pluginManager.registerEvents(ChatListener(), this)
    }

    private fun checkFolia(): Boolean {
        return try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun registerCommands() {
        // DM Command
        CommandAPICommand("dm")
            .withSubcommand(
                CommandAPICommand("start")
                    .withArguments(PlayerArgument("target"))
                    .executesPlayer(PlayerCommandExecutor { player, args ->
                        val target = args[0] as Player
                        handleDMStart(player, target)
                    })
            )
            .withSubcommand(
                CommandAPICommand("switch")
                    .withArguments(PlayerArgument("target"))
                    .executesPlayer(PlayerCommandExecutor { player, args ->
                        val target = args[0] as Player
                        handleDMSwitch(player, target)
                    })
            )
            .withSubcommand(
                CommandAPICommand("list")
                    .executesPlayer(PlayerCommandExecutor { player, _ ->
                        handleDMList(player)
                    })
            )
            .withSubcommand(
                CommandAPICommand("leave")
                    .executesPlayer(PlayerCommandExecutor { player, _ ->
                        handleDMLeave(player)
                    })
            )
            .withSubcommand(
                CommandAPICommand("help")
                    .executesPlayer(PlayerCommandExecutor { player, _ ->
                        sendHelp(player)
                    })
            )
            .register()

        // Define the whisper command executor once:
        val whisperExecutor = PlayerCommandExecutor { player, args ->
            val target = args[0] as Player
            val message = args[1] as String
            sendFormattedMessage(player, target, message, "whisper")
        }

        val whisperCmd = CommandAPICommand("w")
            .withArguments(PlayerArgument("target"))
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(whisperExecutor)
        whisperCmd.register()

        // Re-use the same executor for the "msg" command:
        CommandAPICommand("msg")
            .withArguments(PlayerArgument("target"))
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(whisperExecutor)
            .register()

        // Reply Command
        CommandAPICommand("r")
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val message = args[0] as String
                handleReply(player, message)
            })
            .register()
    }

    private fun handleDMStart(player: Player, target: Player) {
        if (player == target) {
            player.sendMessage(config.getString("messages.self-whisper-error") ?: "You cannot whisper yourself!")
            return
        }

        dmSessions.compute(player.uniqueId) { _, sessions ->
            (sessions ?: mutableSetOf()).apply { add(target.uniqueId) }
        }
        activeDMs[player.uniqueId] = target.uniqueId
        lastSenders[target.uniqueId] = player.uniqueId

        player.sendMessage(
            (config.getString("messages.dm-start") ?: "DM started with {target}")
                .replace("{target}", target.name)
        )
    }

    private fun handleDMSwitch(player: Player, target: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.no-dm-sessions") ?: "No DM sessions found.")
            return
        }

        if (target.uniqueId in sessions) {
            activeDMs[player.uniqueId] = target.uniqueId
            player.sendMessage(
                (config.getString("messages.dm-switch") ?: "Switched DM to {target}")
                    .replace("{target}", target.name)
            )
        } else {
            player.sendMessage(config.getString("messages.invalid-dm-target") ?: "Invalid DM target.")
        }
    }

    private fun handleDMList(player: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.no-dm-sessions") ?: "No DM sessions found.")
            return
        }

        val onlineTargets = sessions.mapNotNull { Bukkit.getPlayer(it)?.name }
        if (onlineTargets.isEmpty()) {
            player.sendMessage(config.getString("messages.no-active-dms") ?: "No active DMs.")
            return
        }

        player.sendMessage(config.getString("messages.dm-list-header") ?: "Active DMs:")
        onlineTargets.forEach { target ->
            player.sendMessage(
                (config.getString("messages.dm-list-item") ?: "{target}")
                    .replace("{target}", target)
            )
        }
    }

    private fun handleDMLeave(player: Player) {
        val targetId = activeDMs[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.not-in-dm") ?: "You are not in a DM.")
            return
        }

        activeDMs.remove(player.uniqueId)
        dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
            sessions.apply { remove(targetId) }
        }
        player.sendMessage(
            (config.getString("messages.dm-left") ?: "Left DM with {target}")
                .replace("{target}", Bukkit.getPlayer(targetId)?.name ?: "Unknown")
        )
    }

    private fun handleReply(player: Player, message: String) {
        val lastSenderId = lastSenders[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.no-reply-target") ?: "No reply target found.")
            return
        }

        val target = Bukkit.getPlayer(lastSenderId) ?: run {
            lastSenders.remove(player.uniqueId)
            player.sendMessage(config.getString("messages.target-offline") ?: "Target is offline.")
            return
        }

        sendFormattedMessage(player, target, message, "reply")
        lastSenders[target.uniqueId] = player.uniqueId
    }

    fun sendFormattedMessage(sender: Player, receiver: Player, message: String, type: String) {
        val formatKey = when (type) {
            "whisper" -> "formats.whisper"
            "reply" -> "formats.reply"
            else -> "formats.default"
        }

        val baseFormat = config.getString(formatKey, "&7[{type}] {sender} &7-> &6{receiver}&7: &f{message}") 
            ?: "&7[{type}] {sender} &7-> &6{receiver}&7: &f{message}"
        val format = baseFormat
            .replace("{type}", type.uppercase())
            .replace("{sender}", sender.name)
            .replace("{receiver}", receiver.name)
            .replace("{message}", message)

        sender.sendMessage(format)
        receiver.sendMessage(format)
    }

    private fun sendHelp(player: Player) {
        config.getStringList("messages.help").forEach { msg ->
            player.sendMessage(msg)
        }
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    inner class ChatListener : Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        fun onChat(event: AsyncChatEvent) {
            val player = event.player
            val targetId = activeDMs[player.uniqueId] ?: return
            event.setCancelled(true)

            val runnable = Runnable {
                val target = Bukkit.getPlayer(targetId) ?: run {
                    player.sendMessage(config.getString("messages.target-offline") ?: "Target is offline.")
                    activeDMs.remove(player.uniqueId)
                    dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                        sessions.apply { remove(targetId) }
                    }
                    return@Runnable
                }

		val msg = PlainTextComponentSerializer.plainText().serialize(event.message())
                sendFormattedMessage(player, target, msg, "dm")
                lastSenders[target.uniqueId] = player.uniqueId
            }

            if (isFolia) {
                // Adjust scheduling according to your Folia API usage
                server.scheduler.runTask(this@WhisperChatPlugin, runnable)
            } else {
                Bukkit.getScheduler().runTask(this@WhisperChatPlugin, runnable)
            }
        }
    }
}
