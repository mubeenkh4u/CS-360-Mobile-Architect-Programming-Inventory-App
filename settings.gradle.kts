pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Auwire"
include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:data",
    ":core:ui",
    ":feature:workspace",
    ":feature:inventory",
    ":feature:invoice",
)
