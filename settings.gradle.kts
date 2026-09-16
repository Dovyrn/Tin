pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven("https://maven.kikugie.dev/releases")
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        versions("26.2", "26.3")
        vcsVersion = "26.2"
    }
}

rootProject.name = "Tin"
