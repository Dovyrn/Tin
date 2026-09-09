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
val metaljVersion: String by project

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
    maven("https://jitpack.io")
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    implementation("com.github.Dovyrn:MetalJ:$metaljVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

val nativeSrcDir = file("src/main/rust")
val nativeOutDir = layout.buildDirectory.dir("generated/native")

tasks.register<Exec>("buildNative") {
    workingDir = nativeSrcDir
    commandLine("cargo", "build", "--release")
    inputs.dir(File(nativeSrcDir, "src"))
    inputs.files(File(nativeSrcDir, "Cargo.toml"))
    outputs.file(File(nativeSrcDir, "target/release/libtin_shaders.dylib"))
}

tasks.register<Sync>("stageNative") {
    dependsOn("buildNative")
    into(nativeOutDir.map { File(it.asFile, "natives") })
    from(File(nativeSrcDir, "target/release/libtin_shaders.dylib"))
}

sourceSets.main {
    resources.srcDir(nativeOutDir)
}

tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn("stageNative")
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
