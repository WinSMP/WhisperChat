package org.winlogon.whisperchat

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.CommandAPIConfig
import dev.jorel.commandapi.CommandAPIPaperConfig

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.whisperchat.MessageFormatter
import org.winlogon.asynccraftr.AsyncCraftr
import org.winlogon.whisperchat.group.GroupManager
import org.winlogon.whisperchat.loggers.*

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

import io.papermc.paper.event.player.AsyncChatEvent

import java.io.File
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

enum class MessageType {
    DM, WHISPER, REPLY
}

sealed class ActiveConversation {
    data class PlayerTarget(val target: UUID) : ActiveConversation()
    data class GroupTarget(val groupName: String) : ActiveConversation()
}

open class WhisperChatPlugin : JavaPlugin() {
    private lateinit var config: FileConfiguration
    private lateinit var formatter: MessageFormatter
    private lateinit var groupManager: GroupManager
    private lateinit var logger: Logger
    private lateinit var context: CommandRegistrarContext

    private val dmSessionManager = DMSessionManager()
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val replacementCommands = arrayOf("w", "msg", "tell")
    private var socialSpyLogger: MessageLogger? = null

    override fun onLoad() {
        saveDefaultConfig()
        config = getConfig()
        logger = getLogger()

        CommandAPI.onLoad(CommandAPIPaperConfig(this).verboseOutput(true))
    }

    override fun onEnable() {
        logger.fine("Enabling WhisperChat...")

        setupSocialSpyLogger()
        formatter = MessageFormatter(dmSessionManager.lastInteraction, config, socialSpyLogger, miniMessage)
        groupManager = GroupManager(config, this)
        context = CommandRegistrarContext(this, dmSessionManager, groupManager, formatter)
        
        server.pluginManager.registerEvents(
            DirectMessageHandler(
                plugin = this,
                logger = logger,
                dmSessionManager = dmSessionManager,
                formatter = formatter,
                config = config,
                groupManager = groupManager,
            ),
            this
        )

        AsyncCraftr.runGlobalTaskTimer(this, Runnable {
            groupManager.expireGroups()
        }, Duration.ofMinutes(1), Duration.ofMinutes(1))
        
        val registrar = CommandManager(config, logger)

        CommandAPI.onEnable()
        registrar.registerCommands(context)
        startExpirationChecker()
    }

    override fun onDisable() {
        logger.fine("Disabling WhisperChat...")
        CommandAPI.onDisable()
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
        val delay = Duration.ofMinutes(1)
        val interval = delay

        AsyncCraftr.runGlobalTaskTimer(this, task, delay, interval)
    }

    private fun checkExpiredSessions() {
        val now = System.currentTimeMillis()
        val expirationPeriod = 30 * 60 * 1000 // 30 minutes

        // get all expired pairs based on last interaction
        val expiredPairs = dmSessionManager.lastInteraction.filter { (_, lastTime) -> 
            now - lastTime >= expirationPeriod 
        }.keys.toList()
        
        expiredPairs.forEach { pair ->
            dmSessionManager.lastInteraction.remove(pair)
            val (a, b) = pair
    
            dmSessionManager.dmSessions.computeIfPresent(a) { _, sessions -> 
                sessions.apply { remove(b) } 
            }
            dmSessionManager.dmSessions.computeIfPresent(b) { _, sessions -> 
                sessions.apply { remove(a) } 
            }
    
            dmSessionManager.activeDMs[a]?.let { conv ->
                if (conv is ActiveConversation.PlayerTarget && conv.target == b) {
                    dmSessionManager.activeDMs.remove(a)
                }
            }
            
            dmSessionManager.activeDMs[b]?.let { conv ->
                if (conv is ActiveConversation.PlayerTarget && conv.target == a) {
                    dmSessionManager.activeDMs.remove(b)
                }
            }
    
            val parsedMessageTemplate = config.getString("messages.session-expired") 
                ?: "Your DM session with {player} has expired due to inactivity."
            
            val aName = Bukkit.getOfflinePlayer(a).name ?: "Unknown"
            val bName = Bukkit.getOfflinePlayer(b).name ?: "Unknown"
    
            Bukkit.getPlayer(a)?.let { playerA ->
                playerA.sendMessage(formatter.parseMessage(
                    parsedMessageTemplate.replace("{player}", bName)
                ))
            }
    
            Bukkit.getPlayer(b)?.let { playerB ->
                playerB.sendMessage(formatter.parseMessage(
                    parsedMessageTemplate.replace("{player}", aName)
                ))
            }
        }
    }
}
