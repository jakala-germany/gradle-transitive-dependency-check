package com.jakala.transitivedependencycheck

import com.jakala.transitivedependencycheck.extension.CheckViolationAction
import com.jakala.transitivedependencycheck.extension.MutableTransitiveDependencyCheckExtension
import com.jakala.transitivedependencycheck.extension.TransitiveDependencyCheckExtension
import com.jakala.transitivedependencycheck.model.DependencyVersion
import com.jakala.transitivedependencycheck.task.CheckAggregatedTransitiveDependenciesTask
import com.jakala.transitivedependencycheck.task.CheckTransitiveDependenciesTask
import com.jakala.transitivedependencycheck.task.DependencyDetectionHelper
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult

@Suppress("unused") // Instantiated reflectively by Gradle.
class TransitiveDependencyCheckPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val targetProjects = if (project == project.rootProject) {
            project.allprojects
        } else {
            throw GradleException("Only apply this plugin to the root project. Current project: ${project.path}")
        }

        val extension = project.objects.newInstance(MutableTransitiveDependencyCheckExtension::class.java)
        project.extensions.add(TransitiveDependencyCheckExtension::class.java, "transitiveDependencyCheck", extension)

        targetProjects.map { subProject ->
            subProject.tasks.register(
                "checkTransitiveDependencies",
                CheckTransitiveDependenciesTask::class.java,
            ) { task ->
                task.transitiveUpgradeCheckViolationAction.set(extension.transitiveUpgradeCheckViolationAction)
                task.versionMismatchCheckViolationAction.set(extension.versionMismatchCheckViolationAction)
                task.transitiveUpgradeExclusion.set(extension.transitiveUpgradeExclusion)
                task.versionMismatchExclusion.set(extension.versionMismatchExclusion)
                wireProjectProperties(task, subProject)
            }
        }

        val perProjectAggregationTasks = targetProjects.map { subProject ->
            subProject.tasks.register(
                "checkTransitiveDependenciesForAggregation",
                CheckTransitiveDependenciesTask::class.java,
            ) { task ->
                task.transitiveUpgradeCheckViolationAction.set(CheckViolationAction.IGNORE)
                task.versionMismatchCheckViolationAction.set(CheckViolationAction.IGNORE)
                wireProjectProperties(task, subProject)
            }
        }
        project.tasks.register(
            "checkAggregatedTransitiveDependencies",
            CheckAggregatedTransitiveDependenciesTask::class.java,
        ) { task ->
            task.transitiveUpgradeCheckViolationAction.set(extension.transitiveUpgradeCheckViolationAction)
            task.versionMismatchCheckViolationAction.set(extension.versionMismatchCheckViolationAction)
            task.transitiveUpgradeExclusion.set(extension.transitiveUpgradeExclusion)
            task.versionMismatchExclusion.set(extension.versionMismatchExclusion)
            task.dependsOn(perProjectAggregationTasks)
            val reports = perProjectAggregationTasks.map { taskProvider ->
                project
                    .files(taskProvider.flatMap { t -> t.declaredDependenciesFile })
                    .plus(project.files(taskProvider.flatMap { t -> t.resolvedDependenciesFile }))
            }
            task.inputReports.from(reports)
        }
    }

    private fun wireProjectProperties(task: CheckTransitiveDependenciesTask, subProject: Project) {
        task.projectPath.set(subProject.path)
        task.projectDisplayName.set(subProject.displayName)
        task.declaredDependenciesFile.convention(
            subProject.layout.buildDirectory.file("reports/transitive-dependency-check/declared.txt"),
        )
        task.resolvedDependenciesFile.convention(
            subProject.layout.buildDirectory.file("reports/transitive-dependency-check/resolved.txt"),
        )
        task.declaredDependenciesSnapshot.set(
            subProject.provider { buildDeclaredSnapshot(subProject) },
        )
        task.resolvedDependenciesSnapshot.set(
            subProject.provider { buildResolvedSnapshot(subProject) },
        )
    }

    private fun buildDeclaredSnapshot(project: Project): Map<String, List<String>> {
        val declared = mutableMapOf<String, MutableSet<String>>()
        project.configurations
            .matching { DependencyDetectionHelper.isRelevantClasspath(it.name) }
            .forEach { config ->
                config.allDependencies.forEach { dep ->
                    val g = dep.group
                    val v = dep.version
                    if (g != null && v != null) {
                        declared.getOrPut("$g:${dep.name}") { linkedSetOf() }.add(v)
                    }
                }
            }
        return declared.mapValues { (_, vs) -> vs.toList().sorted() }
    }

    private fun buildResolvedSnapshot(project: Project): Map<String, String> {
        val resolved = mutableMapOf<String, String>()
        project.configurations
            .matching { it.isCanBeResolved && DependencyDetectionHelper.isRelevantClasspath(it.name) }
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
                                if (current == null ||
                                    DependencyDetectionHelper.compareVersions(
                                        DependencyVersion(version),
                                        DependencyVersion(current),
                                    ) > 0
                                ) {
                                    resolved[key] = version
                                }
                            }
                    }
                }.onFailure { throwable ->
                    project.logger.warn(
                        "[CheckTransitiveDependenciesTask] Failed to traverse resolution graph for ${config.name}",
                        throwable,
                    )
                }
            }
        return resolved
    }
}
