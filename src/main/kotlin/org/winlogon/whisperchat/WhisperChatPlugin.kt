package org.winlogon.whisperchat

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.PlayerArgument

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.whisperchat.MessageFormatter
import org.winlogon.whisperchat.loggers.*

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

import io.papermc.paper.event.player.AsyncChatEvent

import java.util.*
import java.io.File
import java.util.logging.Logger
import java.util.concurrent.ConcurrentHashMap

enum class MessageType {
    DM, WHISPER, REPLY
}

class WhisperChatPlugin : JavaPlugin() {
    private val activeDMs = ConcurrentHashMap<UUID, UUID>()
    private val dmSessions = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val lastSenders = ConcurrentHashMap<UUID, UUID>()
    private val lastInteraction = ConcurrentHashMap<Pair<UUID, UUID>, Long>()
    private var isFolia = false
    private lateinit var formatter: MessageFormatter
    private lateinit var logger: Logger
    private lateinit var config: FileConfiguration
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val replacementCommands = arrayOf("w", "msg", "tell")
    private var socialSpyLogger: MessageLogger? = null

    override fun onEnable() {
        saveDefaultConfig()
        config = getConfig()
        logger = getLogger()
        isFolia = try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

        setupSocialSpyLogger()
        formatter = MessageFormatter(config, socialSpyLogger, miniMessage)
        
        server.pluginManager.registerEvents(
            DirectMessageHandler(this, activeDMs, dmSessions, lastSenders, formatter, config, isFolia), 
            this
        )
        
        registerCommands()
        startExpirationChecker()
    }

    private fun setupSocialSpyLogger() {
        socialSpyLogger = when (config.getString("socialspy")?.lowercase()) {
            "console" -> ConsoleLogger()
            "file" -> {
                val logDir = File(dataFolder, "socialspy").apply { mkdirs() }
                DiskLogger(logDir)
            }
            else -> null
        }
    }

