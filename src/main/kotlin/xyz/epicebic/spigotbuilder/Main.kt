package xyz.epicebic.spigotbuilder

import xyz.epicebic.spigotbuilder.util.FORCE_REOMPILE
import xyz.epicebic.spigotbuilder.util.PARSER
import xyz.epicebic.spigotbuilder.util.QUIET
import xyz.epicebic.spigotbuilder.util.VERSION_FLAG
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = PARSER.parse(*args)

    val version = options.valueOf(VERSION_FLAG)
    if (version == null) {
        println("No version argument passed, please pass a version to --version")
        exitProcess(0)
    }

    VersionFinder.loadVersions().whenComplete { versions, ex ->
        ex?.printStackTrace()

        if (!versions.contains(version)) {
            println("Invalid version supplied, please use a valid version.")
            exitProcess(1)
        }
    }

    val forceRecompile = options.has(FORCE_REOMPILE)
    val quiet = options.has(QUIET)

    println("Starting builder with args: (Version: $version, Force: $forceRecompile, Quiet: $quiet)")
    Builder(version, forceRecompile, quiet).start()

}