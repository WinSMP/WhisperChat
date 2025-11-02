package org.winlogon.whisperchat

import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.*
import org.bukkit.command.CommandSender

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.winlogon.whisperchat.group.GroupManager
import org.winlogon.whisperchat.WhisperChatPlugin

import java.util.logging.Logger
import java.util.UUID

data class CommandRegistrarContext(
    var plugin: JavaPlugin,
    val dmSessionManager: DMSessionManager,
    val groupManager: GroupManager,
    val formatter: MessageFormatter,
)

interface CommandRegistrar {
    /**
     * Register commands using the initializer's returned context.
     */
    fun registerCommands(ctx: CommandRegistrarContext)
}

class CommandManager(val config: FileConfiguration, val logger: Logger) : CommandRegistrar {
    override fun registerCommands(ctx: CommandRegistrarContext) {
        logger.fine("Registering commands...")
        registerDMCommands(ctx)
        registerWhisperCommands(ctx)
        registerReplyCommand(ctx)
        registerGroupCommands(ctx)
    }

    private fun registerDMCommands(ctx: CommandRegistrarContext) {
        commandAPICommand("dm") {
            subcommand("start") {
                entitySelectorArgumentOnePlayer("target")
                playerExecutor { player, args ->
                    val target = args["target"] as Player
                    handleDMStart(ctx, player, target)
                }
            }
            subcommand("switch") {
                entitySelectorArgumentOnePlayer("target")
                playerExecutor { player, args ->
                    val target = args["target"] as Player
                    handleDMSwitch(ctx, player, target)
                }
            }
            subcommand("list") {
                playerExecutor { player, _ ->
                    handleDMList(ctx, player)
                }
            }
            subcommand("leave") {
                playerExecutor { player, _ ->
                    handleDMLeave(ctx, player)
                }
            }
            subcommand("help") {
                playerExecutor { player, _ ->
                    sendHelp(ctx, player)
                }
            }
        }
    }

    private fun registerWhisperCommands(ctx: CommandRegistrarContext) {
        commandAPICommand("w") {
            withAliases("msg", "tell")
            entitySelectorArgumentOnePlayer("target")
            greedyStringArgument("message")
            playerExecutor { player, args ->
                val target = args["target"] as Player
                val message = args["message"] as String
                ctx.formatter.sendFormattedMessage(player, target, message, MessageType.WHISPER)
            }
        }
    }

    private fun registerReplyCommand(ctx: CommandRegistrarContext) {
        commandAPICommand("r") {
            greedyStringArgument("message")
            playerExecutor { player, args ->
                val message = args["message"] as String
                handleReply(ctx, player, message)
            }
        }
    }

