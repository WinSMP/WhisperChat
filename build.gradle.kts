import java.text.SimpleDateFormat
import java.util.*
import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.gradleup.shadow") version "9.3.0"
    kotlin("jvm") version "2.3.0"
}

group = "org.winlogon.whisperchat"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HHmmss'Z'").apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return sdf.format(Date()).toString()
}

val shortVersion: String? = if (project.hasProperty("ver")) {
    val ver = project.property("ver").toString()
    if (ver.startsWith("v")) {
        ver.substring(1).uppercase()
    } else {
        ver.uppercase()
    }
} else {
    null
}

val version: String = when {
    shortVersion.isNullOrEmpty() -> "${getTime()}-SNAPSHOT"
    shortVersion.contains("-RC-") -> shortVersion.substringBefore("-RC-") + "-SNAPSHOT"
    else -> shortVersion
}

val pluginName = rootProject.name
val pluginVersion = version
val pluginPackage = project.group.toString()
val projectName = rootProject.name

repositories {
    mavenCentral()
    maven {
        name = "CommandAPI"
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("net.md-5", "bungeecord-chat")
        }
    }
    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }
    maven {
        name = "winlogon"
        url = uri("https://maven.winlogon.org/releases/")
    }
}

kotlin {
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    compileOnly(libs.commandapi.paper.shade)
    compileOnly(libs.commandapi.paper.core)
    compileOnly(libs.commandapi.kotlin.paper)
    compileOnly(libs.paper.api)

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Wrapper libraries around Paper API features
    compileOnly("org.winlogon:retrohue:0.1.1")
    compileOnly("org.winlogon:asynccraftr:0.1.0")
    
    // MockBukkit & JUnit Jupiter
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockbukkit.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.platform.launcher)

    // Paper API and other dependencies
    testImplementation(libs.paper.api)
    testImplementation("org.winlogon:asynccraftr:0.1.0")
    testImplementation("org.winlogon:retrohue:0.1.1")

    // CommandAPI testing
    testImplementation(libs.commandapi.paper.test.toolkit)
    testRuntimeOnly(libs.commandapi.core)
    testRuntimeOnly(libs.commandapi.paper.core)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesMatching("**/paper-plugin.yml") {
        expand(
            "NAME" to pluginName,
            "VERSION" to pluginVersion,
            "PACKAGE" to pluginPackage
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
}

// Disable jar and replace with shadowJar
tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Utility tasks
tasks.register("printProjectName") {
    doLast {
        println(projectName)
    }
}

var shadowJarTask = tasks.shadowJar.get()
tasks.register("release") {
    dependsOn(tasks.build)
    doLast {
        if (!version.endsWith("-SNAPSHOT")) {
            shadowJarTask.archiveFile.get().asFile.renameTo(
                file("${layout.buildDirectory.get()}/libs/${rootProject.name}.jar")
            )
        }
    }
}
