package org.winlogon.whisperchat.loggers

import org.winlogon.whisperchat.MessageLogger

import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

import kotlin.Result

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

public class DiskLogger(private val dataFolder: File) : MessageLogger {
    private val currentDateTime = ZonedDateTime.now(ZoneId.of("UTC"))
    private val formatter = DateTimeFormatter.ISO_DATE_TIME
    private val utcIso8601 = currentDateTime.format(formatter)
    private val serializer = PlainTextComponentSerializer.plainText()
    private val dest = File(dataFolder, "${utcIso8601}.log")

    override fun log(players: Pair<String, String>, message: String) {
        writeStringToFile(players, message)
    }

    override fun log(players: Pair<String, String>, message: ComponentLike) {
        val msg = serializer.serialize(message.asComponent())
        writeStringToFile(players, msg)
    }

    override fun flush(): Result<Unit> {
        return try {
            Files.exists(dest.toPath())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun writeStringToFile(players: Pair<String, String>, message: String) {
        val template = "[$utcIso8601] ${players.first} sent ${players.second}: $message"

        Files.write(
            dest.toPath(),
            template.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}
