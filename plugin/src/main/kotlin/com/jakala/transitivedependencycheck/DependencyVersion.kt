package com.jakala.transitivedependencycheck

@JvmInline
internal value class DependencyVersion(val value: String) {
    override fun toString(): String = value
}
