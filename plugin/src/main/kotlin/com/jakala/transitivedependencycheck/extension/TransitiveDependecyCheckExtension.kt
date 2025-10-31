package com.jakala.transitivedependencycheck.extension

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

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

    /**
     * Transitive dependency upgrade check exclusion. Exclude via regex.
     * Default: empty
     */
    val transitiveUpgradeExclusion: SetProperty<String>

    /**
     * Dependency version mismatch exclusion. Exclude via regex.
     * Default: empty
     */
    val versionMismatchExclusion: SetProperty<String>
}

internal abstract class MutableTransitiveDependecyCheckExtension : TransitiveDependecyCheckExtension {
    init {
        transitiveUpgradeCheckViolationAction.convention(CheckViolationAction.FAIL)
        versionMismatchCheckViolationAction.convention(CheckViolationAction.FAIL)
        transitiveUpgradeExclusion.convention(emptySet())
        versionMismatchExclusion.convention(emptySet())
    }
}
