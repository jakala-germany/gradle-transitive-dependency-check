package com.jakala.transitivedependencycheck

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class AggregatedTransitiveDependencyCheckFunctionalTest {
    @Test
    fun `fails when declared dependency is overridden by newer transitive dependency in subprojects`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        val moduleOneDir = createTempDir(path = projectDir.toPath(), prefix = "moduleOne")
        val moduleTwoDir = createTempDir(path = projectDir.toPath(), prefix = "moduleTwo")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\ninclude(\"${moduleOneDir.name}\", \"${moduleTwoDir.name}\")\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                    id("com.jakala.transitive-dependency-check-gradle-plugin")
                }
                """.trimIndent(),
            )
        moduleOneDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.apache.httpcomponents:httpclient:4.5.13")
                }
                """.trimIndent(),
            )
        moduleTwoDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("commons-codec:commons-codec:1.10")
                }
                """.trimIndent(),
            )

        val result = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "-s")
            .buildAndFail()

        val output = result.output
        assertTrue(output.contains("Some dependencies were upgraded transitively across projects."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.FAILED, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `fails when declared dependencies differ in multiple modules`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        val moduleOneDir = createTempDir(path = projectDir.toPath(), prefix = "moduleOne")
        val moduleTwoDir = createTempDir(path = projectDir.toPath(), prefix = "moduleTwo")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\ninclude(\"${moduleOneDir.name}\", \"${moduleTwoDir.name}\")\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                    id("com.jakala.transitive-dependency-check-gradle-plugin")
                }
                """.trimIndent(),
            )
        moduleOneDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("commons-codec:commons-codec:1.10")
                }
                """.trimIndent(),
            )
        moduleTwoDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("commons-codec:commons-codec:1.11")
                }
                """.trimIndent(),
            )

        val result = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "-s")
            .buildAndFail()

        val output = result.output
        assertTrue(output.contains("Some dependencies were declared with different versions across projects."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with multiple versions → 1.10, 1.11"), output)
        assertEquals(TaskOutcome.FAILED, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `fails when declared dependency is overridden within multiple projects by newer transitive dependency`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        val moduleOneDir = createTempDir(path = projectDir.toPath(), prefix = "moduleOne")
        val moduleTwoDir = createTempDir(path = projectDir.toPath(), prefix = "moduleTwo")
        val moduleThreeDir = createTempDir(path = projectDir.toPath(), prefix = "moduleThree")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText(
                "rootProject.name = \"sample\"\ninclude(\"${moduleOneDir.name}\", \"${moduleTwoDir.name}\", \"${moduleThreeDir.name}\")\n"
            )
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                    id("com.jakala.transitive-dependency-check-gradle-plugin")
                }
                """.trimIndent(),
            )
        moduleOneDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.apache.httpcomponents:httpclient:4.5.13")
                    implementation("commons-codec:commons-codec:1.10")
                }
                """.trimIndent(),
            )
        moduleTwoDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.apache.httpcomponents:httpclient:4.5.13")
                }
                """.trimIndent(),
            )
        moduleThreeDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.apache.httpcomponents:httpclient:4.5.13")
                    implementation("commons-codec:commons-codec:1.10")
                }
                """.trimIndent(),
            )

        val result = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "-s")
            .buildAndFail()

        val output = result.output
        assertTrue(output.contains("Some dependencies were upgraded transitively across projects."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.FAILED, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `succeeds when declared dependency are not overridden within multiple projects`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        val moduleOneDir = createTempDir(path = projectDir.toPath(), prefix = "moduleOne")
        val moduleTwoDir = createTempDir(path = projectDir.toPath(), prefix = "moduleTwo")
        val moduleThreeDir = createTempDir(path = projectDir.toPath(), prefix = "moduleThree")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText(
                "rootProject.name = \"sample\"\ninclude(\"${moduleOneDir.name}\", \"${moduleTwoDir.name}\", \"${moduleThreeDir.name}\")\n"
            )
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                    id("com.jakala.transitive-dependency-check-gradle-plugin")
                }
                """.trimIndent(),
            )
        moduleOneDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.apache.httpcomponents:httpclient:4.5.13")
                    implementation("commons-codec:commons-codec:1.11")
                }
                """.trimIndent(),
            )
        moduleTwoDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("commons-codec:commons-codec:1.11")
                }
                """.trimIndent(),
            )
        moduleThreeDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                }

                repositories {
                    mavenCentral()
                }

                dependencies {
                    implementation("org.apache.httpcomponents:httpclient:4.5.13")
                    implementation("commons-codec:commons-codec:1.11")
                }
                """.trimIndent(),
            )

        val result = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "-s")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    private fun createTempDir(path: Path? = null, prefix: String = ""): File {
        val dir = createTempDirectory(path, prefix).toFile()
        dir.deleteOnExit()
        return dir
    }
}
