package xyz.epicebic.spigotbuilder

//import org.jetbrains.java.decompiler.main.Fernflower
//import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler.SaveType
import com.google.common.hash.Hashing
import io.codechicken.diffpatch.cli.PatchOperation
import io.codechicken.diffpatch.util.Input.FolderMultiInput
import io.codechicken.diffpatch.util.LogLevel
import io.codechicken.diffpatch.util.Output.FolderMultiOutput
import net.md_5.ss.SpecialSource
import org.apache.commons.io.output.TeeOutputStream
import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.GpgConfig
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.FetchResult
import org.eclipse.jgit.transport.URIish
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler
import xyz.epicebic.spigotbuilder.schema.PistonAPI
import xyz.epicebic.spigotbuilder.schema.PistonAPI.getVersionMeta
import xyz.epicebic.spigotbuilder.schema.SpigotAPI
import xyz.epicebic.spigotbuilder.util.BUILD_DATA_REMOTE
import xyz.epicebic.spigotbuilder.util.BUILD_DATA_REPO_DIR
import xyz.epicebic.spigotbuilder.util.BUKKIT_REMOTE
import xyz.epicebic.spigotbuilder.util.BUKKIT_REPO_DIR
import xyz.epicebic.spigotbuilder.util.CRAFT_BUKKIT_REMOTE
import xyz.epicebic.spigotbuilder.util.CRAFT_BUKKIT_REPO_DIR
import xyz.epicebic.spigotbuilder.util.MapUtil
import xyz.epicebic.spigotbuilder.util.SPIGOT_BUILDER_LOG
import xyz.epicebic.spigotbuilder.util.SPIGOT_REMOTE
import xyz.epicebic.spigotbuilder.util.SPIGOT_REPO_DIR
import xyz.epicebic.spigotbuilder.util.VANILLA_BUNDLER_JAR
import xyz.epicebic.spigotbuilder.util.VANILLA_MAPPED_JAR
import xyz.epicebic.spigotbuilder.util.VANILLA_MAPPED_MOJANG_JAR
import xyz.epicebic.spigotbuilder.util.VANILLA_MAPPED_SPIGOT_JAR
import xyz.epicebic.spigotbuilder.util.VANILLA_MAPPINGS
import xyz.epicebic.spigotbuilder.util.VANILLA_SERVER_JAR
import xyz.epicebic.spigotbuilder.util.VANILLA_WORK_DIR
import xyz.epicebic.spigotbuilder.util.WORK_DIR
import xyz.epicebic.spigotbuilder.util.downloadUri
import xyz.epicebic.spigotbuilder.util.sha1
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.io.path.writeBytes
import kotlin.system.exitProcess
import kotlin.time.measureTime


class Builder(private val version: String, private val forceRecompile: Boolean, private val quiet: Boolean) {

    companion object {
        private val AUTO_CRLF = "\n" != System.lineSeparator()
    }

    private val workPath = getAndCreate(WORK_DIR, true)
    private lateinit var startInstant: Instant
    private val javaHome: File
    private val spigotVersionMeta = SpigotAPI.retrieveVersionInfo(version)
    private lateinit var decompileDir: Path
    private val mavenInvoker = DefaultInvoker()
    private var clonedRepo = false


    init {
        if (workPath.notExists()) workPath.createDirectory()
        javaHome = File(ProcessHandle.current().info().command().get()).parentFile.parentFile
        mavenInvoker.mavenHome = File(System.getenv("MAVEN_HOME"))
    }

    // TODO not use this jank ahh stuff
    private fun logOutput() {
        val out = System.out
        val err = System.err
        val logOut = Path(SPIGOT_BUILDER_LOG).outputStream().buffered()

        Runtime.getRuntime().addShutdownHook(Thread {
            System.setOut(PrintStream(FileOutputStream(FileDescriptor.out)))
            System.setErr(PrintStream(FileOutputStream(FileDescriptor.err)))
            try {
                logOut.close()
            } catch (_: Exception) {
            }
        })

        System.setOut(PrintStream(TeeOutputStream(out, logOut)))
        System.setErr(PrintStream(TeeOutputStream(err, logOut)))
    }

