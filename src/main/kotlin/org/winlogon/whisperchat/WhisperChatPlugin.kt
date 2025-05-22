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
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import io.papermc.paper.event.player.AsyncChatEvent

import java.util.*
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
    private lateinit var logger: Logger
    private lateinit var config: FileConfiguration
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val replacementCommands = arrayOf("w", "msg", "tell")

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

        registerCommands()
        server.pluginManager.registerEvents(ChatListener(), this)
        startExpirationChecker()
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
                val message = parseMessage(parsedMessageTemplate.replace("{player}", bName))
                playerA.sendMessage(message)
            }
    
            Bukkit.getPlayer(b)?.let { playerB ->
                val message = parseMessage(parsedMessageTemplate.replace("{player}", aName))
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
	    .withPermission("whisperchat.dm")
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
                        sender.sendMessage(
                            Component.text("WhisperChat config reloaded.", NamedTextColor.DARK_GREEN)
                        )
                    })
            )
            .register()

       
        val whisperExecutor = PlayerCommandExecutor { player, args ->
            val target = args[0] as Player
            val message = args[1] as String
            sendFormattedMessage(player, target, message, MessageType.WHISPER)
        }

        CommandAPICommand("w")
	    .withPermission("whisperchat.direct")
            .withAliases("msg", "tell")
            .withArguments(PlayerArgument("target"))
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(whisperExecutor)
            .register()
       
        CommandAPICommand("r")
	    .withPermission("whisperchat.direct")
            .withArguments(GreedyStringArgument("message"))
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val message = args[0] as String
                handleReply(player, message)
            })
            .register()
    }

    private fun reloadWhisperConfig() {
        config = getConfig()
        logger.info("Reloaded WhisperChat config succesfully.")
    }

    private fun handleDMStart(player: Player, target: Player) {
        if (player == target) {
            player.sendLegacyMessage("messages.self-whisper-error", "You cannot whisper yourself!", null)
            return
        }

        dmSessions.compute(player.uniqueId) { _, sessions ->
            (sessions ?: mutableSetOf()).apply { add(target.uniqueId) }
        }
        activeDMs[player.uniqueId] = target.uniqueId
        lastSenders[target.uniqueId] = player.uniqueId
        updateLastInteraction(player, target)

        player.sendLegacyMessage("messages.dm-start", "DM started with {target}", Pair("{target}", target.name))
    }

    private fun handleDMSwitch(player: Player, target: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            player.sendLegacyMessage("messages.no-dm-sessions", "No DM sessions found.", null)
            return
        }

        if (target.uniqueId in sessions) {
            activeDMs[player.uniqueId] = target.uniqueId
            player.sendLegacyMessage("messages.dm-switch", "Switched DM to {target}", Pair("{target}", target.name))
        } else {
            player.sendLegacyMessage("messages.invalid-dm-target", "Invalid DM target.", null)
        }
    }

    private fun handleDMList(player: Player) {
        val sessions = dmSessions[player.uniqueId] ?: run {
            player.sendLegacyMessage("messages.no-dm-sessions", "No DM sessions found.", null)
            return
        }

        val onlineTargets = sessions.mapNotNull { Bukkit.getPlayer(it)?.name }
        if (onlineTargets.isEmpty()) {
            player.sendLegacyMessage("messages.no-active-dms", "No active DMs.", null)
            return
        }

        player.sendLegacyMessage("messages.dm-list-header", "Active DMs:", null)
        onlineTargets.forEach { target ->
            player.sendLegacyMessage("messages.dm-list-item", "{target}", Pair("{target}", target))
        }
    }

    private fun handleDMLeave(player: Player) {
        val targetId = activeDMs[player.uniqueId] ?: run {
            player.sendLegacyMessage("messages.not-in-dm", "You are not in a DM.", null)
            return
        }

        activeDMs.remove(player.uniqueId)
        dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
            sessions.apply { remove(targetId) }
        }
        player.sendLegacyMessage(
            "messages.dm-left",
            "Left DM with {target}",
            Pair("{target}", Bukkit.getPlayer(targetId)?.getName() ?: "Unknown")
        )
    }

    private fun handleReply(player: Player, message: String) {
        val lastSenderId = lastSenders[player.uniqueId] ?: run {
            player.sendLegacyMessage("messages.no-reply-target", "No reply target found.", null)
            return
        }

        val target = Bukkit.getPlayer(lastSenderId) ?: run {
            lastSenders.remove(player.uniqueId)
            player.sendLegacyMessage("messages.target-offline", "Target is offline.", null)
            return
        }

        sendFormattedMessage(player, target, message, MessageType.REPLY)
        lastSenders[target.uniqueId] = player.uniqueId
    }

    fun sendFormattedMessage(sender: Player, receiver: Player, message: String, type: MessageType) {
        val formatKey = when (type) {
            MessageType.WHISPER -> "formats.whisper"
            MessageType.REPLY -> "formats.reply"
            MessageType.DM -> "formats.dm"
        }

        val baseFormat = config.getString(formatKey) ?: "&7[{type}] {sender} &7-> &6{receiver}&7: &f{message}"
        val formatted = baseFormat
            .replace("{type}", type.name.uppercase())
            .replace("{sender}", sender.name)
            .replace("{receiver}", receiver.name)
            .replace("{message}", message)

        val component = parseMessage(formatted)
        sender.sendMessage(component)
        receiver.sendMessage(component)

        if (config.getBoolean("socialspy.console") && type == MessageType.DM) {
            val prefixPlaceholder = Placeholder.component(
                "prefix", Component.text("[WhisperChat]", NamedTextColor.DARK_BLUE)
            )
            val typePlaceholder = Placeholder.component(
                "type", Component.text("${type.name.uppercase()}", NamedTextColor.DARK_GREEN)
            )
            val messagePlaceholder = Placeholder.component("message", component)

            Bukkit.getConsoleSender().sendRichMessage(
                "<prefix> > <type>: <message>",
                prefixPlaceholder, typePlaceholder, messagePlaceholder
            )
            // TODO: save to cache probably
        }

        updateLastInteraction(sender, receiver)
        lastSenders[receiver.uniqueId] = sender.uniqueId
    }

    private fun sendHelp(player: Player) {
        config.getStringList("messages.help").forEach { msg ->
            player.sendMessage(parseMessage(msg))
        }
    }

    inner class ChatListener : Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        fun onChat(event: AsyncChatEvent) {
            val player = event.player
            val targetId = activeDMs[player.uniqueId] ?: return
    
            val msg = PlainTextComponentSerializer.plainText().serialize(event.message())
            val prefix = config.getString("public-prefix") ?: "!"
    
            if (msg.startsWith(prefix) && msg.length > prefix.length && !msg[prefix.length].isWhitespace()) {
                val newMessage = msg.substring(prefix.length).trim()
                val component = parseMessage(newMessage)
                event.message(component)
                event.isCancelled = false
                return
            }
    
            event.isCancelled = true
    
            val runnable = Runnable {
                val target = Bukkit.getPlayer(targetId) ?: run {
                    player.sendLegacyMessage("messages.target-offline", "Target is offline.", null)
                    activeDMs.remove(player.uniqueId)
                    dmSessions.computeIfPresent(player.uniqueId) { _, sessions ->
                        sessions.apply { remove(targetId) }
                    }
                    return@Runnable
                }
    
                sendFormattedMessage(player, target, msg, MessageType.DM)
                lastSenders[target.uniqueId] = player.uniqueId
            }
    
            if (isFolia) {
                player.scheduler.run(this@WhisperChatPlugin, { _ -> runnable.run() }, null)
            } else {
                Bukkit.getScheduler().runTask(this@WhisperChatPlugin, runnable)
            }
        }
    }

    /**
     * Converts legacy ampersand codes to MiniMessage tags and deserializes the string into a Component.
     *
     * This function supports both legacy codes (e.g. &a, &c) and native MiniMessage tags.
     */
    private fun parseMessage(input: String): Component {
        return miniMessage.deserialize(legacyToMiniMessage(input))
    }

    /**
     * Converts all legacy ampersand color/format codes in the input string to equivalent MiniMessage tags.
     *
     * For example, "&aHello &cWorld" becomes "<green>Hello <red>World".
     */
     private fun legacyToMiniMessage(input: String): String {
         val colorMap = mapOf(
             '0' to "black",
             '1' to "dark_blue",
             '2' to "dark_green",
             '3' to "dark_aqua",
             '4' to "dark_red",
             '5' to "dark_purple",
             '6' to "gold",
             '7' to "gray",
             '8' to "dark_gray",
             '9' to "blue",
             'a' to "green",
             'b' to "aqua",
             'c' to "red",
             'd' to "light_purple",
             'e' to "yellow",
             'f' to "white",
             'k' to "obfuscated",
             'l' to "bold",
             'm' to "strikethrough",
             'n' to "underlined",
             'o' to "italic",
             'r' to "reset"
         )
     
         val regex = "&([0-9a-fk-or])".toRegex(RegexOption.IGNORE_CASE)
         return regex.replace(input) { matchResult ->
             val code = matchResult.groupValues[1].lowercase()
             val tag = colorMap[code.first()]
             if (tag != null) "<$tag>" else matchResult.value
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

    private fun Player.sendLegacyMessage(messageKey: String, fallback: String, replacement: Pair<String, String>?) {
        val rawTemplate = config.getString(messageKey) ?: fallback

        val applied = if (replacement != null) {
            rawTemplate.replace(replacement.first, replacement.second)
        } else {
            rawTemplate
        }

        this.sendMessage(parseMessage(applied))
    }
}
