package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.extension.CheckViolationAction
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger

internal object ViolationReporter {
    fun report(
        declaredMismatches: List<String>,
        resolvedMismatches: List<String>,
        versionMismatchAction: CheckViolationAction,
        transitiveUpgradeAction: CheckViolationAction,
        declaredHeader: String,
        resolvedHeader: String,
        tag: String,
        logger: Logger,
    ) {
        when {
            declaredMismatches.isNotEmpty() ->
                react(versionMismatchAction, message(declaredHeader, declaredMismatches), tag, logger)
            resolvedMismatches.isNotEmpty() ->
                react(transitiveUpgradeAction, message(resolvedHeader, resolvedMismatches), tag, logger)
            else -> logger.info("[$tag] All declared dependency versions look fine.")
        }
    }

    private fun message(header: String, items: List<String>): String =
        buildString {
            appendLine(header)
            items.forEach { appendLine(it) }
        }.trim()

    private fun react(
        action: CheckViolationAction,
        message: String,
        tag: String,
        logger: Logger,
    ) {
        when (action) {
            CheckViolationAction.FAIL -> throw GradleException(message)
            CheckViolationAction.WARN -> logger.warn("[$tag] $message")
            CheckViolationAction.IGNORE -> Unit
        }
    }
}
