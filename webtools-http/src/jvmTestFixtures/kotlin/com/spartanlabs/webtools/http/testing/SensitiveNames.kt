package com.spartanlabs.webtools.http.testing

import com.spartanlabs.webtools.http.HttpSyntax

/** `A-Z` to `a-z` and nothing else: never the Kelvin sign or the dotless `i`, which `lowercase()` would fold. */
internal fun asciiLowercase(text: String): String = text.map(HttpSyntax::asciiLower).joinToString("")

/**
 * Decides whether a query-parameter or header name carries a credential, by its tokens: the name is split
 * on non-alphanumerics, on camelCase and acronym boundaries and between letters and digits, ASCII-lower-cased
 * (`apiKey` -> `api`, `key`; `APIKey` -> `api`, `key`; `X-Amz-Signature` -> `x`, `amz`, `signature`; `key2` ->
 * `key`, `2`). A name is sensitive when a token is in [TOKENS] or the tokens joined are in [COMPOUNDS]. `code`
 * (the OAuth parameter), `keyword`, `monkey`, `signal` and `design` are not sensitive; `sort_key` is, until a
 * keep list restores it.
 */
internal object SensitiveNames {
    private val TOKENS = setOf(
        "key", "apikey", "token", "secret", "password", "passwd", "pwd", "auth", "authorization",
        "sig", "signature", "session", "sessionid", "credential", "credentials", "jwt", "bearer",
    )
    private val COMPOUNDS = setOf(
        "accesstoken", "accesskey", "apitoken", "authtoken", "clientsecret", "idtoken",
        "privatekey", "refreshtoken", "secretkey", "sessiontoken",
    )

    /** The ASCII-lower-cased tokens of [name]; a character that is not an ASCII letter or digit is a separator. */
    fun tokens(name: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                result.add(asciiLowercase(current.toString()))
                current.setLength(0)
            }
        }
        for (i in name.indices) {
            val c = name[i]
            if (!isAlnum(c)) {
                flush()
                continue
            }
            // current is non-empty only when the previous character was alphanumeric.
            if (current.isNotEmpty() && boundaryBefore(name, i)) flush()
            current.append(c)
        }
        flush()
        return result
    }

    /** True when a token of [name] is a credential word or the whole name (joined) is a credential compound. */
    fun isSensitiveName(name: String): Boolean {
        val tokens = tokens(name)
        return tokens.any { it in TOKENS } || tokens.joinToString("") in COMPOUNDS
    }

    /** [isSensitiveName] or a member of [extra], unless a member of [keep]; both lists ASCII-lower-cased. */
    fun isSensitiveQuery(decodedName: String, extra: Set<String>, keep: Set<String>): Boolean {
        val lower = asciiLowercase(decodedName)
        return lower !in keep && (isSensitiveName(decodedName) || lower in extra)
    }

    // lower->upper, letter<->digit, or an acronym end (previous upper, this upper, next lower).
    private fun boundaryBefore(name: String, i: Int): Boolean {
        val prev = name[i - 1]
        val c = name[i]
        val next = name.getOrNull(i + 1)
        return (isLower(prev) && isUpper(c)) ||
            (isLetter(prev) && isDigit(c)) ||
            (isDigit(prev) && isLetter(c)) ||
            (isUpper(prev) && isUpper(c) && next != null && isLower(next))
    }

    private fun isUpper(c: Char): Boolean = c in 'A'..'Z'
    private fun isLower(c: Char): Boolean = c in 'a'..'z'
    private fun isDigit(c: Char): Boolean = c in '0'..'9'
    private fun isLetter(c: Char): Boolean = isUpper(c) || isLower(c)
    private fun isAlnum(c: Char): Boolean = isLetter(c) || isDigit(c)
}
