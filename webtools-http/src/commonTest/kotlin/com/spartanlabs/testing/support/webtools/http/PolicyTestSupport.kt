package com.spartanlabs.testing.support.webtools.http

import com.spartanlabs.webtools.http.CidrBlock
import com.spartanlabs.webtools.http.GuardRefusal
import com.spartanlabs.webtools.http.GuardRelaxation
import com.spartanlabs.webtools.http.OutboundUrlPolicy
import com.spartanlabs.webtools.http.StrictIp
import com.spartanlabs.webtools.http.Url
import com.spartanlabs.webtools.http.UrlGuard

/**
 * Parses [text] or fails the test; for URLs the test itself wrote.
 *
 * @param text the URL text.
 * @return the parsed URL.
 * @throws IllegalStateException when `Url.parse` refuses [text] (a mistake in the test's own data).
 */
internal fun url(text: String): Url = Url.parse(text).getOrElse { error("test URL rejected: $text") }

/**
 * Address bytes through the strict parsers: dotted decimal, or IPv6 when [text] holds `:`.
 *
 * @param text a strict dotted quad or a strict IPv6 literal without brackets.
 * @return 4 or 16 bytes.
 * @throws IllegalStateException when neither parser accepts [text] (a mistake in the test's own data).
 */
internal fun addr(text: String): ByteArray =
    (if (':' in text) StrictIp.parseV6(text) else StrictIp.parseV4(text)) ?: error("test address rejected: $text")

/**
 * A block the test itself wrote.
 *
 * @param text the block in CIDR notation.
 * @return the parsed block.
 * @throws IllegalStateException when `CidrBlock.parse` refuses [text] (a mistake in the test's own data).
 */
internal fun cidr(text: String): CidrBlock = CidrBlock.parse(text).getOrElse { error("test block rejected: $text") }

/**
 * A guard over an `Untrusted` policy built from the defaults and [configure].
 *
 * @param relaxation the loopback relaxation for tests; `None` by default.
 * @param configure the policy's changes from the defaults; none by default.
 * @return the guard.
 */
internal fun untrustedGuard(
    relaxation: GuardRelaxation = GuardRelaxation.None,
    configure: OutboundUrlPolicy.Untrusted.Builder.() -> Unit = {},
): UrlGuard = UrlGuard(OutboundUrlPolicy.untrusted(configure), relaxation)

/**
 * A guard over a `Trusted` policy built from the defaults and [configure], with no relaxation (`Trusted` ignores it).
 *
 * @param configure the policy's changes from the defaults; none by default.
 * @return the guard.
 */
internal fun trustedGuard(configure: OutboundUrlPolicy.Trusted.Builder.() -> Unit = {}): UrlGuard =
    UrlGuard(OutboundUrlPolicy.trusted(configure))

/**
 * The reason name of a refusal, or "allowed" for `null`: the form the tables compare.
 *
 * @return `reason.name` (`HostForm`, `DeniedAddress`, ...) or `allowed`.
 */
internal fun GuardRefusal?.outcome(): String = this?.reason?.name ?: "allowed"
