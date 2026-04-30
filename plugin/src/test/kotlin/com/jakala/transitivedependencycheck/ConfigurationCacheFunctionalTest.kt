package com.jakala.transitivedependencycheck

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class ConfigurationCacheFunctionalTest {
    @Test
    fun `checkTransitiveDependencies is compatible with the configuration cache`() {
        val projectDir = createTempDirectory(prefix = "configCacheTest").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"sample\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
            }
            """.trimIndent(),
        )

        val storeResult = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkTransitiveDependencies", "--configuration-cache")
            .build()

        assertEquals(TaskOutcome.SUCCESS, storeResult.task(":checkTransitiveDependencies")?.outcome)
        assertTrue(
            storeResult.output.contains("Configuration cache entry stored"),
            "Expected CC entry to be stored on first run, got:\n${storeResult.output}",
        )

        val reuseResult = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkTransitiveDependencies", "--configuration-cache")
            .build()

        assertEquals(TaskOutcome.UP_TO_DATE, reuseResult.task(":checkTransitiveDependencies")?.outcome)
        assertTrue(
            reuseResult.output.contains("Reusing configuration cache"),
            "Expected CC entry to be reused on second run, got:\n${reuseResult.output}",
        )
    }

    @Test
    fun `checkAggregatedTransitiveDependencies is compatible with the configuration cache`() {
        val projectDir = createTempDirectory(prefix = "configCacheAggTest").toFile()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "sample"
            include(":subA")
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
            }
            """.trimIndent(),
        )
        projectDir.resolve("subA").mkdirs()
        projectDir.resolve("subA/build.gradle.kts").writeText("")

        val storeResult = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "--configuration-cache")
            .build()

        assertEquals(
            TaskOutcome.SUCCESS,
            storeResult.task(":checkAggregatedTransitiveDependencies")?.outcome,
        )
        assertTrue(
            storeResult.output.contains("Configuration cache entry stored"),
            "Expected CC entry to be stored on first run, got:\n${storeResult.output}",
        )

        val reuseResult = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "--configuration-cache")
            .build()

        // aggregated task has no declared outputs so it re-runs every time; the CC reuse is still verified
        assertTrue(
            reuseResult.output.contains("Reusing configuration cache"),
            "Expected CC entry to be reused on second run, got:\n${reuseResult.output}",
        )
    }
}
