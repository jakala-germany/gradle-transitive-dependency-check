package com.jakala.transitivedependencycheck

@JvmInline
internal value class DependencyGroupName(val value: String) {
    override fun toString(): String = value
}
