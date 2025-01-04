package xyz.epicebic.spigotbuilder.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PistonVersion(
    val id: String,
    val url: String,
)

@Serializable
data class PistonLibraryArtifact(
    val url: String,
    val path: String
)

@Serializable
data class PistonLibraryDownloads(
    val artifact: PistonLibraryArtifact? = null,
    val classifiers: JsonObject? = null
)

@Serializable
data class PistonLibrary(
    val name: String,
    val downloads: PistonLibraryDownloads
)

@Serializable
data class PistonVersionsResponse(
    val versions: List<PistonVersion>,
)

@Serializable
data class PistonVersionDownload(
    val url: String,
    val sha1: String
)

@Serializable
data class PistonVersionDownloads(
    val server: PistonVersionDownload,
    @SerialName("server_mappings")
    val serverMappings: PistonVersionDownload
)

@Serializable
data class PistonVersionMeta(
    val downloads: PistonVersionDownloads,
    val libraries: List<PistonLibrary>
)