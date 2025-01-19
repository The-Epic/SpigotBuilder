import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.0.20"
    id("com.gradleup.shadow") version "8.3.3"
}

group = "xyz.epicebic"
version = "1.0.0-SNAPSHOT"


repositories {
    mavenCentral()
    maven("https://maven.miles.sh/snapshots")
    maven("https://maven.fabricmc.net/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.eclipse.org/content/repositories/jgit-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenLocal()
}


dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0-SNAPSHOT")
    implementation("net.sf.jopt-simple:jopt-simple:6.0-alpha-3")
    implementation("com.google.guava:guava:33.3.1-jre")
    implementation("io.papermc:patched-spigot-fernflower:0.1+build.12")
    implementation("net.md_5:ss:1.0.0")
    implementation("net.md-5:SpecialSource:1.11.4")

    implementation("io.codechicken:DiffPatch:2.0.0.36")
    implementation("commons-io:commons-io:2.17.0")
    implementation("org.apache.maven.shared:maven-invoker:3.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "xyz.epicebic.spigotbuilder.MainKt"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

kotlin {
    target.compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
    sourceCompatibility = JavaVersion.VERSION_21
}