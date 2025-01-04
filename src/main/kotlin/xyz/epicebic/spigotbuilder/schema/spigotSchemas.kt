package xyz.epicebic.spigotbuilder.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpigotVersionMeta(
    val refs: RepoRefs,
    val toolsVersion: Int
)

@Serializable
data class RepoRefs(
    @SerialName("BuildData") val buildData: String,
    @SerialName("Bukkit") val bukkit: String,
    @SerialName("CraftBukkit") val craftBukkit: String,
    @SerialName("Spigot") val spigot: String
)