package com.jakala.transitivedependencycheck.model

@JvmInline
internal value class DependencyGroupName(val value: String) {
    override fun toString(): String = value
}
