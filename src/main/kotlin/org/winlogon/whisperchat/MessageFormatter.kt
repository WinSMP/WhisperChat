package org.winlogon.whisperchat

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import org.winlogon.retrohue.RetroHue
import org.winlogon.whisperchat.MessageLogger

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MessageFormatter(
    private val lastInteraction: ConcurrentHashMap<Pair<UUID, UUID>, Long>,
    private val config: FileConfiguration,
    private val socialSpyLogger: MessageLogger?,
    private val miniMessage: MiniMessage
) {
    /**
     * Translates legacy color codes to MiniMessage tags
     */
    private val formatter = RetroHue(miniMessage)

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

        socialSpyLogger?.takeIf { type == MessageType.DM }?.let { logger ->
            logger.log(Pair(sender.name, receiver.name), component)
        }
        updateLastInteraction(sender, receiver)
    }

    /**
     * Sends a legacy message taken from the config, converting legacy color codes to MiniMessage tags,
     * applying some replacements if necessary
     */
    fun sendLegacyMsg(commandSender: Player, messageKey: String, fallback: String, replacement: List<Pair<String, String>>?) {
        val rawTemplate = config.getString(messageKey) ?: fallback

        val applied = if (replacement != null) {
            var s = rawTemplate
            replacement.forEach { pair ->
                s = rawTemplate.replace(pair.first, pair.second)
            }
            s
        } else {
            rawTemplate
        }

        commandSender.sendMessage(parseMessage(applied))
    }

    fun sendLegacyMessage(commandSender: Player, messageKey: String, fallback: String, vararg replacements: Pair<String, String>) {
        sendLegacyMsg(commandSender, messageKey, fallback, replacements.toList())
    }

    /**
     * Converts a string's legacy color codes to MiniMessage, returning the serialized MiniMessage component.
     */
    fun parseMessage(input: String): Component {
        return formatter.convertToComponent(input, '&')
    }

    /**
     * Converts a string's legacy color codes to MiniMessage, returning the serialized MiniMessage component.
     */
    fun parseMessage(input: String, vararg replacements: Pair<String, String>): Component {
        val sb = StringBuilder(input.length)

        var i = 0
        while (i < input.length) {
            var replaced = false
            // try to find a replacement
            for ((from, to) in replacements) {
                // if the string starts with the replacement, replace it
                if (input.startsWith(from, i)) {
                    sb.append(to)
                    i += from.length
                    replaced = true
                    break
                }
            }
            // if no replacement was found, just append the character
            if (!replaced) {
                sb.append(input[i])
                i++
            }
        }
        return formatter.convertToComponent(sb.toString(), '&')
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
