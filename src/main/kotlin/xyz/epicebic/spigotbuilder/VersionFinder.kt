package xyz.epicebic.spigotbuilder

import com.google.common.io.CharStreams
import xyz.epicebic.spigotbuilder.util.SPIGOT_VERSIONS
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

object VersionFinder {

    val versions: MutableList<String> = mutableListOf()
    private val QUOTE_PATTERN = Pattern.compile("\".*?\"")
    private val VERSION_PATTERN = Regex("\\b\\d+\\.[\\.\\d]*")

    fun loadVersions(): CompletableFuture<List<String>> {
        if (versions.isNotEmpty()) return CompletableFuture.completedFuture(versions)

        return CompletableFuture.supplyAsync {
            val connection = URI(SPIGOT_VERSIONS).toURL().openConnection()
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            val inputReader = connection.getInputStream().reader(Charsets.UTF_8)
            val matcher = QUOTE_PATTERN.matcher(CharStreams.toString(inputReader))

            versions.add("latest")
            versions.add("experimental")

            while (matcher.find()) {
                val version = matcher.group().replace('"', ' ').replace(".json", "").trim()

                if (!version.matches(VERSION_PATTERN)) {
                    continue
                }

                versions.add(version)
            }

            return@supplyAsync versions
        }
    }
}