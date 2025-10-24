buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
    dependencies {
        classpath(libs.jetbrains.kotlin.gradle.plugin)
    }
}

allprojects {
    group = "com.jakala"
    version = System.getenv("RELEASE_VERSION") ?: "0.1.0-SNAPSHOT"
}
