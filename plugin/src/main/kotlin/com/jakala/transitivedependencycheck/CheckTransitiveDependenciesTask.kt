package com.jakala.transitivedependencycheck

import com.jakala.transitivedependencycheck.DependencyDetectionHelper.compareVersions
import com.jakala.transitivedependencycheck.DependencyDetectionHelper.detectDeclaredVersionMismatches
import com.jakala.transitivedependencycheck.DependencyDetectionHelper.detectResolvedUpgradeMismatches
import com.jakala.transitivedependencycheck.DependencyDetectionHelper.isRelevantClasspath
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.PrintWriter

/**
 * Per-project task:
 * - Collects declared and resolved dependencies for the current project only
 * - Writes a report file
 * - Fails on local mismatches (declared multi-version or resolved upgrades)
 */
abstract class CheckTransitiveDependenciesTask : DefaultTask() {
    @get:Input
    abstract val ignoreFailures: Property<Boolean>

    @get:Input
    abstract val declaredDependenciesSnapshot: MapProperty<String, List<String>>

    @get:Input
    abstract val resolvedDependenciesSnapshot: MapProperty<String, String>

    @get:OutputFile
    abstract val declaredDependenciesFile: RegularFileProperty

    @get:OutputFile
    abstract val resolvedDependenciesFile: RegularFileProperty

    init {
        description = "Checks transitive dependencies for this project and writes a report"
        group = "verification"
        notCompatibleWithConfigurationCache("Inspects configurations for dependency resolution.")
        ignoreFailures.convention(false)
        declaredDependenciesFile.convention(
            project.layout.buildDirectory.file("reports/transitive-dependency-check/declared.txt"),
        )
        resolvedDependenciesFile.convention(
            project.layout.buildDirectory.file("reports/transitive-dependency-check/resolved.txt"),
        )

        declaredDependenciesSnapshot.convention(project.provider { buildDeclaredDependenciesSnapshot() })
        resolvedDependenciesSnapshot.convention(project.provider { buildResolvedDependenciesSnapshot() })
    }

    @TaskAction
    fun runCheck() {
        val declaredDependenciesSnapshotValue = declaredDependenciesSnapshot.get()
        val resolvedDependenciesSnapshotValue = resolvedDependenciesSnapshot.get()

        logger.info("[$TAG] Checking declared and resolved dependencies of ${project.path}")

        // Declared
        writeReport(
            file = declaredDependenciesFile.get().asFile,
            projectPath = project.path,
            write = {
                declaredDependenciesSnapshotValue
                    .toSortedMap(compareBy { it })
                    .forEach { (groupName, versions) ->
                        println("$KEY_DECLARED\t${groupName}\t${versions.joinToString(",")}")
                    }
            },
        )
        // Resolved
        writeReport(
            file = resolvedDependenciesFile.get().asFile,
            projectPath = project.path,
            write = {
                resolvedDependenciesSnapshotValue
                    .toSortedMap(compareBy { it })
                    .forEach { (groupName, version) -> println("$KEY_RESOLVED\t${groupName}\t$version") }
            },
        )

        // Build structures expected by detection helpers
        val declaredDependencyVersions = mutableMapOf<DependencyGroupName, MutableSet<DependencyVersion>>()
        declaredDependenciesSnapshotValue.forEach { (groupName, versions) ->
            declaredDependencyVersions[DependencyGroupName(groupName)] =
                versions.map(::DependencyVersion).toMutableSet()
        }
        val resolvedDependencies = mutableMapOf<DependencyGroupName, DependencyVersion>()
        resolvedDependenciesSnapshotValue.forEach { (groupName, version) ->
            resolvedDependencies[DependencyGroupName(groupName)] = DependencyVersion(version)
        }

        // Local project mismatches
        val declaredMismatches = detectDeclaredVersionMismatches(declaredDependencyVersions)
        val resolvedUpgradeMismatches = detectResolvedUpgradeMismatches(
            declared = declaredDependencyVersions.mapValues { it.value.toSet() },
            resolved = resolvedDependencies,
        )
        if (declaredMismatches.isNotEmpty() || resolvedUpgradeMismatches.isNotEmpty()) {
            val message = buildString {
                if (declaredMismatches.isNotEmpty()) {
                    appendLine("Some dependencies were declared with different versions in ${project.displayName}.")
                    declaredMismatches.forEach { appendLine(it) }
                }
                if (resolvedUpgradeMismatches.isNotEmpty()) {
                    appendLine("Some dependencies were upgraded transitively in ${project.displayName}.")
                    resolvedUpgradeMismatches.forEach { appendLine(it) }
                }
            }
            if (ignoreFailures.get().not()) {
                throw GradleException(message.trim())
            }
        } else {
            logger.info("[$TAG] ${project.path}: All declared dependency versions match resolved ones.")
        }
    }

    private fun writeReport(
        file: File,
        projectPath: String,
        write: PrintWriter.() -> Unit,
    ) {
        file.parentFile.mkdirs()
        file.printWriter().use { out ->
            out.println("projectPath\t$projectPath")
            out.write()
        }
        logger.info("[$TAG] Wrote report for ${project.path} to ${file.relativeTo(project.rootProject.projectDir)}")
    }

    private fun buildDeclaredDependenciesSnapshot(): Map<String, List<String>> {
        val declared = mutableMapOf<String, MutableSet<String>>()
        project.configurations.forEach { config ->
            config.dependencies.forEach { dependency ->
                val group = dependency.group
                val name = dependency.name
                val version = dependency.version
                if (group != null && version != null) {
                    val key = "$group:$name"
                    declared.getOrPut(key) { linkedSetOf() }.add(version)
                }
            }
        }
        return declared.mapValues { (_, versions) -> versions.toList().sorted() }
    }

    private fun buildResolvedDependenciesSnapshot(): Map<String, String> {
        val resolved = mutableMapOf<String, String>()
        project.configurations
            .matching { configuration -> configuration.isCanBeResolved && isRelevantClasspath(configuration.name) }
            .forEach { config ->
                runCatching {
                    val root = config.incoming.resolutionResult.root
                    val visited = mutableSetOf<ComponentIdentifier>()
                    val queue = ArrayDeque<ResolvedComponentResult>()
                    queue += root
                    while (queue.isNotEmpty()) {
                        val component = queue.removeFirst()
                        component
                            .dependencies
                            .filterIsInstance<ResolvedDependencyResult>()
                            .forEach { dep ->
                                val selected = dep.selected
                                if (visited.add(selected.id)) {
                                    queue += selected
                                }
                                val moduleId = selected.id as? ModuleComponentIdentifier ?: return@forEach
                                val key = "${moduleId.group}:${moduleId.module}"
                                val version = moduleId.version
                                val current = resolved[key]
                                if (current == null || compareVersions(
                                        DependencyVersion(version),
                                        DependencyVersion(current),
                                    ) > 0
                                ) {
                                    resolved[key] = version
                                }
                            }
                    }
                }.onFailure { throwable ->
                    logger.info("[$TAG] Failed to traverse resolution graph for ${config.name}", throwable)
                }
            }
        return resolved
    }

    companion object {
        private const val TAG = "CheckTransitiveDependenciesTask"
        internal const val KEY_DECLARED = "D"
        internal const val KEY_RESOLVED = "R"
    }
}
