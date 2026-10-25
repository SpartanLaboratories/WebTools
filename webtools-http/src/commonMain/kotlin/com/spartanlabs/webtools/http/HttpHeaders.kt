package com.spartanlabs.webtools.http

/**
 * An immutable, ordered list of header entries. Names are RFC 9110 tokens and values obey the container
 * rule (after trimming SP and HTAB at the ends, no CR, LF or NUL); both are checked by the [Builder].
 * Lookup folds ASCII letters only.
 *
 * **Stable Core.** Built with the factory `HttpHeaders { add(name, value) }`. Equality is on the ordered
 * entries (name spelling, value and sensitivity). `toString()` prints the names in order, with `*` after
 * each sensitive entry, and never a value. A transport that receives entries this type cannot hold drops
 * an entry whose name is not a token, replaces CR, LF and NUL in a value with SP, and keeps everything
 * else in order.
 */
public class HttpHeaders internal constructor(entries: List<Entry>) {
    private val items: List<Entry> = entries.toList()

    /** The number of entries. */
    public val size: Int get() = items.size

    /**
     * Whether there is no entry.
     *
     * @return true when [size] is 0.
     */
    public fun isEmpty(): Boolean = items.isEmpty()

    /**
     * Returns the last value of [name], compared with an ASCII-only fold. Never throws, whatever the name.
     *
     * @param name the header name.
     * @return the last value of [name], or `null` when absent.
     */
    public operator fun get(name: String): String? = items.lastOrNull { HttpSyntax.equalsName(it.name, name) }?.value

    /**
     * Returns every value of [name], compared with an ASCII-only fold. Never throws, whatever the name.
     *
     * @param name the header name.
     * @return the values in insertion order; empty when absent.
     */
    public fun getAll(name: String): List<String> = items.filter { HttpSyntax.equalsName(it.name, name) }.map { it.value }

    /**
     * Whether there is an entry named [name], compared with an ASCII-only fold.
     *
     * @param name the header name.
     * @return true when at least one entry has that name.
     */
    public operator fun contains(name: String): Boolean = items.any { HttpSyntax.equalsName(it.name, name) }

    /**
     * Returns the entries.
     *
     * @return a copy of the entries, in insertion order.
     */
    public fun entries(): List<Entry> = items.toList()

    override fun equals(other: Any?): Boolean = other is HttpHeaders && other.items == items
    override fun hashCode(): Int = items.hashCode()
    override fun toString(): String =
        items.joinToString(separator = ", ", prefix = "HttpHeaders(", postfix = ")") { it.name + if (it.isSensitive) "*" else "" }

    /**
     * One header line: its name (spelling kept), its value (trimmed of SP and HTAB at the ends) and
     * whether it is sensitive.
     *
     * **Stable Core.** Equality is on the name spelling, the value and [isSensitive]. `toString()` is
     * `Entry(<name>)` or `Entry(<name>, sensitive)`, never the value.
     */
    public class Entry internal constructor(
        /** The header name, as it was added. */
        public val name: String,
        /** The header value, trimmed of SP and HTAB at the ends. */
        public val value: String,
        internal val flagged: Boolean,
    ) {
        /** True when the entry was added as sensitive or its name is a credential header name. */
        public val isSensitive: Boolean get() = flagged || HttpSyntax.isCredentialName(name)

        override fun equals(other: Any?): Boolean =
            other is Entry && other.name == name && other.value == value && other.isSensitive == isSensitive
        override fun hashCode(): Int = (name.hashCode() * 31 + value.hashCode()) * 31 + isSensitive.hashCode()
        override fun toString(): String = if (isSensitive) "Entry($name, sensitive)" else "Entry($name)"
    }

    /**
     * Collects entries for an [HttpHeaders]. Not thread-safe; use it only inside the factory's lambda.
     *
     * **Stable Core.** Each adding call checks, in this order, that the name is not empty, that the name
     * is an RFC 9110 token and that the value obeys the container rule; a failing call throws and changes
     * nothing. A name is echoed in a message only after it passed the token rule; a value never.
     */
    public class Builder internal constructor() {
        private val items = ArrayList<Entry>()

