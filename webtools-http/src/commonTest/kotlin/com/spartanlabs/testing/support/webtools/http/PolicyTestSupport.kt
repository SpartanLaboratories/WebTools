package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.GuardRefusal
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.StrictIp
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlGuard

/** Parses [text] or fails the test; for URLs the test itself wrote. */
internal fun url(text: String): Url = Url.parse(text).getOrElse { error("test URL rejected: $text") }

/** Address bytes through the strict parsers: dotted decimal, or IPv6 when [text] holds ':'. */
internal fun addr(text: String): ByteArray =
    (if (':' in text) StrictIp.parseV6(text) else StrictIp.parseV4(text)) ?: error("test address rejected: $text")

/** A block the test itself wrote. */
internal fun cidr(text: String): CidrBlock = CidrBlock.parse(text).getOrElse { error("test block rejected: $text") }

internal fun untrustedGuard(
    relaxation: GuardRelaxation = GuardRelaxation.None,
    configure: OutboundUrlPolicy.Untrusted.Builder.() -> Unit = {},
): UrlGuard = UrlGuard(OutboundUrlPolicy.untrusted(configure), relaxation)

internal fun trustedGuard(configure: OutboundUrlPolicy.Trusted.Builder.() -> Unit = {}): UrlGuard =
    UrlGuard(OutboundUrlPolicy.trusted(configure))

/** The reason name of a refusal, or "allowed" for `null`: the form the tables compare. */
internal fun GuardRefusal?.outcome(): String = this?.reason?.name ?: "allowed"
