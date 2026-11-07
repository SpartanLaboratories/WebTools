package com.spartanlabs.webtools.feeds

/** The kind name a log line prints for a component: its simple class name, or `custom` for an anonymous one. */
internal fun kindOf(component: Any): String = component::class.simpleName ?: "custom"
