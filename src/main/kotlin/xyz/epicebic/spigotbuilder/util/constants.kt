package xyz.epicebic.spigotbuilder.util


// Directories
const val WORK_DIR = "work"
const val VANILLA_WORK_DIR = "$WORK_DIR/vanilla"
const val SPIGOT_REPO_DIR = "$WORK_DIR/Spigot"
const val CRAFT_BUKKIT_REPO_DIR = "$WORK_DIR/CraftBukkit"
const val BUKKIT_REPO_DIR = "$WORK_DIR/Bukkit"
const val BUILD_DATA_REPO_DIR = "$WORK_DIR/BuildData"

// Files
const val VANILLA_BUNDLER_JAR = "$VANILLA_WORK_DIR/mojang_bundler.jar"
const val VANILLA_SERVER_JAR = "$VANILLA_WORK_DIR/mojang_server.jar"
const val VANILLA_MAPPED_SPIGOT_JAR = "$VANILLA_WORK_DIR/mojang_server_remapped-spigot-<rev>.jar"
const val VANILLA_MAPPED_MOJANG_JAR = "$VANILLA_WORK_DIR/mojang_server_remapped-mojang-<rev>.jar"
const val VANILLA_MAPPED_JAR = "$VANILLA_WORK_DIR/mojang_server_remapped-<rev>.jar"
const val BUKKIT_CLASSES_TINY = "$VANILLA_WORK_DIR/bukkit-<ver>-cl.tiny"
const val BUKKIT_MEMBERS_TINY = "$VANILLA_WORK_DIR/bukkit-<ver>-m.tiny"
const val VANILLA_MOJANG_TINY = "$VANILLA_WORK_DIR/obf_mojang.tiny"
const val VANILLA_MAPPINGS = "$VANILLA_WORK_DIR/mojang_mappings.txt"
const val SPIGOT_BUILDER_LOG = "$WORK_DIR/builder.log.txt"


// Urls
const val SPIGOT_VERSIONS = "https://hub.spigotmc.org/versions"
private const val REMOTE_URL = "https://hub.spigotmc.org/stash/scm/spigot"
const val CRAFT_BUKKIT_REMOTE = "$REMOTE_URL/craftbukkit.git"
const val BUKKIT_REMOTE = "$REMOTE_URL/bukkit.git"
const val SPIGOT_REMOTE = "$REMOTE_URL/spigot.git"
const val BUILD_DATA_REMOTE = "$REMOTE_URL/builddata.git"