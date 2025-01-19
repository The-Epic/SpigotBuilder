package xyz.epicebic.spigotbuilder.util

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

fun Path.sha1() = readBytes().inputStream().use {
        MessageDigest.getInstance("SHA-1").digest(it.readBytes())
    }.joinToString("") { "%02x".format(it) }