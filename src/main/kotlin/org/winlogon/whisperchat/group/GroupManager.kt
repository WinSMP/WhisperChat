package org.winlogon.whisperchat.group

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.audience.Audience

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Consumer
import java.util.logging.Logger

enum class DisbandReason {
    EXPIRED, DELETED
}

class Group(
    val name: String,
    val owner: UUID,
) : ForwardingAudience.Single {
    val creationTime: Long = System.currentTimeMillis()
    val expirationPeriod: Long = Duration.ofDays(1).toMillis()
    val members: MutableSet<UUID> = mutableSetOf(owner)

    /**
     * Returns true if the group has expired
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - creationTime >= expirationPeriod
    }

    override fun audience(): Audience = Audience.audience(
        members.mapNotNull { uuid ->
            Bukkit.getPlayer(uuid)?.let { Audience.audience(it) }
        }
    )
}

class GroupManager(val config: FileConfiguration, val plugin: Plugin) {
    private val wordListPath = config.getString("wordlist.path") ?: "nounlist.txt"
    private val wordListUrl = config.getString("wordlist.url") ?: "https://www.desiquintans.com/downloads/nounlist/nounlist.txt"
    private val defaultNouns = listOf("cat", "falcon", "apple", "dragon", "grape", "cherry", "hedgehog", "eagle", "banana", "monarch", "iguana", "jellyfish")
    private val logger: Logger = plugin.logger

    val groups = ConcurrentHashMap<String, Group>()
    val playerGroups = ConcurrentHashMap<UUID, String>()
    var nouns = listOf<String>()

    init {
        loadNouns()
    }

    private fun loadNouns() {
        val pluginWordlistPath = Paths.get(plugin.dataFolder.path, wordListPath)
        logger.fine("Loading nouns...")

        try {
            if (Files.exists(pluginWordlistPath)) {
                logger.fine("File $pluginWordlistPath exists. Loading newline-separated nouns.")
                nouns = Files.readAllLines(pluginWordlistPath).filter { it.isNotBlank() }
                return
            }

            logger.fine("File $pluginWordlistPath does not exist. Attempting to download from $wordListUrl.")
            val url = java.net.URI.create(wordListUrl).toURL()
            Files.copy(url.openStream(), pluginWordlistPath)
            nouns = url.readText().split("\n").filter { it.isNotBlank() }
        } catch (e: Exception) {
            logger.warning("Failed to load nouns from $pluginWordlistPath or $wordListUrl. Using default nouns: $defaultNouns")
            nouns = defaultNouns
        }
    }

    private fun generateName(): String {
        val random = ThreadLocalRandom.current()
        val noun1 = nouns[random.nextInt(nouns.size)]
        val noun2 = nouns[random.nextInt(nouns.size)]
        return "$noun1-$noun2"
    }

    fun createGroup(owner: Player): Group {
        logger.fine("Creating group for ${owner.name}")
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
        logger.fine("Deleting group $name by ${deleter.name}")
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
        logger.fine("Player ${player.name} is leaving a group")
        val groupName = playerGroups[player.uniqueId] ?: return false
        val group = groups[groupName] ?: return false
        
        group.members.remove(player.uniqueId)
        playerGroups.remove(player.uniqueId)
        
        if (group.members.isEmpty()) {
            groups.remove(groupName)
        }
        
        return true
    }

    /**
     * Returns true if the player was added to the group
     */
    fun joinGroup(player: Player, name: String): Boolean {
        logger.fine("Player ${player.name} is joining group $name")
        val group = groups[name] ?: return false
        if (playerGroups.containsKey(player.uniqueId)) return false
        
        group.members.add(player.uniqueId)
        playerGroups[player.uniqueId] = name
        return true
    }

    /**
     * Deletes expired groups
     */
    fun expireGroups() {
        logger.fine("Expiring groups...")
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
