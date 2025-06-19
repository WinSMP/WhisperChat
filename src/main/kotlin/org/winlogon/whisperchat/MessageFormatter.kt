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

class MessageFormatter(
    private val config: FileConfiguration,
    private val socialSpyLogger: MessageLogger?,
    private val miniMessage: MiniMessage
) {
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
    }

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

    fun sendLegacyMessage(commandSender: Player, messageKey: String, fallback: String, replacement: Pair<String, String>?) {
        val list = if (replacement != null) listOf(replacement) else emptyList()
        sendLegacyMsg(commandSender, messageKey, fallback, list)
    }

    private fun sendLegacyMessage(commandSender: Player, messageKey: String, fallback: String, replacement: List<Pair<String, String>>?) {
        sendLegacyMsg(commandSender, messageKey, fallback, replacement)
    }

    fun parseMessage(input: String): Component {
        return miniMessage.deserialize(legacyToMiniMessage(input))
    }

    private fun legacyToMiniMessage(input: String): String {
        return formatter.convertToMiniMessage(input, '&')
    }
}
