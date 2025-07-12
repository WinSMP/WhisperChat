package org.winlogon.whisperchat

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.arguments.ArgumentSuggestions

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

import java.util.*
import java.io.File
import java.util.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration

enum class MessageType {
    DM, WHISPER, REPLY
}

sealed class ActiveConversation {
    data class PlayerTarget(val target: UUID) : ActiveConversation()
    data class GroupTarget(val groupName: String) : ActiveConversation()
}

class WhisperChatPlugin : JavaPlugin() {
    private val activeDMs = ConcurrentHashMap<UUID, ActiveConversation>()
    private val dmSessions = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val lastSenders = ConcurrentHashMap<UUID, UUID>()
    private val lastInteraction = ConcurrentHashMap<Pair<UUID, UUID>, Long>()
    private lateinit var formatter: MessageFormatter
    private lateinit var logger: Logger
    private lateinit var config: FileConfiguration
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val replacementCommands = arrayOf("w", "msg", "tell")
    private var socialSpyLogger: MessageLogger? = null
    val groupManager = GroupManager

    override fun onEnable() {
        saveDefaultConfig()
        config = getConfig()
        logger = getLogger()

        setupSocialSpyLogger()
        formatter = MessageFormatter(lastInteraction, config, socialSpyLogger, miniMessage)
        
        server.pluginManager.registerEvents(
            DirectMessageHandler(this, activeDMs, dmSessions, lastSenders, formatter, config), 
            this
        )

        AsyncCraftr.runGlobalTaskTimer(this, Runnable {
            groupManager.expireGroups()
        }, Duration.ofMinutes(1), Duration.ofMinutes(1))
        
        registerCommands()
        startExpirationChecker()

        groupManager.loadNouns()
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

    // TODO: move command registration logic to separate class
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

        registerGroupCommands()
    }

