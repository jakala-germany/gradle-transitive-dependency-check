package com.jakala.transitivedependencycheck.extension

import org.gradle.api.provider.Property

interface TransitiveDependecyCheckExtension {
    /**
     * Set the [CheckViolationAction] for transitive dependency upgrade check.
     * Default: [CheckViolationAction.FAIL]
     */
    val transitiveUpgradeCheckViolationAction: Property<CheckViolationAction>

    /**
     * Set the [CheckViolationAction] for dependency version mismatches between modules.
     * Default: [CheckViolationAction.FAIL]
     */
    val versionMismatchCheckViolationAction: Property<CheckViolationAction>
}

internal abstract class MutableTransitiveDependecyCheckExtension : TransitiveDependecyCheckExtension {
    init {
        transitiveUpgradeCheckViolationAction.convention(CheckViolationAction.FAIL)
        versionMismatchCheckViolationAction.convention(CheckViolationAction.FAIL)
    }
}
