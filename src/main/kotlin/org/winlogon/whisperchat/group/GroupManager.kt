package org.winlogon.whisperchat.group

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.random.Random
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.Component

enum class DisbandReason {
    EXPIRED, DELETED
}

class Group(
    val name: String,
    val owner: UUID,
    val creationTime: Long = System.currentTimeMillis(),
    val expirationPeriod: Long = 24 * 60 * 60 * 1000,
    val members: MutableSet<UUID> = mutableSetOf()
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - creationTime >= expirationPeriod
    }

    fun sendMessage(sender: Player, message: String) {
        members.forEach { memberId ->
            Bukkit.getPlayer(memberId)?.sendMessage("[$name] ${sender.name}: $message")
        }
    }
}

object GroupManager {
    val groups = ConcurrentHashMap<String, Group>()
    val playerGroups = ConcurrentHashMap<UUID, String>()
    var nouns = listOf<String>()

    fun loadNouns() {
        try {
            val url = java.net.URL("https://www.desiquintans.com/downloads/nounlist/nounlist.txt")
            nouns = url.readText().split("\n").filter { it.isNotBlank() }
        } catch (e: Exception) {
            nouns = listOf("apple", "banana", "cherry", "dragon", "eagle", "falcon")
        }
    }

    private fun generateName(): String {
        val random = ThreadLocalRandom.current()
        val noun1 = nouns[random.nextInt(nouns.size)]
        val noun2 = nouns[random.nextInt(nouns.size)]
        return "$noun1-$noun2"
    }

    fun createGroup(owner: Player): Group {
        val name = generateName()
        val group = Group(name, owner.uniqueId)
        group.members.add(owner.uniqueId)
        groups[name] = group
        playerGroups[owner.uniqueId] = name
        return group
    }

    /**
     * Returns true if the group was deleted
     */
    fun deleteGroup(name: String, deleter: Player): Boolean {
        val group = groups[name] ?: return false
        if (group.owner != deleter.uniqueId) return false
        
        disbandGroup(group, DisbandReason.DELETED)
        groups.remove(name)
        return true
    }

    /**
     * Returns true if the player was removed from the group
     */
    fun leaveGroup(player: Player): Boolean {
        val groupName = playerGroups[player.uniqueId] ?: return false
        val group = groups[groupName] ?: return false
        
        group.members.remove(player.uniqueId)
        playerGroups.remove(player.uniqueId)
        
        if (group.members.isEmpty()) {
            groups.remove(groupName)
        }
        
        return true
    }

    fun joinGroup(player: Player, name: String): Boolean {
        val group = groups[name] ?: return false
        if (playerGroups.containsKey(player.uniqueId)) return false
        
        group.members.add(player.uniqueId)
        playerGroups[player.uniqueId] = name
        return true
    }

    fun expireGroups() {
        val expired = groups.values.filter { it.isExpired() }
        
        expired.forEach { group ->
            disbandGroup(group, DisbandReason.EXPIRED)
        }
    }

    private fun disbandGroup(group: Group, reason: DisbandReason) {
        val owner = Bukkit.getPlayer(group.owner)?.name
            ?: Bukkit.getOfflinePlayer(group.owner).name
            ?: "Unknown"

        val reasonString = when (reason) {
            DisbandReason.EXPIRED -> "expired"
            DisbandReason.DELETED -> "deleted by ${owner}"
        }
        val groupPlaceholder = Placeholder.component("group", Component.text(group.name, NamedTextColor.DARK_RED))
        val reasonPlaceholder = Placeholder.component("reason", Component.text(reasonString, NamedTextColor.DARK_RED))

        group.members.forEach { 
            Bukkit.getPlayer(it)?.sendRichMessage(
                "<red>Group <group> has been deleted due to: <reason><red>",
                groupPlaceholder, reasonPlaceholder
            )
            playerGroups.remove(it)
        }
        groups.remove(group.name)
    }
}
