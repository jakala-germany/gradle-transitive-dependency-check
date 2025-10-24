package com.jakala.transitive.dependency.check

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project

// Workaround for using version catalog in Kotlin script convention plugins
// https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
internal val Project.libs get() = project.extensions.getByName("libs") as LibrariesForLibs
