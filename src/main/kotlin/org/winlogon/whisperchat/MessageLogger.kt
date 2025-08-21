package org.winlogon.whisperchat

import net.kyori.adventure.text.ComponentLike

import org.bukkit.entity.Player

import kotlin.Result

interface MessageLogger {
    /**
     * Logs a message with a custom component.
     *
     * @param players the players involved in the conversation
     * @param message the message sent
     */
    fun log(players: Pair<String, String>, message: ComponentLike): Unit
    /**
     * Logs a message, using a regular string
     *
     * @param players the players involved in the conversation
     * @param message the raw message sent
     */
    fun log(players: Pair<String, String>, message: String): Unit
    /**
     * Flushes the logger, useful for saving logs to disk, sending them to a server,
     * or saving them to a cache.
     *
     * @return a result that indicates if the flush was successful
     */
    fun flush(): Result<Unit>
}
