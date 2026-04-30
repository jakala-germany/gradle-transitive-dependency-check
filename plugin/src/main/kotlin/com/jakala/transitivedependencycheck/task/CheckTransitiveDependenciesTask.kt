package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.extension.CheckViolationAction
import com.jakala.transitivedependencycheck.model.DependencyGroupName
import com.jakala.transitivedependencycheck.model.DependencyVersion
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.PrintWriter

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

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val projectDisplayName: Property<String>

    @get:OutputFile
    abstract val declaredDependenciesFile: RegularFileProperty

    @get:OutputFile
    abstract val resolvedDependenciesFile: RegularFileProperty

    init {
        description = "Checks transitive dependencies for this project and writes a report"
        group = "verification"

        transitiveUpgradeCheckViolationAction.convention(CheckViolationAction.FAIL)
        versionMismatchCheckViolationAction.convention(CheckViolationAction.FAIL)
        transitiveUpgradeExclusion.convention(emptySet())
        versionMismatchExclusion.convention(emptySet())
    }

    @TaskAction
    fun runCheck() {
        val path = projectPath.get()
        val displayName = projectDisplayName.get()
        val declaredSnap = declaredDependenciesSnapshot.get()
        val resolvedSnap = resolvedDependenciesSnapshot.get()

        logger.info("[$TAG] Checking declared and resolved dependencies of $path")

        writeReport(
            file = declaredDependenciesFile.get().asFile,
            projectPath = path,
            write = {
                declaredSnap
                    .toSortedMap(compareBy { it })
                    .forEach { (groupName, versions) ->
                        println("$KEY_DECLARED\t$groupName\t${versions.joinToString(",")}")
                    }
            },
        )
        writeReport(
            file = resolvedDependenciesFile.get().asFile,
            projectPath = path,
            write = {
                resolvedSnap
                    .toSortedMap(compareBy { it })
                    .forEach { (groupName, version) -> println("$KEY_RESOLVED\t$groupName\t$version") }
            },
        )

        val declaredDependencyVersions = mutableMapOf<DependencyGroupName, MutableSet<DependencyVersion>>()
        declaredSnap.forEach { (groupName, versions) ->
            declaredDependencyVersions[DependencyGroupName(groupName)] =
                versions.map(::DependencyVersion).toMutableSet()
        }
        val resolvedDependencies = mutableMapOf<DependencyGroupName, DependencyVersion>()
        resolvedSnap.forEach { (groupName, version) ->
            resolvedDependencies[DependencyGroupName(groupName)] = DependencyVersion(version)
        }

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
            declaredHeader = "Some dependencies were declared with different versions in $displayName.",
            resolvedHeader = "Some dependencies were upgraded transitively in $displayName.",
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
        logger.info("[$TAG] Wrote report for $projectPath to $file")
    }

    companion object {
        private const val TAG = "CheckTransitiveDependenciesTask"
        internal const val KEY_DECLARED = "D"
        internal const val KEY_RESOLVED = "R"
    }
}