    fun start() {
        logOutput()
        this.startInstant = Instant.now()
        downloadPortables()
        val repoTime = measureTime { setupRepos() }
        val vanillaSetupTime = measureTime { setupVanilla() }
        val vanillaDecompileTime = measureTime { decompileVanilla() }
        val cbPatchTime = measureTime { patchCraftBukkit() }

        val patchedDir = decompileDir.resolve("patched")
        copyPatchedFiles(Path(CRAFT_BUKKIT_REPO_DIR), patchedDir.toAbsolutePath())
        createCraftBukkitPatched()

        copyDirectories()

        val spigotPatchTime = measureTime { patchSpigot() }
        val spigotCompileTime = measureTime { compileSpigot() }

//        Path(SPIGOT_REPO_DIR).resolve("Spigot-Server").resolve("target")
        println("Took ${Duration.between(startInstant, Instant.now()).seconds}")
        println("""
            Repo Setup Time: $repoTime
            Vanilla Setup Time: $vanillaSetupTime
            Vanilla Decompile Time: $vanillaDecompileTime
            CraftBukkit Patch Time: $cbPatchTime
            
            Spigot Patch Time: $spigotPatchTime
            Spigot Compile Time: $spigotCompileTime
        """.trimIndent())
    }

    private fun downloadPortables() {

    }


    private fun setupVanilla() {
        println("Setting up Vanilla")
        val vanillaWorkDir = workPath.resolve("vanilla")
        if (vanillaWorkDir.notExists()) vanillaWorkDir.createDirectories()

        val pistonVersionMeta = PistonAPI.getPistonVersions().getVersionMeta(version)
        val vanillaBundler = Path(VANILLA_BUNDLER_JAR)
        var vanillaBundlerDownloaded = false
        if (vanillaBundler.exists()) {
            val correctJar = checkHash(pistonVersionMeta.downloads.server.sha1, vanillaBundler)
            if (!correctJar) {
                println("Vanilla Bundler exists but is wrong hash, downloading.")
                downloadUri(URI(pistonVersionMeta.downloads.server.url), vanillaBundler)
                vanillaBundlerDownloaded = true
            }
        }

        if (vanillaBundler.notExists()) {
            println("Downloading Vanilla Bundler")
            downloadUri(URI(pistonVersionMeta.downloads.server.url), vanillaBundler)
            vanillaBundlerDownloaded = true
        }


        if (spigotVersionMeta.toolsVersion > 128) {
            val vanillaMappings = Path(VANILLA_MAPPINGS)
            if (vanillaMappings.exists()) {
                val correctFile = checkHash(pistonVersionMeta.downloads.serverMappings.sha1, vanillaMappings)
                if (!correctFile) {
                    println("Vanilla Mappings exists but is wrong hash, downloading.")
                    downloadUri(URI(pistonVersionMeta.downloads.serverMappings.url), vanillaMappings)
                }
            }

            if (vanillaMappings.notExists()) {
                println("Downloading Vanilla Mappings")
                downloadUri(URI(pistonVersionMeta.downloads.serverMappings.url), vanillaMappings)
            }
        }

        val vanillaServer = Path(VANILLA_SERVER_JAR)
        if (vanillaServer.exists() && !vanillaBundlerDownloaded) return


        val needsExtraction = JarFile(vanillaBundler.toFile()).use {
            it.getJarEntry("META-INF/versions") != null
        }

        if (!needsExtraction) {
            vanillaBundler.copyTo(vanillaServer)
            FileSystems.newFileSystem(vanillaServer, null as ClassLoader?).use { zipfs ->
                Files.delete(zipfs.getPath("/META-INF/MOJANGCS.RSA"))
                Files.delete(zipfs.getPath("/META-INF/MOJANGCS.SF"))
            }
        } else {
            println("Exracting Vanilla Server")
            extractFile(vanillaBundler, "versions/$version/server-$version.jar", vanillaServer, true) { file ->
                FileSystems.newFileSystem(file, null as ClassLoader?).use { zipfs ->
                    Files.delete(zipfs.getPath("/META-INF/MOJANGCS.RSA"))
                    Files.delete(zipfs.getPath("/META-INF/MOJANGCS.SF"))
                }
            }
        }
    }

