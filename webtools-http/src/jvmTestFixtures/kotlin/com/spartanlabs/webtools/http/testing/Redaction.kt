package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpSyntax
import com.spartanlabs.webtools.http.encodeQueryComponent

/**
 * What a [FixtureRecorder] hides: sensitive query names (by token, see the recorder's KDoc) less the
 * [keepQueryParams], plus the [extraSensitiveQueryParams]; and caller-supplied [secrets], each replaced by
 * [placeholder] wherever it appears in the whole serialised file (comment and body included) as raw text, as
 * `addQueryParameter` encodes it and as `java.net.URLEncoder` encodes it. After the replacement the file is
 * searched again for those forms, their lower-case-hex variants and the full percent-escape of the secret's
 * bytes; a file in which one survives is refused. That search is a backstop over the exact forms, not a decoder
 * of transformed ones: a secret echoed as base64, hashed, truncated, or percent-escaped only in part (e.g.
 * `SECRET%2dCANARY` for `SECRET-CANARY`) is neither replaced nor detected.
 *
 * **Stable Core, source compatibility only.** Immutable and safe to share. Equality is identity. `toString()`
 * prints the three list sizes and never a name or a secret; the `require` messages never hold a secret.
 *
 * @param keepQueryParams query names that are never redacted even when their tokens look sensitive (for example
 *   `sort_key`); compared ASCII case-insensitively.
 * @param extraSensitiveQueryParams query names redacted in addition to the token rule; compared ASCII case-insensitively.
 * @param secrets exact values to replace wherever they appear; each at least [MIN_SECRET_LENGTH] characters and
 *   well-formed Unicode.
 * @param placeholder the replacement text; [DEFAULT_PLACEHOLDER] by default.
 * @throws IllegalArgumentException if [placeholder] is empty or holds CR, LF or NUL; if a secret is shorter than
 *   [MIN_SECRET_LENGTH] or holds an unpaired surrogate; or if a secret contains or is contained in [placeholder]
 *   (every write would then be refused).
 */
public class Redaction(
    keepQueryParams: Set<String> = emptySet(),
    extraSensitiveQueryParams: Set<String> = emptySet(),
    secrets: List<String> = emptyList(),
    /** The text that replaces a redacted value. */
    public val placeholder: String = DEFAULT_PLACEHOLDER,
) {
    /** The keep list, ASCII-lower-cased. */
    internal val keep: Set<String> = keepQueryParams.mapTo(LinkedHashSet(), ::asciiLowercase)

    /** The extra sensitive names, ASCII-lower-cased. */
    internal val extra: Set<String> = extraSensitiveQueryParams.mapTo(LinkedHashSet(), ::asciiLowercase)
    private val secretValues: List<String> = secrets.toList()

    init {
        require(placeholder.isNotEmpty() && HttpSyntax.isFieldValue(placeholder)) { "the placeholder must be non-empty and hold no CR, LF or NUL" }
        require(secretValues.all { it.length >= MIN_SECRET_LENGTH }) { "every secret must have at least $MIN_SECRET_LENGTH characters" }
        require(secretValues.all { encodeQueryComponent(it).isSuccess }) { "every secret must be well-formed Unicode" }
        require(secretValues.none { placeholder.contains(it) || it.contains(placeholder) }) { "a secret must not contain the placeholder or be contained in it" }
    }

    /** The scrubber over the secrets and the placeholder, applied by the recorder to the whole serialised file. */
    internal val scrubber: SecretScrubber = SecretScrubber(secretValues, placeholder)

    /** Whether the decoded query name [decodedName] is redacted: the token rule or [extra], unless in [keep]. */
    internal fun isSensitiveQuery(decodedName: String): Boolean = SensitiveNames.isSensitiveQuery(decodedName, extra, keep)

    override fun toString(): String = "Redaction(keep=${keep.size}, extra=${extra.size}, secrets=${secretValues.size})"

    /** The defaults. */
    public companion object {
        /** The default [placeholder], `REDACTED`. */
        public const val DEFAULT_PLACEHOLDER: String = "REDACTED"

        /** The shortest secret accepted, 6 characters: a shorter one would match ordinary text. */
        public const val MIN_SECRET_LENGTH: Int = 6
    }
}