    private fun startExpirationChecker() {
        val task = Runnable { checkExpiredSessions() }
        val delay = 20L * 60 // 1 minute in ticks
        val interval = 20L * 60 // 1 minute in ticks

        if (isFolia) {
            server.globalRegionScheduler.runAtFixedRate(this, { _ -> task.run() }, delay, interval)
        } else {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, task, delay, interval)
        }
    }

    private fun checkExpiredSessions() {
        val now = System.currentTimeMillis()
        val expirationPeriod = 30 * 60 * 1000
        val expiredPairs = lastInteraction.filter { (_, lastTime) -> now - lastTime >= expirationPeriod }.keys.toList()
    
        expiredPairs.forEach { pair ->
            lastInteraction.remove(pair)
            val (a, b) = pair
    
            dmSessions.computeIfPresent(a) { _, sessions -> sessions.apply { remove(b) } }
            dmSessions.computeIfPresent(b) { _, sessions -> sessions.apply { remove(a) } }
    
            if (activeDMs[a] == b) activeDMs.remove(a)
            if (activeDMs[b] == a) activeDMs.remove(b)
    
            val parsedMessageTemplate = config.getString("messages.session-expired") ?: "Your DM session with {player} has expired due to inactivity."
            
            val aName = Bukkit.getOfflinePlayer(a).name ?: "Unknown"
            val bName = Bukkit.getOfflinePlayer(b).name ?: "Unknown"
    
            Bukkit.getPlayer(a)?.let { playerA ->
                val message = formatter.parseMessage(parsedMessageTemplate.replace("{player}", bName))
                playerA.sendMessage(message)
            }
    
            Bukkit.getPlayer(b)?.let { playerB ->
                val message = formatter.parseMessage(parsedMessageTemplate.replace("{player}", aName))
                playerB.sendMessage(message)
            }
        }
    }

    private fun registerCommands() {
        logger.info("Unregistering vanilla commands: ${replacementCommands.contentToString()}")
        for (command in replacementCommands) {
            logger.info("Replacing command: $command")
            CommandAPI.unregister(command)
        }
       
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
            .withSubcommand(
                CommandAPICommand("reload")
                    .withPermission("whisperchat.admin")
                    .executes(CommandExecutor { sender, _ ->
                        reloadWhisperConfig()
                        sender.sendRichMessage("<dark_green>WhisperChat config reloaded.")
                    })
            )
            .register()

        val whisperExecutor = PlayerCommandExecutor { player, args ->
            val target = args[0] as Player
            val message = args[1] as String
            formatter.sendFormattedMessage(player, target, message, MessageType.WHISPER)
        }

        CommandAPICommand("w")
            .withAliases("msg", "tell")
            .withArguments(PlayerArgument("target"))
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(whisperExecutor)
            .register()
       
        CommandAPICommand("r")
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val message = args[0] as String
                handleReply(player, message)
            })
            .register()
    }

    private fun reloadWhisperConfig() {
        reloadConfig()
        config = getConfig()
        setupSocialSpyLogger()
        formatter = MessageFormatter(config, socialSpyLogger, miniMessage)
        logger.info("Reloaded WhisperChat config successfully.")
    }

    private fun handleDMStart(player: Player, target: Player) {
        if (player == target) {
            formatter.sendLegacyMessage(player, "messages.self-whisper-error", "You cannot whisper yourself!", null)
            return
        }

        dmSessions.compute(player.uniqueId) { _, sessions ->
            (sessions ?: mutableSetOf()).apply { add(target.uniqueId) }
        }
        activeDMs[player.uniqueId] = target.uniqueId
        lastSenders[target.uniqueId] = player.uniqueId
        updateLastInteraction(player, target)

        formatter.sendLegacyMessage(player, "messages.dm-start", "DM started with {target}", Pair("{target}", target.name))
    }

    private fun handleDMSwitch(player: Player, target: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.no-dm-sessions", "No DM sessions found.", null)
            return
        }

        if (target.uniqueId in sessions) {
            activeDMs[player.uniqueId] = target.uniqueId
            formatter.sendLegacyMessage(player, "messages.dm-switch", "Switched DM to {target}", Pair("{target}", target.name))
        } else {
            formatter.sendLegacyMessage(player, "messages.invalid-dm-target", "Invalid DM target.", null)
        }
    }

    private fun handleDMList(player: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.no-dm-sessions", "No DM sessions found.", null)
            return
        }

        val onlineTargets = sessions.mapNotNull { Bukkit.getPlayer(it)?.name }
        if (onlineTargets.isEmpty()) {
            formatter.sendLegacyMessage(player, "messages.no-active-dms", "No active DMs.", null)
            return
        }

        formatter.sendLegacyMessage(player, "messages.dm-list-header", "Active DMs:", null)
        onlineTargets.forEach { target ->
            formatter.sendLegacyMessage(player, "messages.dm-list-item", "{target}", Pair("{target}", target))
        }
    }

    private fun handleDMLeave(player: Player) {
        val targetId = activeDMs[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.not-in-dm", "You are not in a DM.", null)
            return
        }

        activeDMs.remove(player.uniqueId)
        dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
            sessions.apply { remove(targetId) }
        }
        formatter.sendLegacyMessage(
            player,
            "messages.dm-left",
            "Left DM with {target}",
            Pair("{target}", Bukkit.getPlayer(targetId)?.getName() ?: "Unknown")
        )
    }

    private fun handleReply(player: Player, message: String) {
        val lastSenderId = lastSenders[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.no-reply-target", "No reply target found.", null)
            return
        }

        val target = Bukkit.getPlayer(lastSenderId) ?: run {
            lastSenders.remove(player.uniqueId)
            formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.", null)
            return
        }

        formatter.sendFormattedMessage(player, target, message, MessageType.REPLY)
        lastSenders[target.uniqueId] = player.uniqueId
    }

    private fun sendHelp(player: Player) {
        config.getStringList("messages.help").forEach { msg ->
            player.sendMessage(formatter.parseMessage(msg))
        }
    }

    private fun updateLastInteraction(sender: Player, receiver: Player) {
        val pair = if (sender.uniqueId < receiver.uniqueId) {
            Pair(sender.uniqueId, receiver.uniqueId)
        } else {
            Pair(receiver.uniqueId, sender.uniqueId)
        }
        lastInteraction[pair] = System.currentTimeMillis()
    }
}
