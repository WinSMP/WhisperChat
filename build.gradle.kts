import java.text.SimpleDateFormat
import java.util.*
import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("com.gradleup.shadow") version "8.3.6"
    kotlin("jvm") version "2.1.10"
}

group = "org.winlogon.whisperchat"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyMMdd-HHmm")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
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
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val commandapiVersion = "11.0.0"
val minecraftVersion = "1.21.8"

dependencies {
    compileOnly("dev.jorel:commandapi-paper-shade:${commandapiVersion}")
    compileOnly("dev.jorel:commandapi-kotlin-paper:${commandapiVersion}")
    compileOnly("dev.jorel:commandapi-paper-core:${commandapiVersion}")
    compileOnly("io.papermc.paper:paper-api:${minecraftVersion}-R0.1-SNAPSHOT")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    // Wrapper libraries around Paper API features
    compileOnly("org.winlogon:retrohue:0.1.1")
    compileOnly("org.winlogon:asynccraftr:0.1.0")
    
    // MockBukkit & JUnit Jupiter
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.83.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.0")

    // Paper API and other dependencies
    testImplementation("io.papermc.paper:paper-api:${minecraftVersion}-R0.1-SNAPSHOT")
    testImplementation("org.winlogon:asynccraftr:0.1.0")
    testImplementation("org.winlogon:retrohue:0.1.1")

    // CommandAPI testing
    testImplementation("dev.jorel:commandapi-paper-test-toolkit:${commandapiVersion}")
    testRuntimeOnly("dev.jorel:commandapi-core:${commandapiVersion}")
    testRuntimeOnly("dev.jorel:commandapi-paper-core:${commandapiVersion}")
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
