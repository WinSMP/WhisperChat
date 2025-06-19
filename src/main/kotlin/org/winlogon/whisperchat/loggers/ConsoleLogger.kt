package org.winlogon.whisperchat.loggers

import org.bukkit.Bukkit
import org.winlogon.whisperchat.MessageLogger

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder

import kotlin.Result

public class ConsoleLogger : MessageLogger {
    val consoleSender = Bukkit.getConsoleSender()
    val miniMessage = MiniMessage.miniMessage()

    override fun log(players: Pair<String, String>, message: String): Unit {
        consoleSender.sendMessage("${players.first} sent ${players.second}: $message")
    }

    override fun log(players: Pair<String, String>, message: ComponentLike): Unit {
        val firstPlayer = Component.text(players.first, NamedTextColor.DARK_AQUA)
        val secondPlayer = Component.text(players.second, NamedTextColor.DARK_GREEN)
        val messageComponent = message.asComponent().color(NamedTextColor.GRAY)

        consoleSender.sendRichMessage(
            "<first> sent <second>: <message>",
            Placeholder.component("first", firstPlayer),
            Placeholder.component("second", secondPlayer),
            Placeholder.component("message", messageComponent),
        )
    }

    override fun flush(): Result<Unit> {
        return Result.success(Unit)
    }
}