    private fun setupRepos() {

        val bukkit = Path(BUKKIT_REPO_DIR)
        if (bukkit.notExists() && !containsGit(bukkit)) cloneRepo(BUKKIT_REMOTE, bukkit)

        val craftBukkit = Path(CRAFT_BUKKIT_REPO_DIR)
        if (craftBukkit.notExists() && !containsGit(craftBukkit)) cloneRepo(CRAFT_BUKKIT_REMOTE, craftBukkit)

        val spigot = Path(SPIGOT_REPO_DIR)
        if (spigot.notExists() && !containsGit(spigot)) cloneRepo(SPIGOT_REMOTE, spigot)

        val buildData = Path(BUILD_DATA_REPO_DIR)
        if (buildData.notExists() && !containsGit(buildData)) cloneRepo(BUILD_DATA_REMOTE, buildData)


        val bukkitGit = Git.open(bukkit.toFile())
        val craftBukkitGit = Git.open(craftBukkit.toFile())
        val spigotGit = Git.open(spigot.toFile())
        val buildDataGit = Git.open(buildData.toFile())

        val bukkitChanged = pull(bukkitGit, spigotVersionMeta.refs.bukkit)
        val craftBukkitChanged = pull(craftBukkitGit, spigotVersionMeta.refs.craftBukkit)
        val spigotChanged = pull(spigotGit, spigotVersionMeta.refs.spigot)
        val buildDataChanged = pull(buildDataGit, spigotVersionMeta.refs.buildData)

        if (!bukkitChanged && !craftBukkitChanged && !spigotChanged && !buildDataChanged && !clonedRepo) {
            if (!forceRecompile) {
                println("No changes found for any repos. Exiting")
                exitProcess(0)
            }

            println("No changes for repos, but forced recompiled is set. Recompiling.")
        }
    }

