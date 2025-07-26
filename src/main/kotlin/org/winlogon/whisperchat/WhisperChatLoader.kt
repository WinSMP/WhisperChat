package org.winlogon.whisperchat

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class WhisperChatLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()

        val repositories = mapOf(
            "central" to MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR,
            "winlogon-code" to "https://maven.winlogon.org/releases/",
        )

        for ((name, url) in repositories) {
            resolver.addRepository(
                RemoteRepository.Builder(name, "default", url).build()
            )
        }

        val dependencies = mapOf(
            "org.winlogon:retrohue" to "0.1.1",
            "org.winlogon:asynccraftr" to "0.1.0",
        )

        for ((artifact, version) in dependencies) {
            val dependency = Dependency(DefaultArtifact("$artifact:$version"), null)
            resolver.addDependency(dependency)
        }

        classpathBuilder.addLibrary(resolver)
    }
}
