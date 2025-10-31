package com.jakala.transitivedependencycheck.model

@JvmInline
internal value class DependencyVersion(val value: String) {
    override fun toString(): String = value
}