    private fun decompileVanilla() {
        println("Decompiling Vanilla")
        val buildDataDir = Path(BUILD_DATA_REPO_DIR)
        val dataMappings = Path(BUILD_DATA_REPO_DIR).resolve("mappings")
        val buildDataGit = Git.open(buildDataDir.toFile())

        val mappings: Iterable<RevCommit> = buildDataGit.log()
            .addPath("mappings/")
            .setMaxCount(1).call()

        //noinspection deprecation
        val mappingsHash = Hashing.md5().newHasher()
        for (rev in mappings) {
            mappingsHash.putString(rev.name, StandardCharsets.UTF_8)
        }
        val mappingsVersion = mappingsHash.hash().toString().substring(24) // Last 8 chars


        var memberMappings = dataMappings.resolve("bukkit-$version-members.csrg")
        val fieldMappings = Path(VANILLA_WORK_DIR).resolve("bukkit-$version-fields.csrg")

        // Handle pre mojmap versions
        if (spigotVersionMeta.toolsVersion > 128) {
            val mapUtil = MapUtil()
            mapUtil.loadBuk(dataMappings.resolve("bukkit-$version-cl.csrg").toFile())
            if (!memberMappings.exists()) {
                memberMappings = Path(VANILLA_WORK_DIR).resolve("bukkit-$version-members.csrg")
                mapUtil.makeFieldMaps(File(VANILLA_MAPPINGS), memberMappings.toFile(), true)
            } else if (!fieldMappings.exists()) {
                mapUtil.makeFieldMaps(File(VANILLA_MAPPINGS), fieldMappings.toFile(), false)
            }

            // TODO mvn install mappings files
        }
        val spigotMappedJar = Path(VANILLA_MAPPED_SPIGOT_JAR.replace("<rev>", mappingsVersion).trim())

        if (spigotMappedJar.notExists()) SpecialSource.main(
            listOf(
                "map",
                "-i=${Path(VANILLA_SERVER_JAR).toAbsolutePath().pathString}",
                "-o=${spigotMappedJar.toAbsolutePath().pathString}",
                "-m=${buildDataDir.resolve("mappings").resolve("bukkit-$version-cl.csrg")}",
                "-auto-lvt=BASIC",
                "-e=${buildDataDir.resolve("mappings").resolve("bukkit-$version.exclude")}"
            ).toTypedArray()
        )

        val mojangMappedJar = Path(VANILLA_MAPPED_MOJANG_JAR.replace("<rev>", mappingsVersion).trim())

        if (mojangMappedJar.notExists()) SpecialSource.main(
            listOf(
                "map",
                "-auto-member=TOKENS",
                "-i=${spigotMappedJar.toAbsolutePath().pathString}",
                "-o=${mojangMappedJar.toAbsolutePath().pathString}",
                "-m=${memberMappings.toAbsolutePath().pathString}",
            ).toTypedArray()
        )

        val finalMappedJar = Path(VANILLA_MAPPED_JAR.replace("<rev>", mappingsVersion).trim())

        if (finalMappedJar.notExists()) net.md_5.specialsource.SpecialSource.main(
            listOf(
                "--access-transformer=${buildDataDir.resolve("mappings").resolve("bukkit-$version.at")}",
                "-i=${mojangMappedJar.toAbsolutePath()}",
                "-o=${finalMappedJar.toAbsolutePath()}",
                "-m=${buildDataDir.resolve("mappings").resolve("package.srg")}"
            ).toTypedArray()
        )


        val decompileWorkDir = Path(VANILLA_WORK_DIR).resolve("decompile-$mappingsVersion")
        this.decompileDir = decompileWorkDir
        val decompileClassesDir = decompileWorkDir.resolve("classes")

        if (decompileClassesDir.notExists()) {
            val decompiledZip = ZipFile(finalMappedJar.toFile())
            val entries = decompiledZip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.name.startsWith("net/minecraft")) continue

                val outFile = decompileClassesDir.resolve(entry.name)
                if (outFile.isDirectory()) outFile.toFile().mkdirs()
                if (outFile.parent != null) outFile.toFile().parentFile.mkdirs()

                outFile.writeBytes(decompiledZip.getInputStream(entry).readBytes())
                if (!quiet) println("Extracted ${entry.name}")
            }
        }


        val decompileStart = Instant.now()
        val decompileJava = decompileWorkDir.resolve("java")
        if (decompileJava.notExists()) {
            decompileJava.createDirectories()
            // TODO allow for this to be quiet
            ConsoleDecompiler.main(
                listOf(
                    "-dgs=1", "-hdc=0", "-asc=1", "-udv=0", "-rsy=1", "-aoa=1",
                    decompileClassesDir.pathString, decompileJava.pathString
                ).toTypedArray()
            )
            println("Decompilation took ${Duration.between(decompileStart, Instant.now()).seconds}")
        }
    }

    private fun patchCraftBukkit() {
        println("Applying CraftBukkit's patches")
        val patchesDir = Path(CRAFT_BUKKIT_REPO_DIR).resolve("nms-patches")
        val patchedDir = decompileDir.resolve("patched")
        if (patchedDir.exists()) return
        val patcher = PatchOperation.builder()
            .logTo(System.out)
            .level(LogLevel.ALL)
            .baseInput(FolderMultiInput(decompileDir.resolve("java")))
            .patchesInput(FolderMultiInput(patchesDir))
            .patchedOutput(FolderMultiOutput(patchedDir))
            .rejectsOutput(FolderMultiOutput(Path(VANILLA_WORK_DIR).resolve("rejected")))
//            .summary(true)
            .build()

        val result = patcher.operate()
        println("Applied ${result.summary?.changedFiles} patches")
    }

    private fun compileBukkit() {
        println("Compiling Bukkit")
        val bukkitDir = Path(BUKKIT_REPO_DIR)

        val bukkitRequest = DefaultInvocationRequest()
        bukkitRequest.goals = listOf("clean", "install")
        bukkitRequest.javaHome = this.javaHome
        bukkitRequest.baseDirectory = bukkitDir.toFile()

        mavenInvoker.execute(bukkitRequest)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyPatchedFiles(craftBukkitDir: Path, patchedDir: Path) {

        val patchDir = craftBukkitDir.resolve("nms-patches")
        val nmsDir = craftBukkitDir.resolve("src/main/java/net")
        patchDir.walk(PathWalkOption.BREADTH_FIRST).filter { it.isRegularFile() }.filter { it.name.endsWith(".patch") }
            .forEach { path ->
                val relativeName = patchDir.relativize(path).toString().replace(".patch", ".java")
                val targetFile =
                    if (relativeName.contains(File.separator)) relativeName else "net/minecraft/server/$relativeName"
                val targetPath = nmsDir.parent.resolve(targetFile)

                targetPath.parent.createDirectories()
                if (targetPath.notExists()) {
                    patchedDir.resolve(relativeName).copyTo(targetPath)
                    if (!quiet) println("Copied $relativeName to $targetPath")
                }
            }
    }

    private fun createCraftBukkitPatched() {
        val craftBukkitDir = Path(CRAFT_BUKKIT_REPO_DIR)
        val craftBukkitGit = Git.open(craftBukkitDir.toFile())

        craftBukkitGit.branchDelete().setBranchNames("patched").setForce(true).call()
        craftBukkitGit.checkout().setCreateBranch(true).setForceRefUpdate(true).setName("patched").call()
        craftBukkitGit.add().addFilepattern("src/main/java/net/").call()
        craftBukkitGit.commit().setGpgConfig(GpgConfig(Config())).setSign(false)
            .setMessage("CraftBukkit $ " + Date()).call()
        craftBukkitGit.checkout().setName(spigotVersionMeta.refs.craftBukkit).call()
    }

    private fun compileCraftBukkit() {
        println("Compiling CraftBukkit")
        val craftBukkitDir = Path(CRAFT_BUKKIT_REPO_DIR)

        val craftBukkitRequest = DefaultInvocationRequest()
        craftBukkitRequest.goals = listOf("clean", "install")
        craftBukkitRequest.javaHome = this.javaHome
        craftBukkitRequest.baseDirectory = craftBukkitDir.toFile()

        mavenInvoker.execute(craftBukkitRequest)
    }

    private fun copyDirectories() {
        val spigot = Path(SPIGOT_REPO_DIR)
        val spigotApi = spigot.resolve("Spigot-API")
        if (spigotApi.notExists()) {
            cloneRepo("file://" + Path(BUKKIT_REPO_DIR).absolutePathString(), spigotApi)
        }

        val spigotServer = spigot.resolve("Spigot-Server")
        if (spigotServer.notExists()) {
            cloneRepo("file://" + Path(CRAFT_BUKKIT_REPO_DIR).absolutePathString(), spigotServer)
        }
    }

    private fun patchSpigot() {
        // Apply Bukkit Patches
        val spigotParentDir = Path(SPIGOT_REPO_DIR)
        val bukkitDir = Path(BUKKIT_REPO_DIR)

        val spigotApiDir = spigotParentDir.resolve("Spigot-API")
        val spigotServerDir = spigotParentDir.resolve("Spigot-Server")

        // Set spigot branch hash
        val bukkitGit = Git.open(bukkitDir.toFile())
        val head = bukkitGit.log().setMaxCount(1).call().iterator().next()
        bukkitGit.branchCreate().setForce(true).setName("spigot").setStartPoint(head).call()

        applyPatches("Bukkit", spigotApiDir, "origin/spigot", spigotParentDir)
        applyPatches("CraftBukkit", spigotServerDir, "origin/patched", spigotParentDir)
    }

    private fun compileSpigot() {
        val spigotRequest = DefaultInvocationRequest()
        spigotRequest.goals = listOf("clean", "install")
        spigotRequest.baseDirectory = Path(SPIGOT_REPO_DIR).toFile()
        spigotRequest.javaHome = this.javaHome

        mavenInvoker.execute(spigotRequest)
    }

    @OptIn(ExperimentalPathApi::class)
    private fun applyPatches(what: String, target: Path, branch: String, spigotParent: Path) {
        println("Applying patches for '$what'")
        val git = Git.open(target.toFile())
        val config = git.repository.config
        config.setBoolean("commit", null, "gpgSign", false)
        config.save()


        val whatDir = spigotParent.parent.resolve(what)
        git.remoteRemove().setRemoteName("origin").call()
        git.remoteAdd().setName("origin").setUri(URIish(whatDir.toUri().toURL())).call()
        git.checkout().setName("master").call()
        git.fetch().setRemote("origin").call()
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef(branch).call()

        spigotParent.resolve("$what-Patches").walk(PathWalkOption.BREADTH_FIRST).forEach { patch ->
            println("Applying $what Patch ${patch.name}")
            process(
                "git",
                "apply",
                "--ignore-whitespace",
                patch.absolutePathString(),
                directory = target.toFile()
            ).waitFor()
        }
    }

    private fun getAndCreate(path: String, directory: Boolean): Path {
        val nioPath = Path(path)
        if (nioPath.notExists()) {
            when (directory) {
                true -> nioPath.createDirectory()
                false -> nioPath.createFile()
            }
        }

        return nioPath
    }

    private fun checkHash(expected: String, path: Path): Boolean {
        val hash = path.sha1()
        return hash == expected
    }

    private fun pull(repo: Git, ref: String): Boolean {
        println("Pulling updates for ${repo.repository.directory.parentFile.relativeTo(workPath.toFile().absoluteFile)}")

        repo.reset().setRef("origin/master").setMode(ResetCommand.ResetType.HARD).call()

        val result: FetchResult = repo.fetch().call()
        repo.reset().setRef(ref).setMode(ResetCommand.ResetType.HARD).call()

        if (ref == "master") repo.reset().setRef("origin/master").setMode(ResetCommand.ResetType.HARD).call()

        println("Checked out $ref")

        return !result.trackingRefUpdates.isEmpty()
    }

    private fun containsGit(path: Path): Boolean {
        val git = path.resolve(".git")
        return git.exists() && git.isDirectory()
    }

    private fun cloneRepo(url: String, target: Path) {
        println("Starting clone of $url to $target")
        Git.cloneRepository().setURI(url).setDirectory(target.toFile()).call().use { git ->
            val config = git.repository.config
            config.setBoolean("core", null, "autocrlf", AUTO_CRLF)
            config.save()

            val head = git.log().setMaxCount(1).call().elementAt(0).name
            clonedRepo = true

            println("Cloned repository $url to $target. Current head $head")
        }
    }

    private fun process(vararg command: String, directory: File): Process {
        return process(command.toList(), directory)
    }

    private fun process(command: List<String>, directory: File): Process {
        val processBuilder = ProcessBuilder(command)

        processBuilder.environment()["GIT_COMMITTER_NAME"] = "SpigotBuilder"
        processBuilder.environment()["GIT_COMMITTER_EMAIL"] = "unconfigured@example.org"
        processBuilder.directory(directory)
        processBuilder.redirectErrorStream(true)
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        val process = processBuilder.start()
        return process
    }


    private fun extractFile(
        extractFrom: Path,
        internalPath: String,
        output: Path,
        override: Boolean,
        toExecute: (Path) -> Unit = {}
    ) {
        if (output.exists() && !override) return
        val zip = ZipFile(extractFrom.toFile())
        val stream = zip.getInputStream(zip.getEntry("META-INF/$internalPath"))

        stream.use { input ->
            Files.copy(input, output)
        }

        toExecute.invoke(output)
    }
}