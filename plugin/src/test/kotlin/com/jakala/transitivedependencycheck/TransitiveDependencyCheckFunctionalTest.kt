package com.jakala.transitivedependencycheck

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class TransitiveDependencyCheckFunctionalTest {
    @Test
    fun `fails when declared dependency is overridden within same module by newer transitive dependency`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
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
            .withArguments("checkTransitiveDependencies", "-s")
            .buildAndFail()

        val output = result.output
        assertTrue(output.contains("Some dependencies were upgraded transitively in root project \'sample\'."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.FAILED, result.task(":checkTransitiveDependencies")?.outcome)
    }

    @Test
    fun `warns when declared dependency is overridden within same module by newer transitive dependency but warn`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    transitiveUpgradeCheckViolationAction.set(CheckViolationAction.WARN)
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
            .withArguments("checkTransitiveDependencies", "-s")
            .build()

        val output = result.output
        assertTrue(output.contains("Some dependencies were upgraded transitively in root project \'sample\'."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkTransitiveDependencies")?.outcome)
    }

    @Test
    fun `succeeds when declared dependency is overridden within same module by newer dependency but excluded`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    transitiveUpgradeExclusion.add("commons-codec:commons-codec:.*")
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
            .withArguments("checkTransitiveDependencies", "-s")
            .build()

        val output = result.output
        assertFalse(output.contains("Some dependencies were upgraded transitively in root project \'sample\'."), output)
        assertFalse(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkTransitiveDependencies")?.outcome)
    }

    @Test
    fun `fails when declared dependency is overridden within same module by newer but exclusion is not matching`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    transitiveUpgradeExclusion.add("commons-codec:commons-codec2:.*")
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
            .withArguments("checkTransitiveDependencies", "-s")
            .buildAndFail()

        val output = result.output
        assertTrue(output.contains("Some dependencies were upgraded transitively in root project \'sample\'."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.FAILED, result.task(":checkTransitiveDependencies")?.outcome)
    }

    @Test
    fun `succeeds when checking module directly for declared dependency override by newer dependency`() {
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
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
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

        val resultModuleOne = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(":${moduleOneDir.name}:checkTransitiveDependencies", "-s")
            .build()
        assertEquals(
            TaskOutcome.SUCCESS,
            resultModuleOne.task(":${moduleOneDir.name}:checkTransitiveDependencies")?.outcome,
        )

        val resultModuleTwo = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(":${moduleTwoDir.name}:checkTransitiveDependencies", "-s")
            .build()
        assertEquals(
            TaskOutcome.SUCCESS,
            resultModuleTwo.task(":${moduleTwoDir.name}:checkTransitiveDependencies")?.outcome,
        )
    }

    @Test
    fun `succeeds when only plugin-internal tool configurations contain conflicting versions`() {
        val projectDir = createTempDir(prefix = "transitiveDependencyCheck")
        projectDir
            .resolve("settings.gradle.kts")
            .writeText("rootProject.name = \"sample\"\n")
        projectDir
            .resolve("build.gradle.kts")
            .writeText(
                """
                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                repositories {
                    mavenCentral()
                }

                // Simulates how plugins like ktlint and detekt each create their own
                // tool classpath and add dependencies to it. The user does not declare
                // these in implementation/api and has no control over them.
                val ktlintTool by configurations.creating
                val detektTool by configurations.creating

                dependencies {
                    ktlintTool("io.github.detekt.sarif4k:sarif4k:0.5.0")
                    detektTool("io.github.detekt.sarif4k:sarif4k:0.6.0")
                }
                """.trimIndent(),
            )

        val result = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkTransitiveDependencies", "-s")
            .build()

        val output = result.output
        assertFalse(
            output.contains("io.github.detekt.sarif4k:sarif4k declared with multiple versions"),
            output,
        )
        assertFalse(
            output.contains("Some dependencies were declared with different versions in root project \'sample\'."),
            output,
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkTransitiveDependencies")?.outcome)
    }

    private fun createTempDir(path: Path? = null, prefix: String = ""): File {
        val dir = createTempDirectory(path, prefix).toFile()
        dir.deleteOnExit()
        return dir
    }
}
