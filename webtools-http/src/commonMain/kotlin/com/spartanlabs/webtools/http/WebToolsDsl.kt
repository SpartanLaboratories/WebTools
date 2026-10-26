package com.spartanlabs.webtools.http

/**
 * Marks the configuration builders of `webtools-http` as one DSL. Inside a lambda whose receiver is a builder carrying this
 * marker, only the innermost such builder is an implicit receiver, so a nested block cannot read or set an outer builder's
 * member by accident: in `OutboundUrlPolicy.untrusted { settings { allowedPorts = null } }` the assignment does not compile,
 * because `allowedPorts` belongs to the outer builder. The outer builder stays reachable through its label:
 * `this@untrusted.allowedPorts = null`.
 *
 * **Stable Core.** The marker is never removed, and a builder that carries it keeps it. The module applies it to every
 * builder whose instance it passes to a consumer's lambda as the receiver: [HttpHeaders.Builder], [HttpRequest.Builder],
 * [HttpResponse.Builder], [TransportSettings.Builder], [OutboundUrlPolicy.Trusted.Builder] and
 * [OutboundUrlPolicy.Untrusted.Builder].
 */
@DslMarker
@Target(AnnotationTarget.CLASS)
// Retention is left at its default, RUNTIME: a test finds the marker on each builder by reflection (the ABI dump does
// not record a class's annotations), and Kotlin/JS warns about a RUNTIME retention only when it is written out.
@MustBeDocumented
public annotation class WebToolsDsl
