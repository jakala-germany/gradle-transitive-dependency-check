package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.model.DependencyGroupName
import com.jakala.transitivedependencycheck.model.DependencyVersion
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CheckAggregatedTransitiveDependenciesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputReports: ConfigurableFileCollection

    init {
        description = "Checks aggregated per-project transitive dependency reports and checks cross-project mismatches"
        group = "verification"
        notCompatibleWithConfigurationCache("Aggregated task outputs across projects.")
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
            declaredGlobal.mapValues { it.value.toSet() },
        )
        val resolvedUpgradeMismatches = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
            declaredGlobal.mapValues { it.value.toSet() },
            resolvedGlobal,
        )

        if (declaredMismatches.isNotEmpty() || resolvedUpgradeMismatches.isNotEmpty()) {
            val message = buildString {
                if (declaredMismatches.isNotEmpty()) {
                    appendLine("Some dependencies were declared with different versions across projects.")
                    declaredMismatches.forEach { appendLine(it) }
                }
                if (resolvedUpgradeMismatches.isNotEmpty()) {
                    appendLine("Some dependencies were upgraded transitively across projects.")
                    resolvedUpgradeMismatches.forEach { appendLine(it) }
                }
            }
            throw GradleException(message.trim())
        } else {
            logger.info("[$TAG] All declared dependency versions match resolved ones across projects.")
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
