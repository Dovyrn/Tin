plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

stonecutter active "26.2"

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
