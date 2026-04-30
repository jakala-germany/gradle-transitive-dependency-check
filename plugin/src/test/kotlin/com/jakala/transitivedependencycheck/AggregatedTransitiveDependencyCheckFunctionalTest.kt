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
    fun `warns when declared dependency is overridden by newer transitive dependency in subprojects but warn`() {
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
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    transitiveUpgradeCheckViolationAction.set(CheckViolationAction.WARN)
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
            .build()

        val output = result.output
        assertTrue(output.contains("Some dependencies were upgraded transitively across projects."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `succeeds when declared dependency is overridden by newer transitive dependency in subprojects but excluded`() {
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
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    transitiveUpgradeExclusion.add("commons-codec:.*:.*")
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
            .build()

        val output = result.output
        assertFalse(output.contains("Some dependencies were upgraded transitively across projects."), output)
        assertFalse(output.contains("commons-codec:commons-codec declared with 1.10 → resolved as 1.11"), output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `fails when declared dependency is overridden by newer transitive dependency but exclusion is not matching`() {
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
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    transitiveUpgradeExclusion.add("commons-codec2:.*:.*")
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
    fun `warns when declared dependencies differ in multiple modules but warn`() {
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
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    versionMismatchCheckViolationAction.set(CheckViolationAction.WARN)
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
            .build()

        val output = result.output
        assertTrue(output.contains("Some dependencies were declared with different versions across projects."), output)
        assertTrue(output.contains("commons-codec:commons-codec declared with multiple versions → 1.10, 1.11"), output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `succeeds when declared dependencies differ in multiple modules but excluded`() {
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
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    versionMismatchExclusion.add("commons-codec:commons-codec:.*")
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
            .build()

        val output = result.output
        assertFalse(output.contains("Some dependencies were declared with different versions across projects."), output)
        assertFalse(output.contains("commons-codec:commons-codec declared with multiple versions → 1.10, 1.11"), output)
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    @Test
    fun `fails when declared dependencies differ in multiple modules but exclusion is not matching`() {
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
                import com.jakala.transitivedependencycheck.extension.CheckViolationAction

                plugins {
                    id("java")
                    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin")
                }

                transitiveDependencyCheck {
                    versionMismatchExclusion.add("commons-codec:commons-codec2:.*")
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
                "rootProject.name = \"sample\"\ninclude(\"${moduleOneDir.name}\", \"${moduleTwoDir.name}\", \"${moduleThreeDir.name}\")\n",
            )
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
                "rootProject.name = \"sample\"\ninclude(\"${moduleOneDir.name}\", \"${moduleTwoDir.name}\", \"${moduleThreeDir.name}\")\n",
            )
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

    @Test
    fun `succeeds when only plugin-internal tool configurations contain conflicting versions across modules`() {
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

                val ktlintTool by configurations.creating

                dependencies {
                    ktlintTool("io.github.detekt.sarif4k:sarif4k:0.5.0")
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

                val detektTool by configurations.creating

                dependencies {
                    detektTool("io.github.detekt.sarif4k:sarif4k:0.6.0")
                }
                """.trimIndent(),
            )

        val result = GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("checkAggregatedTransitiveDependencies", "-s")
            .build()

        val output = result.output
        assertFalse(
            output.contains("io.github.detekt.sarif4k:sarif4k declared with multiple versions"),
            output,
        )
        assertFalse(
            output.contains("Some dependencies were declared with different versions across projects."),
            output,
        )
        assertEquals(TaskOutcome.SUCCESS, result.task(":checkAggregatedTransitiveDependencies")?.outcome)
    }

    private fun createTempDir(path: Path? = null, prefix: String = ""): File {
        val dir = createTempDirectory(path, prefix).toFile()
        dir.deleteOnExit()
        return dir
    }
}
