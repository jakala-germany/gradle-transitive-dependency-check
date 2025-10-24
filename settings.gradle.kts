enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "gradle-transitive-dependency-check"

include(":plugin")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}