    private fun registerGroupCommands() {
        CommandAPICommand("dm")
            .withSubcommand(
                CommandAPICommand("group")
                    .withSubcommand(
                        CommandAPICommand("create")
                            .executesPlayer(PlayerCommandExecutor { player, _ ->
                                val group = groupManager.createGroup(player)
                                formatter.sendLegacyMessage(
                                    player,
                                    "messages.group-created",
                                    "Created group: ${group.name} (Expires in 24 hours)",
                                    Pair("{group}", group.name)
                                )
                            })
                    )
                    .withSubcommand(
                        CommandAPICommand("leave")
                            .executesPlayer(PlayerCommandExecutor { player, _ ->
                                if (groupManager.leaveGroup(player)) {
                                    formatter.sendLegacyMessage(player, "messages.group-left", "Left group")
                                } else {
                                    formatter.sendLegacyMessage(player, "messages.not-in-group", "Not in a group")
                                }
                            })
                    )
                    .withSubcommand(
                        CommandAPICommand("delete")
                        .withArguments(
                            StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings { info ->
                                val sender = info.sender()
                                if (sender !is Player) emptyArray()
                                else groupManager.groups.values
                                    .filter { it.owner == sender.uniqueId }
                                    .map { it.name }
                                    .toTypedArray()
                            })
                        )
                        .executesPlayer(PlayerCommandExecutor { player, args ->
                            val name = args["name"] as String
                            if (groupManager.deleteGroup(name, player)) {
                                formatter.sendLegacyMessage(player, "messages.group-deleted", "Deleted group $name")
                            } else {
                                formatter.sendLegacyMessage(player, "messages.cannot-delete-group", "Cannot delete group")
                            }
                        })
                    )
                    .withSubcommand(
                        CommandAPICommand("join")
                            .withArguments(StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings { _ ->
                                groupManager.groups.values.map { it.name }.toTypedArray()
                            }))
                            .executesPlayer(PlayerCommandExecutor { player, args ->
                                val name = args["name"] as String
                                if (groupManager.joinGroup(player, name)) {
                                    activeDMs[player.uniqueId] = ActiveConversation.GroupTarget(name)
                                    formatter.sendLegacyMessage(player, "messages.group-joined", "Joined group $name")
                                } else {
                                    formatter.sendLegacyMessage(player, "messages.cannot-join-group", "Cannot join group")
                                }
                            })
                    )
                    .withSubcommand(
                        CommandAPICommand("switch")
                            .withArguments(StringArgument("name").replaceSuggestions(ArgumentSuggestions.strings { _ ->
                                groupManager.groups.values.map { it.name }.toTypedArray()
                            }))
                            .executesPlayer(PlayerCommandExecutor { player, args ->
                                val name = args["name"] as String
                                if (groupManager.playerGroups[player.uniqueId] == name) {
                                    activeDMs[player.uniqueId] = ActiveConversation.GroupTarget(name)
                                    formatter.sendLegacyMessage(player, "messages.group-dm-switched", "Now chatting in group: $name")
                                } else {
                                    formatter.sendLegacyMessage(player, "messages.not-in-group", "You're not in this group")
                                }
                            })
                    )
                    .withSubcommand(
                        CommandAPICommand("list-current")
                            .executesPlayer(PlayerCommandExecutor { player, _ ->
                                val groupName = groupManager.playerGroups[player.uniqueId]
                                if (groupName == null) {
                                    formatter.sendLegacyMessage(player, "messages.not-in-group", "Not in a group")
                                    return@PlayerCommandExecutor
                                }
                             
                                val group = groupManager.groups[groupName]!!
                                formatter.sendLegacyMessage(player, "messages.group-members", "Group: ${group.name}")
                             
                                val owner = Bukkit.getPlayer(group.owner)?.name ?: "Offline"
                                formatter.sendLegacyMessage(player, "messages.group-owner", "Owner: $owner")
                             
                                val members = group.members
                                    .filter { it != group.owner }
                                    .mapNotNull { Bukkit.getPlayer(it)?.name }
                             
                                if (members.isEmpty()) {
                                    formatter.sendLegacyMessage(player, "messages.group-no-members", "No other members")
                                } else {
                                    formatter.sendLegacyMessage(player, "messages.group-members-list", "Members:")
                                    members.forEach { member ->
                                        formatter.sendLegacyMessage(player, "messages.group-member", "- $member")
                                    }
                                }
                            }
                    )
                )
            )
            .register()
    }

    private fun reloadWhisperConfig() {
        reloadConfig()
        config = getConfig()
        setupSocialSpyLogger()
        formatter = MessageFormatter(lastInteraction, config, socialSpyLogger, miniMessage)
        logger.info("Reloaded WhisperChat config successfully.")
    }

    // TODO: consider moving this to a separate class (which keeps track of individual DMs)
    private fun handleDMStart(player: Player, target: Player) {
        if (player == target) {
            formatter.sendLegacyMessage(player, "messages.self-whisper-error", "You cannot whisper yourself!")
            return
        }
    
        dmSessions.compute(player.uniqueId) { _, sessions ->
            (sessions ?: mutableSetOf()).apply { add(target.uniqueId) }
        }

        activeDMs[player.uniqueId] = ActiveConversation.PlayerTarget(target.uniqueId)
        lastSenders[target.uniqueId] = player.uniqueId
        updateLastInteraction(player, target)
    
        formatter.sendLegacyMessage(player, "messages.dm-start", "DM started with {target}", Pair("{target}", target.name))
    }

    private fun handleDMSwitch(player: Player, target: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.no-dm-sessions", "No DM sessions found.")
            return
        }
    
        if (target.uniqueId in sessions) {
            activeDMs[player.uniqueId] = ActiveConversation.PlayerTarget(target.uniqueId)
            formatter.sendLegacyMessage(player, "messages.dm-switch", "Switched DM to {target}", Pair("{target}", target.name))
        } else {
            formatter.sendLegacyMessage(player, "messages.invalid-dm-target", "Invalid DM target.")
        }
    }

    private fun handleDMList(player: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.no-dm-sessions", "No DM sessions found.")
            return
        }

        val onlineTargets = sessions.mapNotNull { Bukkit.getPlayer(it)?.name }
        if (onlineTargets.isEmpty()) {
            formatter.sendLegacyMessage(player, "messages.no-active-dms", "No active DMs.")
            return
        }

        formatter.sendLegacyMessage(player, "messages.dm-list-header", "Active DMs:")
        onlineTargets.forEach { target ->
            formatter.sendLegacyMessage(player, "messages.dm-list-item", "{target}", Pair("{target}", target))
        }
    }

    
    private fun handleDMLeave(player: Player) {
        val activeConv = activeDMs[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.not-in-dm", "You are not in a DM.")
            return
        }
    
        when (activeConv) {
            is ActiveConversation.PlayerTarget -> {
                val targetId = activeConv.target
                activeDMs.remove(player.uniqueId)
                dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                    sessions.apply { remove(targetId) }
                }
                formatter.sendLegacyMessage(
                    player,
                    "messages.dm-left",
                    "Left DM with {target}",
                    Pair("{target}", Bukkit.getPlayer(targetId)?.name ?: "Unknown")
                )
            }
            is ActiveConversation.GroupTarget -> {
                formatter.sendLegacyMessage(player, "messages.cannot-leave-group-with-dm", "Use /dm group leave to leave groups")
            }
        }
    }

    private fun handleReply(player: Player, message: String) {
        val lastSenderId: UUID = lastSenders[player.uniqueId] ?: run {
            formatter.sendLegacyMessage(player, "messages.no-reply-target", "No reply target found.")
            return
        }
    
        val target = Bukkit.getPlayer(lastSenderId) ?: run {
            lastSenders.remove(player.uniqueId)
            formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.")
            return
        }
    
        formatter.sendFormattedMessage(player, target, message, MessageType.REPLY)
        lastSenders[target.uniqueId] = player.uniqueId
        updateLastInteraction(player, target)
    }

    private fun sendHelp(player: Player) {
        config.getStringList("messages.help").forEach { msg ->
            player.sendMessage(formatter.parseMessage(msg))
        }
    }

    fun updateLastInteraction(sender: Player, receiver: Player) {
        val pair = if (sender.uniqueId < receiver.uniqueId) {
            Pair(sender.uniqueId, receiver.uniqueId)
        } else {
            Pair(receiver.uniqueId, sender.uniqueId)
        }
        lastInteraction[pair] = System.currentTimeMillis()
    }
}
