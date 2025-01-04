package xyz.epicebic.spigotbuilder.schema

import xyz.epicebic.spigotbuilder.util.SPIGOT_VERSIONS
import xyz.epicebic.spigotbuilder.util.getUriJson
import java.net.URI


object SpigotAPI {

    fun retrieveVersionInfo(version: String) = getUriJson<SpigotVersionMeta>(
        URI("$SPIGOT_VERSIONS/$version.json")
    )
}