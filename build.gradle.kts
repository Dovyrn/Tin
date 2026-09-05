plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
    id("maven-publish")
}

val modVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project
val minecraftVersion: String by project
val loaderVersion: String by project
val fabricVersion: String by project
val lombokVersion: String by project

version = modVersion
group = mavenGroup

base {
    archivesName = archivesBaseName
}

loom {
    runConfigs.named("client") {
        vmArgs("-Xmx2500m")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

val nativeSrcDir = file("src/main/rust")
val nativeOutDir = layout.buildDirectory.dir("generated/native")

val archName = System.getProperty("os.arch").lowercase()
val hostArch = if (archName in listOf("aarch64", "arm64")) "aarch64" else "x86_64"
val hostTarget = "$hostArch-apple-darwin"

val nativeTargets = when {
    !project.hasProperty("targets") -> listOf(hostTarget)
    project.property("targets") == "all" -> listOf("aarch64-apple-darwin", "x86_64-apple-darwin")
    else -> project.property("targets").toString().split(",").map { it.trim() }
}

fun nativeArtifact(triple: String) = File(nativeSrcDir, "target/$triple/release/libtin_native.dylib")

nativeTargets.forEach { triple ->
    tasks.register<Exec>("buildNative-$triple") {
        workingDir = nativeSrcDir
        commandLine("cargo", "build", "--release", "--target", triple)
        inputs.dir(File(nativeSrcDir, "src"))
        inputs.dir(File(nativeSrcDir, "crates"))
        inputs.files(File(nativeSrcDir, "Cargo.toml"), File(nativeSrcDir, "Cargo.lock"))
        outputs.file(nativeArtifact(triple))
    }
}

tasks.register<Sync>("stageNative") {
    dependsOn(nativeTargets.map { "buildNative-$it" })
    into(nativeOutDir.map { File(it.asFile, "natives") })
    nativeTargets.forEach { triple ->
        from(nativeArtifact(triple)) {
            into("macos/${triple.substringBefore("-")}")
        }
    }
}

sourceSets.main {
    resources.srcDir(nativeOutDir)
}

tasks.processResources {
    dependsOn("stageNative")
    inputs.property("version", project.version)
    inputs.property("minecraft_version", minecraftVersion)
    inputs.property("loader_version", loaderVersion)
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to minecraftVersion,
            "loader_version" to loaderVersion,
        )
    }
}

val targetJavaVersion = 25

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:deprecation")
    options.release = targetJavaVersion
}

tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn("stageNative")
}

java {
    if (JavaVersion.current() < JavaVersion.toVersion(targetJavaVersion)) {
        toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    }
    withSourcesJar()
}

tasks.jar {
    from("LICENSE.txt") {
        rename { "${it}_$archivesBaseName" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = archivesBaseName
            from(components["java"])
        }
    }
}
