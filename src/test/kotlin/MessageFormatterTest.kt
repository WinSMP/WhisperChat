package org.winlogon.whisperchat

import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.*
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests specifically for the message formatting capabilities of WhisperChatPlugin.
 * These don't depend on MockBukkit since they test the utility methods directly.
 */
class MessageFormatterTest {
    
    private lateinit var plugin: WhisperChatPlugin
    private lateinit var legacyToMiniMessageMethod: Method
    private lateinit var parseMessageMethod: Method
    
    @BeforeEach
    fun setUp() {
        plugin = WhisperChatPlugin()
        
        // Access private methods via reflection
        legacyToMiniMessageMethod = plugin.javaClass.getDeclaredMethod("legacyToMiniMessage", String::class.java)
        legacyToMiniMessageMethod.isAccessible = true
        
        parseMessageMethod = plugin.javaClass.getDeclaredMethod("parseMessage", String::class.java)
        parseMessageMethod.isAccessible = true
    }
    
    @Test
    fun testBasicColorConversion() {
        val input = "&aGreen text"
        val expected = "<green>Green text"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testMultipleColorConversion() {
        val input = "&aGreen &cRed &bBlue &ePurple"
        val expected = "<green>Green <red>Red <aqua>Blue <yellow>Purple"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testFormattingCodesConversion() {
        val input = "&lBold &nUnderline &oItalic"
        val expected = "<bold>Bold <underlined>Underline <italic>Italic"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testCombinedColorAndFormatting() {
        val input = "&a&lGreen Bold"
        val expected = "<green><bold>Green Bold"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testResetCode() {
        val input = "&cRed &rReset to default"
        val expected = "<red>Red <reset>Reset to default"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testCaseInsensitiveColorCodes() {
        val input = "&AUpper Green &alower green"
        val expected = "<green>Upper Green <green>lower green"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @ParameterizedTest
    @CsvSource(
        "&0,<black>",
        "&1,<dark_blue>",
        "&2,<dark_green>",
        "&3,<dark_aqua>",
        "&4,<dark_red>",
        "&5,<dark_purple>",
        "&6,<gold>",
        "&7,<gray>",
        "&8,<dark_gray>",
        "&9,<blue>",
        "&a,<green>",
        "&b,<aqua>",
        "&c,<red>",
        "&d,<light_purple>",
        "&e,<yellow>",
        "&f,<white>",
        "&k,<obfuscated>",
        "&l,<bold>",
        "&m,<strikethrough>",
        "&n,<underlined>",
        "&o,<italic>",
        "&r,<reset>"
    )
    fun testIndividualColorCodes(input: String, expected: String) {
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testMixedContentWithColorCodes() {
        val input = "Normal &aGreen &4&lBold Red &rNormal again"
        val expected = "Normal <green>Green <dark_red><bold>Bold Red <reset>Normal again"
        
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
    
    @Test
    fun testParseMessageReturnsComponent() {
        val input = "&aGreen message"
        val component = parseMessageMethod.invoke(plugin, input) as Component
        
        // Extract the plain text
        val plainText = PlainTextComponentSerializer.plainText().serialize(component)
        assertEquals("Green message", plainText)
    }
    
    @Test
    fun testFormatPreservation() {
        // Testing that complex formatting like {player} is preserved
        val input = "&aMessage from {player}"
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        
        assertTrue(result.contains("{player}"), "The format placeholder should be preserved")
        assertEquals("<green>Message from {player}", result)
    }
    
    @Test
    fun testMiniMessageTagsPreservation() {
        // Testing that MiniMessage tags are preserved
        val input = "&aGreen <hover:show_text:'Tooltip'>hover text</hover>"
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        
        // The ampersand color should be converted, but the hover tags should remain intact
        assertEquals("<green>Green <hover:show_text:'Tooltip'>hover text</hover>", result)
    }
    
    companion object {
        @JvmStatic
        fun colorCodeCombinations(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("&a&l", "<green><bold>"),
                Arguments.of("&c&n", "<red><underlined>"),
                Arguments.of("&e&o", "<yellow><italic>"),
                Arguments.of("&9&m", "<blue><strikethrough>")
            )
        }
    }
    
    @ParameterizedTest
    @MethodSource("colorCodeCombinations")
    fun testColorCodeCombinations(input: String, expected: String) {
        val result = legacyToMiniMessageMethod.invoke(plugin, input) as String
        assertEquals(expected, result)
    }
}
