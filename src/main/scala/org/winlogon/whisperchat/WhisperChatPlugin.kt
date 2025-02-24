package org.winlogon.whisperchat

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.*
import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.plugin.java.JavaPlugin
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
        config = config
        isFolia = checkFolia()
        CommandAPI.onEnable(this)
        registerCommands()
        server.pluginManager.registerEvents(ChatListener(this), this)
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
                    .executesPlayer { player, args ->
                        val target = args[0] as Player
                        handleDMStart(player, target)
                    }
            )
            .withSubcommand(
                CommandAPICommand("switch")
                    .withArguments(PlayerArgument("target"))
                    .executesPlayer { player, args ->
                        val target = args[0] as Player
                        handleDMSwitch(player, target)
                    }
            )
            .withSubcommand(
                CommandAPICommand("list")
                    .executesPlayer { player, _ ->
                        handleDMList(player)
                    }
            )
            .withSubcommand(
                CommandAPICommand("leave")
                    .executesPlayer { player, _ ->
                        handleDMLeave(player)
                    }
            )
            .withSubcommand(
                CommandAPICommand("help")
                    .executesPlayer { player, _ ->
                        sendHelp(player)
                    }
            )
            .register()

        // Reply Command
        CommandAPICommand("r")
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer { player, args ->
                val message = args[0] as String
                handleReply(player, message)
            }
            .register()

        // Whisper/Msg Commands
        val whisperCmd = CommandAPICommand("w")
            .withArguments(PlayerArgument("target"))
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer { player, args ->
                val target = args[0] as Player
                val message = args[1] as String
                sendFormattedMessage(player, target, message, "whisper")
            }
        
        CommandAPICommand("msg")
            .withArguments(PlayerArgument("target"))
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(whisperCmd.executor)
            .register()
    }

    private fun handleDMStart(player: Player, target: Player) {
        if (player == target) {
            player.sendMessage(config.getString("messages.self-whisper-error"))
            return
        }

        dmSessions.compute(player.uniqueId) { _, sessions ->
            (sessions ?: mutableSetOf()).apply { add(target.uniqueId) }
        }
        activeDMs[player.uniqueId] = target.uniqueId
        lastSenders[target.uniqueId] = player.uniqueId
        
        player.sendMessage(config.getString("messages.dm-start")
            ?.replace("{target}", target.name))
    }

    private fun handleDMSwitch(player: Player, target: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.no-dm-sessions"))
            return
        }

        if (target.uniqueId in sessions) {
            activeDMs[player.uniqueId] = target.uniqueId
            player.sendMessage(config.getString("messages.dm-switch")
                ?.replace("{target}", target.name))
        } else {
            player.sendMessage(config.getString("messages.invalid-dm-target"))
        }
    }

    private fun handleDMList(player: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.no-dm-sessions"))
            return
        }

        val onlineTargets = sessions.mapNotNull { Bukkit.getPlayer(it)?.name }
        if (onlineTargets.isEmpty()) {
            player.sendMessage(config.getString("messages.no-active-dms"))
            return
        }

        player.sendMessage(config.getString("messages.dm-list-header"))
        onlineTargets.forEach { target ->
            player.sendMessage(config.getString("messages.dm-list-item")
                ?.replace("{target}", target))
        }
    }

    private fun handleDMLeave(player: Player) {
        val targetId = activeDMs[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.not-in-dm"))
            return
        }

        activeDMs.remove(player.uniqueId)
        dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
            sessions.apply { remove(targetId) }
        }
        player.sendMessage(config.getString("messages.dm-left")
            ?.replace("{target}", Bukkit.getPlayer(targetId)?.name ?: "Unknown"))
    }

    private fun handleReply(player: Player, message: String) {
        val lastSenderId = lastSenders[player.uniqueId] ?: run {
            player.sendMessage(config.getString("messages.no-reply-target"))
            return
        }

        val target = Bukkit.getPlayer(lastSenderId) ?: run {
            lastSenders.remove(player.uniqueId)
            player.sendMessage(config.getString("messages.target-offline"))
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

        val format = config.getString(formatKey, "&7[{type}] {sender} &7-> &6{receiver}&7: &f{message}")
            .replace("{type}", type.uppercase())
            .replace("{sender}", sender.name)
            .replace("{receiver}", receiver.name)
            .replace("{message}", message)

        sender.sendMessage(format)
        receiver.sendMessage(format)
    }

    private fun sendHelp(player: Player) {
        config.getStringList("messages.help").forEach(player::sendMessage)
    }

    override fun onDisable() {
        CommandAPI.onDisable()
    }

    inner class ChatListener : Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        fun onChat(event: AsyncPlayerChatEvent) {
            val player = event.player
            val targetId = activeDMs[player.uniqueId] ?: return
            event.isCancelled = true

            val message = event.message
            val runnable = Runnable {
                val target = Bukkit.getPlayer(targetId) ?: run {
                    player.sendMessage(config.getString("messages.target-offline"))
                    activeDMs.remove(player.uniqueId)
                    dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                        sessions.apply { remove(targetId) }
                    }
                    return@Runnable
                }

                sendFormattedMessage(player, target, message, "dm")
                lastSenders[target.uniqueId] = player.uniqueId
            }

            if (isFolia) {
                player.scheduler.run(this@WhisperChatPlugin, { _ -> runnable.run() }, null)
            } else {
                Bukkit.getScheduler().runTask(this@WhisperChatPlugin, runnable)
            }
        }
    }
}