    private fun registerGroupCommands(ctx: CommandRegistrarContext) {
        commandAPICommand("dm") {
            subcommand("group") {
                subcommand("create") {
                    playerExecutor { player, _ ->
                        val group = ctx.groupManager.createGroup(player)
                        ctx.formatter.sendLegacyMessage(
                            player,
                            "messages.group-created",
                            "Created group: ${group.name} (Expires in 24 hours)",
                            Pair("{group}", group.name)
                        )
                    }
                }
                subcommand("leave") {
                    playerExecutor { player, _ ->
                        if (ctx.groupManager.leaveGroup(player)) {
                            ctx.formatter.sendLegacyMessage(player, "messages.group-left", "Left group")
                        } else {
                            ctx.formatter.sendLegacyMessage(player, "messages.not-in-group", "Not in a group")
                        }
                    }
                }
                subcommand("delete") {
                    stringArgument("name") {
                        replaceSuggestions(ArgumentSuggestions.strings { info ->
                            val sender = info.sender()
                            if (sender !is Player) emptyArray()
                            else ctx.groupManager.groups.values
                                .filter { it.owner == sender.uniqueId }
                                .map { it.name }
                                .toTypedArray()
                        })
                    }
                    playerExecutor { player, args ->
                        val name = args["name"] as String
                        if (ctx.groupManager.deleteGroup(name, player)) {
                            ctx.formatter.sendLegacyMessage(player, "messages.group-deleted", "Deleted group $name")
                        } else {
                            ctx.formatter.sendLegacyMessage(player, "messages.cannot-delete-group", "Cannot delete group")
                        }
                    }
                }
                subcommand("join") {
                    stringArgument("name") {
                        replaceSuggestions(ArgumentSuggestions.strings { _ ->
                            ctx.groupManager.groups.values.map { it.name }.toTypedArray()
                        })
                    }
                    playerExecutor { player, args ->
                        val name = args["name"] as String
                        if (ctx.groupManager.joinGroup(player, name)) {
                            ctx.dmSessionManager.activeDMs[player.uniqueId] = ActiveConversation.GroupTarget(name)
                            ctx.formatter.sendLegacyMessage(player, "messages.group-joined", "Joined group $name", "{group}" to name)
                        } else {
                            ctx.formatter.sendLegacyMessage(player, "messages.cannot-join-group", "Cannot join group")
                        }
                    }
                }
                subcommand("switch") {
                    stringArgument("name") {
                        replaceSuggestions(ArgumentSuggestions.strings { _ ->
                            ctx.groupManager.groups.values.map { it.name }.toTypedArray()
                        })
                    }
                    playerExecutor { player, args ->
                        val name = args["name"] as String
                        if (ctx.groupManager.playerGroups[player.uniqueId] == name) {
                            ctx.dmSessionManager.activeDMs[player.uniqueId] = ActiveConversation.GroupTarget(name)
                            ctx.formatter.sendLegacyMessage(player, "messages.group-dm-switched", "Now chatting in group: $name", "{group}" to name)
                        } else {
                            ctx.formatter.sendLegacyMessage(player, "messages.not-in-group", "You're not in this group")
                        }
                    }
                }
                subcommand("list-current") {
                    playerExecutor { player, _ ->
                        val groupName = ctx.groupManager.playerGroups[player.uniqueId]
                        if (groupName == null) {
                            ctx.formatter.sendLegacyMessage(player, "messages.not-in-group", "Not in a group")
                            return@playerExecutor
                        }

                        val group = ctx.groupManager.groups[groupName]!!
                        ctx.formatter.sendLegacyMessage(player, "messages.group-members", "Group: ${group.name}")

                        val owner = Bukkit.getPlayer(group.owner)?.name ?: "Offline"
                        ctx.formatter.sendLegacyMessage(player, "messages.group-owner", "Owner: $owner")

                        val members = group.members
                            .filter { it != group.owner }
                            .mapNotNull { Bukkit.getPlayer(it)?.name }

                        if (members.isEmpty()) {
                            ctx.formatter.sendLegacyMessage(player, "messages.group-no-members", "No other members")
                        } else {
                            ctx.formatter.sendLegacyMessage(player, "messages.group-members-list", "Members:")
                            members.forEach { member ->
                                ctx.formatter.sendLegacyMessage(player, "messages.group-member", "- $member")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleDMStart(ctx: CommandRegistrarContext, player: Player, target: Player) {
        logger.fine("Handling DM start for ${player.name} to ${target.name}")
        if (player == target) {
            ctx.formatter.sendLegacyMessage(player, "messages.self-whisper-error", "You cannot whisper yourself!")
            return
        }
    
        ctx.dmSessionManager.dmSessions.compute(player.uniqueId) { _, sessions ->
            (sessions ?: mutableSetOf()).apply { add(target.uniqueId) }
        }

        ctx.dmSessionManager.apply {
            activeDMs[player.uniqueId] = ActiveConversation.PlayerTarget(target.uniqueId)
            lastSenders[target.uniqueId] = player.uniqueId
            updateLastInteraction(player, target)
        }
    
        ctx.formatter.sendLegacyMessage(player, "messages.dm-start", "DM started with {target}", Pair("{target}", target.name))
    }

    private fun handleDMSwitch(ctx: CommandRegistrarContext, player: Player, target: Player) {
        logger.fine("Handling DM switch for ${player.name} to ${target.name}")
        val sessions = ctx.dmSessionManager.dmSessions[player.uniqueId] ?: run {
            ctx.formatter.sendLegacyMessage(player, "messages.no-dm-sessions", "No DM sessions found.")
            return
        }
    
        if (target.uniqueId in sessions) {
            ctx.dmSessionManager.activeDMs[player.uniqueId] = ActiveConversation.PlayerTarget(target.uniqueId)
            ctx.formatter.sendLegacyMessage(player, "messages.dm-switch", "Switched DM to {target}", Pair("{target}", target.name))
        } else {
            ctx.formatter.sendLegacyMessage(player, "messages.invalid-dm-target", "Invalid DM target.")
        }
    }

    private fun handleDMList(ctx: CommandRegistrarContext, player: Player) {
        logger.fine("Handling DM list for ${player.name}")
        val sessions = ctx.dmSessionManager.dmSessions[player.uniqueId] ?: run {
            ctx.formatter.sendLegacyMessage(player, "messages.no-dm-sessions", "No DM sessions found.")
            return
        }

        val onlineTargets = sessions.mapNotNull { Bukkit.getPlayer(it)?.name }
        if (onlineTargets.isEmpty()) {
            ctx.formatter.sendLegacyMessage(player, "messages.no-active-dms", "No active DMs.")
            return
        }

        ctx.formatter.sendLegacyMessage(player, "messages.dm-list-header", "Active DMs:")
        onlineTargets.forEach { target ->
            ctx.formatter.sendLegacyMessage(player, "messages.dm-list-item", "{target}", Pair("{target}", target))
        }
    }

    
    private fun handleDMLeave(ctx: CommandRegistrarContext, player: Player) {
        logger.fine("Handling DM leave for ${player.name}")
        val activeConv = ctx.dmSessionManager.activeDMs[player.uniqueId] ?: run {
            ctx.formatter.sendLegacyMessage(player, "messages.not-in-dm", "You are not in a DM.")
            return
        }
    
        when (activeConv) {
            is ActiveConversation.PlayerTarget -> {
                val targetId = activeConv.target
                ctx.dmSessionManager.activeDMs.remove(player.uniqueId)
                ctx.dmSessionManager.dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                    sessions.apply { remove(targetId) }
                }
                ctx.formatter.sendLegacyMessage(
                    player,
                    "messages.dm-left",
                    "Left DM with {target}",
                    Pair("{target}", Bukkit.getPlayer(targetId)?.name ?: "Unknown")
                )
            }
            is ActiveConversation.GroupTarget -> {
                ctx.formatter.sendLegacyMessage(player, "messages.cannot-leave-group-with-dm", "Use /dm group leave to leave groups")
            }
        }
    }

    private fun handleReply(ctx: CommandRegistrarContext, player: Player, message: String) {
        logger.fine("Handling reply for ${player.name}")
        val lastSenderId: UUID = ctx.dmSessionManager.lastSenders[player.uniqueId] ?: run {
            ctx.formatter.sendLegacyMessage(player, "messages.no-reply-target", "No reply target found.")
            return
        }
    
        val target = Bukkit.getPlayer(lastSenderId) ?: run {
            ctx.dmSessionManager.lastSenders.remove(player.uniqueId)
            ctx.formatter.sendLegacyMessage(player, "messages.target-offline", "Target is offline.")
            return
        }
    
        ctx.formatter.sendFormattedMessage(player, target, message, MessageType.REPLY)
        ctx.dmSessionManager.apply {
            lastSenders[target.uniqueId] = player.uniqueId
            updateLastInteraction(player, target)
        }
    }

    private fun sendHelp(ctx: CommandRegistrarContext, player: Player) {
        logger.fine("Sending help to ${player.name}")
        config.getStringList("messages.help").forEach { msg ->
            player.sendMessage(ctx.formatter.parseMessage(msg))
        }
    }
}
