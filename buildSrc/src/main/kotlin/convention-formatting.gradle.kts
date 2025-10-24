import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn("ktlintCheck")
}

ktlint {
    version.set("1.7.1")
    outputColorName.set("RED")
    relative.set(true)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }

    filter {
        exclude("**/build/**")
    }
}
