rootProject.name = "tickradar"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        maven("https://repo.extendedclip.com/releases/") {
            name = "extendedclip"
            content {
                includeGroup("me.clip")
            }
        }
    }
}

include(":plugin")

if (file("loadgen/build.gradle.kts").isFile) {
    include(":loadgen")
}
