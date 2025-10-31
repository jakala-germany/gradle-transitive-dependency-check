package com.jakala.transitivedependencycheck

import com.jakala.transitivedependencycheck.extension.CheckViolationAction
import com.jakala.transitivedependencycheck.extension.MutableTransitiveDependecyCheckExtension
import com.jakala.transitivedependencycheck.extension.TransitiveDependecyCheckExtension
import com.jakala.transitivedependencycheck.task.CheckAggregatedTransitiveDependenciesTask
import com.jakala.transitivedependencycheck.task.CheckTransitiveDependenciesTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused") // Instantiated reflectively by Gradle.
class TransitiveDependencyCheckPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val targetProjects = if (project == project.rootProject) {
            project.allprojects
        } else {
            throw GradleException("Only apply this plugin to the root project. Current project: ${project.path}")
        }

        val extension = project.objects.newInstance(MutableTransitiveDependecyCheckExtension::class.java)
        project.extensions.add(TransitiveDependecyCheckExtension::class.java, "transitiveDependencyCheck", extension)

        targetProjects.map { project ->
            project.tasks.register(
                "checkTransitiveDependencies",
                CheckTransitiveDependenciesTask::class.java,
            ) { task ->
                task.transitiveUpgradeCheckViolationAction.set(extension.transitiveUpgradeCheckViolationAction)
                task.versionMismatchCheckViolationAction.set(extension.versionMismatchCheckViolationAction)
                task.transitiveUpgradeExclusion.set(extension.transitiveUpgradeExclusion)
                task.versionMismatchExclusion.set(extension.versionMismatchExclusion)
            }
        }

        val perProjectAggregationTasks = targetProjects.map { project ->
            project.tasks.register(
                "checkTransitiveDependenciesForAggregation",
                CheckTransitiveDependenciesTask::class.java,
            ) { task ->
                task.transitiveUpgradeCheckViolationAction.set(CheckViolationAction.IGNORE)
                task.versionMismatchCheckViolationAction.set(CheckViolationAction.IGNORE)
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
                    .files(taskProvider.flatMap { task -> task.declaredDependenciesFile })
                    .plus(project.files(taskProvider.flatMap { task -> task.resolvedDependenciesFile }))
            }
            task.inputReports.from(reports)
        }
    }
}
