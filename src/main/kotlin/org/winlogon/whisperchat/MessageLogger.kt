package org.winlogon.whisperchat

import net.kyori.adventure.text.ComponentLike

import org.bukkit.entity.Player

import kotlin.Result

interface MessageLogger {
    fun log(players: Pair<String, String>, message: ComponentLike): Unit
    fun log(players: Pair<String, String>, message: String): Unit
    fun flush(): Result<Unit>
}
