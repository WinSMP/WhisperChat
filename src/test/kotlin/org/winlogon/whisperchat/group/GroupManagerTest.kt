package org.winlogon.whisperchat.group

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.winlogon.whisperchat.BaseWhisperChatTest

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

class GroupManagerTest : BaseWhisperChatTest() {

    private lateinit var mockConfig: FileConfiguration
    private lateinit var mockPlugin: Plugin
    private lateinit var groupManager: GroupManager

    @BeforeEach
    override fun setUp() {
        super.setUp()
        mockConfig = mock(FileConfiguration::class.java)
        mockPlugin = mock(Plugin::class.java)

        // Mock plugin.getDataFolder() to return a temporary directory
        val tempDir = Files.createTempDirectory("whisperchat_test").toFile()
        `when`(mockPlugin.dataFolder).thenReturn(tempDir)
        `when`(mockPlugin.logger).thenReturn(java.util.logging.Logger.getLogger("TestLogger"))

        // Default mock for wordlist path
        `when`(mockConfig.getString("wordlist.path")).thenReturn("test_nounlist.txt")
        `when`(mockConfig.getString("wordlist.url")).thenReturn("https://example.com/nounlist.txt")

        groupManager = GroupManager(mockConfig, mockPlugin)
    }

    @Test
    fun `group is expired when current time is past expiration period`() {
        val ownerUUID = UUID.randomUUID()
        val group = Group("test-group", ownerUUID)

        // Set creation time manually to simulate an expired group
        val expiredCreationTime = System.currentTimeMillis() - group.expirationPeriod - 1000
        val field = group.javaClass.getDeclaredField("creationTime")
        field.isAccessible = true
        field.set(group, expiredCreationTime)

        assertTrue(group.isExpired())
    }

    @Test
    fun `group is not expired when current time is within expiration period`() {
        val ownerUUID = UUID.randomUUID()
        val group = Group("test-group", ownerUUID)

        // Creation time is current, so it should not be expired
        assertFalse(group.isExpired())
    }

    @Test
    fun `loadNouns uses default nouns when file and URL fail`() {
        // Make sure file doesn't exist and URL will fail (by not mocking it to return anything)
        `when`(mockConfig.getString("wordlist.path")).thenReturn("non_existent_file.txt")

        // Re-initialize groupManager to trigger loadNouns with the new mock config
        groupManager = GroupManager(mockConfig, mockPlugin)

        // The default nouns are 12 in GroupManager
        assertEquals(12, groupManager.nouns.size)
        assertTrue(groupManager.nouns.contains("cat"))
        assertTrue(groupManager.nouns.contains("monarch"))
    }

    @Test
    fun `loadNouns loads from file when it exists`() {
        val tempDir = mockPlugin.dataFolder
        val wordlistFile = File(tempDir, "test_nounlist.txt")
        wordlistFile.writeText("apple\nbanana\ncherry")

        groupManager = GroupManager(mockConfig, mockPlugin)

        assertEquals(3, groupManager.nouns.size)
        assertTrue(groupManager.nouns.contains("apple"))
        assertTrue(groupManager.nouns.contains("banana"))
        assertTrue(groupManager.nouns.contains("cherry"))
    }

    @Test
    fun `createGroup creates a new group and assigns owner`() {
        val owner = server.addPlayer()
        val createdGroup = groupManager.createGroup(owner)

        assertNotNull(createdGroup)
        assertTrue(groupManager.groups.containsKey(createdGroup.name))
        assertEquals(owner.uniqueId, createdGroup.owner)
        assertTrue(createdGroup.members.contains(owner.uniqueId))
        assertEquals(createdGroup.name, groupManager.playerGroups[owner.uniqueId])
    }

    @Test
    fun `deleteGroup deletes group if owner is deleter`() {
        val owner = server.addPlayer()
        val group = groupManager.createGroup(owner)

        val isDeleted = groupManager.deleteGroup(group.name, owner)
        assertTrue(isDeleted)
        assertFalse(groupManager.groups.containsKey(group.name))
        assertNull(groupManager.playerGroups[owner.uniqueId])
    }

    @Test
    fun `deleteGroup does not delete group if not owner`() {
        val owner = server.addPlayer()
        val nonOwner = server.addPlayer()
        val group = groupManager.createGroup(owner)

        val isDeleted = groupManager.deleteGroup(group.name, nonOwner)
        assertFalse(isDeleted)
        assertTrue(groupManager.groups.containsKey(group.name))
        assertEquals(group.name, groupManager.playerGroups[owner.uniqueId])
    }

    @Test
    fun `leaveGroup removes player from group and disbands if empty`() {
        val owner = server.addPlayer()
        val member = server.addPlayer()
        val group = groupManager.createGroup(owner)
        groupManager.joinGroup(member, group.name)

        assertTrue(group.members.contains(member.uniqueId))
        assertEquals(group.name, groupManager.playerGroups[member.uniqueId])

        val memberLeft = groupManager.leaveGroup(member)
        assertTrue(memberLeft)
        assertFalse(group.members.contains(member.uniqueId))
        assertNull(groupManager.playerGroups[member.uniqueId])

        // Owner leaves, group should be disbanded
        val ownerLeft = groupManager.leaveGroup(owner)
        assertTrue(ownerLeft)
        assertFalse(groupManager.groups.containsKey(group.name))
    }

    @Test
    fun `joinGroup adds player to existing group`() {
        val owner = server.addPlayer()
        val member = server.addPlayer()
        val group = groupManager.createGroup(owner)

        val joined = groupManager.joinGroup(member, group.name)
        assertTrue(joined)
        assertTrue(group.members.contains(member.uniqueId))
        assertEquals(group.name, groupManager.playerGroups[member.uniqueId])
    }

    @Test
    fun `joinGroup fails if player is already in a group`() {
        val owner = server.addPlayer()
        val member = server.addPlayer()
        val group1 = groupManager.createGroup(owner)
        groupManager.joinGroup(member, group1.name)

        val group2 = groupManager.createGroup(server.addPlayer()) // Another group
        val joined = groupManager.joinGroup(member, group2.name)
        assertFalse(joined)
        assertTrue(group1.members.contains(member.uniqueId))
        assertFalse(group2.members.contains(member.uniqueId))
    }

    @Test
    fun `expireGroups disbands expired groups`() {
        val owner1 = server.addPlayer()
        val owner2 = server.addPlayer()

        val group1 = groupManager.createGroup(owner1)
        val group2 = groupManager.createGroup(owner2)

        // Make group1 expired
        val expiredCreationTime = System.currentTimeMillis() - group1.expirationPeriod - 1000
        val field = group1.javaClass.getDeclaredField("creationTime")
        field.isAccessible = true
        field.set(group1, expiredCreationTime)

        groupManager.expireGroups()

        assertFalse(groupManager.groups.containsKey(group1.name))
        assertNull(groupManager.playerGroups[owner1.uniqueId])
        assertTrue(groupManager.groups.containsKey(group2.name))
        assertEquals(group2.name, groupManager.playerGroups[owner2.uniqueId])
    }
}
