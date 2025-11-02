package org.winlogon.whisperchat

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

const val commandapiVersion = "11.0.0"

class WhisperChatLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()

        val commandapiDependencies: List<String> = listOf("paper-shade", "paper-core", "kotlin-paper")

        val repositories = mapOf(
            "commandapi" to "https://repo.codemc.org/repository/maven-public/",
            "central" to MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR,
            "winlogon-code" to "https://maven.winlogon.org/releases/",
        )

        for ((name, url) in repositories) {
            resolver.addRepository(
                RemoteRepository.Builder(name, "default", url).build()
            )
        }

        val dependencies = mutableMapOf(
            "org.winlogon:retrohue" to "0.1.1",
            "org.winlogon:asynccraftr" to "0.1.0",
        )

        for (element in commandapiDependencies) {
            dependencies["dev.jorel:commandapi-$element"] = commandapiVersion
        }

        for ((artifact, version) in dependencies) {
            val dependency = Dependency(DefaultArtifact("$artifact:$version"), null)
            resolver.addDependency(dependency)
        }

        classpathBuilder.addLibrary(resolver)
    }
}
