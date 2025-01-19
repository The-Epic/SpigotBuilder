package xyz.epicebic.spigotbuilder.util

import joptsimple.OptionParser


val PARSER = OptionParser()

val VERSION_FLAG = PARSER.acceptsAll(listOf("version", "v"), "What version to run SpigotBuilder for").withRequiredArg()
    .ofType(String::class.java)

val FORCE_REOMPILE = PARSER.accepts("force", "Should SpigotBuilder force recompile")

val QUIET = PARSER.acceptsAll(listOf("quiet", "q"), "Should SpigotBuilder run with minimal logging")