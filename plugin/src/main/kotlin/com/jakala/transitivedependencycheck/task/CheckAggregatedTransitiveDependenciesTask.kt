package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.extension.CheckViolationAction
import com.jakala.transitivedependencycheck.model.DependencyGroupName
import com.jakala.transitivedependencycheck.model.DependencyVersion
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CheckAggregatedTransitiveDependenciesTask : DefaultTask() {
    @get:Input
    abstract val transitiveUpgradeCheckViolationAction: Property<CheckViolationAction>

    @get:Input
    abstract val versionMismatchCheckViolationAction: Property<CheckViolationAction>

    @get:Input
    abstract val transitiveUpgradeExclusion: SetProperty<String>

    @get:Input
    abstract val versionMismatchExclusion: SetProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputReports: ConfigurableFileCollection

    init {
        description = "Checks aggregated per-project transitive dependency reports and checks cross-project mismatches"
        group = "verification"
        notCompatibleWithConfigurationCache("Aggregated task outputs across projects.")

        transitiveUpgradeCheckViolationAction.convention(CheckViolationAction.FAIL)
        versionMismatchCheckViolationAction.convention(CheckViolationAction.FAIL)
        transitiveUpgradeExclusion.convention(emptySet())
        versionMismatchExclusion.convention(emptySet())
    }

    @TaskAction
    fun aggregate() {
        val declaredGlobal = mutableMapOf<DependencyGroupName, MutableSet<DependencyVersion>>()
        val resolvedGlobal = mutableMapOf<DependencyGroupName, DependencyVersion>()

        inputReports.files
            .forEach { file ->
                parseReport(file) { kind, groupName, versionsOrVersion ->
                    when (kind) {
                        CheckTransitiveDependenciesTask.Companion.KEY_DECLARED -> {
                            val key = DependencyGroupName(groupName)
                            val versions = versionsOrVersion.split(",").filter { it.isNotBlank() }
                            declaredGlobal
                                .getOrPut(key) { linkedSetOf() }
                                .addAll(versions.map { version -> DependencyVersion(version) })
                        }

                        CheckTransitiveDependenciesTask.Companion.KEY_RESOLVED -> {
                            val key = DependencyGroupName(groupName)
                            val version = DependencyVersion(versionsOrVersion)
                            val current = resolvedGlobal[key]
                            if (
                                current == null ||
                                DependencyDetectionHelper.compareVersions(version, current) > 0
                            ) {
                                resolvedGlobal[key] = version
                            }
                        }
                    }
                }
            }

        val declaredMismatches = DependencyDetectionHelper.detectDeclaredVersionMismatches(
            declared = declaredGlobal.mapValues { it.value.toSet() },
            exclusions = versionMismatchExclusion.get().map { it.toRegex() },
        )
        val resolvedUpgradeMismatches = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
            declared = declaredGlobal.mapValues { it.value.toSet() },
            resolved = resolvedGlobal,
            exclusions = transitiveUpgradeExclusion.get().map { it.toRegex() },
        )

        val mismatchExclusionRegex = versionMismatchExclusion.get().map { it.toRegex() }
        val upgradeExclusionRegex = transitiveUpgradeExclusion.get().map { it.toRegex() }

        when {
            declaredMismatches.isNotEmpty() -> {
                val message = buildString {
                    appendLine("Some dependencies were declared with different versions across projects.")
                    declaredMismatches.forEach { appendLine(it) }
                }.trim()
                when (versionMismatchCheckViolationAction.get()) {
                    CheckViolationAction.FAIL -> throw GradleException(message)
                    CheckViolationAction.WARN -> logger.warn("[$TAG] $message")
                    CheckViolationAction.IGNORE -> Unit
                }
            }

            resolvedUpgradeMismatches.isNotEmpty() -> {
                val message = buildString {
                    appendLine("Some dependencies were upgraded transitively across projects.")
                    resolvedUpgradeMismatches.forEach { appendLine(it) }
                }.trim()
                when (transitiveUpgradeCheckViolationAction.get()) {
                    CheckViolationAction.FAIL -> throw GradleException(message)
                    CheckViolationAction.WARN -> logger.warn("[$TAG] $message")
                    CheckViolationAction.IGNORE -> Unit
                }
            }

            else -> {
                logger.info("[$TAG] All declared dependency versions look fine.")
            }
        }
    }

    private fun parseReport(
        file: File,
        onEntry: (kind: String, groupName: String, versionsOrVersion: String) -> Unit,
    ) {
        file.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                CheckTransitiveDependenciesTask.Companion.KEY_DECLARED -> {
                    val ga = parts.getOrNull(1) ?: return@forEachLine
                    val versions = parts.getOrNull(2) ?: ""
                    onEntry(CheckTransitiveDependenciesTask.Companion.KEY_DECLARED, ga, versions)
                }

                CheckTransitiveDependenciesTask.Companion.KEY_RESOLVED -> {
                    val ga = parts.getOrNull(1) ?: return@forEachLine
                    val version = parts.getOrNull(2) ?: ""
                    onEntry(CheckTransitiveDependenciesTask.Companion.KEY_RESOLVED, ga, version)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CheckAggregatedTransitiveDependenciesTask"
    }
}