        /**
         * Appends an entry.
         *
         * @param name the header name, an RFC 9110 token.
         * @param value the value; stored trimmed of SP and HTAB at the ends.
         * @throws IllegalArgumentException if [name] is empty or not a token, or [value] contains CR, LF or NUL.
         */
        public fun add(name: String, value: String) {
            items.add(entry(name, value, flagged = false))
        }

        /**
         * Appends an entry marked sensitive: it is never printed, and the stack's redirect policy (a later
         * unit) reads the flag.
         *
         * @param name the header name, an RFC 9110 token.
         * @param value the value; stored trimmed of SP and HTAB at the ends.
         * @throws IllegalArgumentException if [name] is empty or not a token, or [value] contains CR, LF or NUL.
         */
        public fun addSensitive(name: String, value: String) {
            items.add(entry(name, value, flagged = true))
        }

        /**
         * Replaces every entry named [name] with one entry appended at the end, spelled as [name] and
         * flagged sensitive if any removed entry was.
         *
         * @param name the header name, an RFC 9110 token.
         * @param value the value; stored trimmed of SP and HTAB at the ends.
         * @throws IllegalArgumentException if [name] is empty or not a token, or [value] contains CR, LF or NUL.
         */
        public fun set(name: String, value: String) {
            replace(name, value, flagged = items.any { it.flagged && HttpSyntax.equalsName(it.name, name) })
        }

        /**
         * Replaces every entry named [name] with one sensitive entry appended at the end.
         *
         * @param name the header name, an RFC 9110 token.
         * @param value the value; stored trimmed of SP and HTAB at the ends.
         * @throws IllegalArgumentException if [name] is empty or not a token, or [value] contains CR, LF or NUL.
         */
        public fun setSensitive(name: String, value: String) {
            replace(name, value, flagged = true)
        }

        /**
         * Removes every entry named [name]; a no-op when there is none.
         *
         * @param name the header name, compared with an ASCII-only fold.
         */
        public fun remove(name: String) {
            items.removeAll { HttpSyntax.equalsName(it.name, name) }
        }

        /**
         * Appends every entry of [headers], with its sensitivity flag.
         *
         * @param headers the entries to append.
         */
        public fun addAll(headers: HttpHeaders) {
            items.addAll(headers.entries())
        }

        internal fun build(): HttpHeaders = HttpHeaders(items)

        // Validates before it mutates, so a failing call changes nothing.
        private fun replace(name: String, value: String, flagged: Boolean) {
            val entry = entry(name, value, flagged)
            items.removeAll { HttpSyntax.equalsName(it.name, name) }
            items.add(entry)
        }

        private fun entry(name: String, value: String, flagged: Boolean): Entry {
            require(name.isNotEmpty()) { "Header name must not be empty" }
            val bad = HttpSyntax.firstNonTokenIndex(name)
            require(bad < 0) { "Header name is not an RFC 9110 token (first offending character at index $bad)" }
            val trimmed = HttpSyntax.trimOws(value)
            require(HttpSyntax.isFieldValue(trimmed)) { "Value of header '$name' must not contain CR, LF or NUL" }
            return Entry(name, trimmed, flagged)
        }
    }
}

/**
 * Builds an [HttpHeaders] from the calls made on the [HttpHeaders.Builder] in [configure].
 *
 * **Stable Core.**
 *
 * @param configure the builder calls; empty by default, for an empty [HttpHeaders].
 * @return the immutable headers.
 * @throws IllegalArgumentException from a builder call whose name is empty or not a token, or whose
 *   value contains CR, LF or NUL.
 */
public fun HttpHeaders(configure: HttpHeaders.Builder.() -> Unit = {}): HttpHeaders =
    HttpHeaders.Builder().apply(configure).build()
