package com.jakala.transitivedependencycheck

import com.jakala.transitivedependencycheck.task.CheckAggregatedTransitiveDependenciesTask
import com.jakala.transitivedependencycheck.task.CheckTransitiveDependenciesTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

class TransitiveDependencyCheckPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val targetProjects = if (project == project.rootProject) {
            project.allprojects
        } else {
            throw GradleException("Only apply this plugin to the root project. Current project: ${project.path}")
        }

        targetProjects.map { project ->
            project.tasks.register("checkTransitiveDependencies", CheckTransitiveDependenciesTask::class.java)
        }

        val perProjectAggregationTasks = targetProjects.map { project ->
            project.tasks.register(
                "checkTransitiveDependenciesForAggregation",
                CheckTransitiveDependenciesTask::class.java,
            ) {
                it.ignoreFailures.set(true)
            }
        }
        project.tasks.register(
            "checkAggregatedTransitiveDependencies",
            CheckAggregatedTransitiveDependenciesTask::class.java,
        ) { checkAggregateTask ->
            checkAggregateTask.dependsOn(perProjectAggregationTasks)
            val reports = perProjectAggregationTasks.map { taskProvider ->
                project
                    .files(taskProvider.flatMap { task -> task.declaredDependenciesFile })
                    .plus(project.files(taskProvider.flatMap { task -> task.resolvedDependenciesFile }))
            }
            checkAggregateTask.inputReports.from(reports)
        }
    }
}
