plugins {
    id("net.fabricmc.fabric-loom")
    id("maven-publish")
}

val modVersion: String by project
val mavenGroup: String by project
val archivesBaseName: String by project
val minecraftVersion = stonecutter.current.version
val loaderVersion: String by project
val lombokVersion: String by project
val metaljVersion: String by project

version = "$modVersion+$minecraftVersion"
group = mavenGroup

base {
    archivesName = archivesBaseName
}

loom {
    runConfigs.named("client") {
        runDir = rootProject.file("run").relativeTo(projectDir).path
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

    implementation("com.github.Dovyrn:MetalJ:$metaljVersion")
    include("com.github.Dovyrn:MetalJ:$metaljVersion")

    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
}

sourceSets.main {
    resources.srcDir(rootProject.layout.buildDirectory.dir("generated/native"))
}

tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(":stageNative")
}

tasks.processResources {
    dependsOn(":stageNative")
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
    from(rootProject.file("LICENSE.txt")) {
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
