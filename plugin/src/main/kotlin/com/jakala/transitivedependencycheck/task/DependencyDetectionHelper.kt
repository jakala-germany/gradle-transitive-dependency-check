package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.model.DependencyGroupName
import com.jakala.transitivedependencycheck.model.DependencyVersion

object DependencyDetectionHelper {
    internal fun compareVersions(
        versionOne: DependencyVersion,
        versionTwo: DependencyVersion,
    ): Int {
        val partsOne = versionOne.value.split(".", "-", "_")
        val partsTwo = versionTwo.value.split(".", "-", "_")
        val maxPartsSize = maxOf(partsOne.size, partsTwo.size)
        for (i in 0 until maxPartsSize) {
            val a = partsOne.getOrNull(i)?.toIntOrNull() ?: 0
            val b = partsTwo.getOrNull(i)?.toIntOrNull() ?: 0
            val comparison = a.compareTo(b)
            if (comparison != 0) return comparison
        }
        return 0
    }

    internal fun isRelevantClasspath(name: String): Boolean {
        return name.endsWith(suffix = "compileClasspath", ignoreCase = true) ||
            name.endsWith(suffix = "runtimeClasspath", ignoreCase = true)
    }

    internal fun detectDeclaredVersionMismatches(
        declared: Map<DependencyGroupName, Set<DependencyVersion>>,
    ): List<String> {
        return declared.mapNotNull { (key, versions) ->
            if (versions.size > 1) {
                "$key declared with multiple versions → ${versions.joinToString { it.value }}"
            } else {
                null
            }
        }
    }

    internal fun detectResolvedUpgradeMismatches(
        declared: Map<DependencyGroupName, Set<DependencyVersion>>,
        resolved: Map<DependencyGroupName, DependencyVersion>,
    ): List<String> {
        return declared.mapNotNull { (key, versions) ->
            if (versions.isEmpty()) return@mapNotNull null
            if (versions.size > 1) return@mapNotNull null // handled by declared mismatches
            val declaredVersion = versions.first()
            val resolvedVersion = resolved[key] ?: return@mapNotNull null
            if (resolvedVersion != declaredVersion && compareVersions(resolvedVersion, declaredVersion) > 0) {
                "$key declared with ${declaredVersion.value} → resolved as ${resolvedVersion.value}"
            } else {
                null
            }
        }
    }
}
