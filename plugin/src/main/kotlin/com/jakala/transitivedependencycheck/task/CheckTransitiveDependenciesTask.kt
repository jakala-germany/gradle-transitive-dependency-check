package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.extension.CheckViolationAction
import com.jakala.transitivedependencycheck.model.DependencyGroupName
import com.jakala.transitivedependencycheck.model.DependencyVersion
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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
    abstract val transitiveUpgradeCheckViolationAction: Property<CheckViolationAction>

    @get:Input
    abstract val versionMismatchCheckViolationAction: Property<CheckViolationAction>

    @get:Input
    abstract val transitiveUpgradeExclusion: SetProperty<String>

    @get:Input
    abstract val versionMismatchExclusion: SetProperty<String>

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

        transitiveUpgradeCheckViolationAction.convention(CheckViolationAction.FAIL)
        versionMismatchCheckViolationAction.convention(CheckViolationAction.FAIL)
        transitiveUpgradeExclusion.convention(emptySet())
        versionMismatchExclusion.convention(emptySet())

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
        val declaredMismatches = DependencyDetectionHelper.detectDeclaredVersionMismatches(
            declared = declaredDependencyVersions,
            exclusions = versionMismatchExclusion.get().map { it.toRegex() },
        )
        val resolvedUpgradeMismatches = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
            declared = declaredDependencyVersions.mapValues { it.value.toSet() },
            resolved = resolvedDependencies,
            exclusions = transitiveUpgradeExclusion.get().map { it.toRegex() },
        )

        ViolationReporter.report(
            declaredMismatches = declaredMismatches,
            resolvedMismatches = resolvedUpgradeMismatches,
            versionMismatchAction = versionMismatchCheckViolationAction.get(),
            transitiveUpgradeAction = transitiveUpgradeCheckViolationAction.get(),
            declaredHeader = "Some dependencies were declared with different versions in ${project.displayName}.",
            resolvedHeader = "Some dependencies were upgraded transitively in ${project.displayName}.",
            tag = TAG,
            logger = logger,
        )
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
        project.configurations
            .matching { configuration -> DependencyDetectionHelper.isRelevantClasspath(configuration.name) }
            .forEach { config ->
                config.allDependencies.forEach { dependency ->
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
            .matching { configuration ->
                configuration.isCanBeResolved && DependencyDetectionHelper.isRelevantClasspath(configuration.name)
            }.forEach { config ->
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
                                if (current == null || DependencyDetectionHelper.compareVersions(
                                        DependencyVersion(version),
                                        DependencyVersion(current),
                                    ) > 0
                                ) {
                                    resolved[key] = version
                                }
                            }
                    }
                }.onFailure { throwable ->
                    logger.warn("[$TAG] Failed to traverse resolution graph for ${config.name}", throwable)
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
