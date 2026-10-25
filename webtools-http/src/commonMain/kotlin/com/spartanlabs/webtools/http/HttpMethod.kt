package com.spartanlabs.webtools.http

/**
 * An HTTP request method: an RFC 9110 token, compared case-sensitively.
 *
 * **Stable Core.** The seven constants in the companion cover the common methods; [of] returns them for
 * their exact names and a new instance for any other token. Equality is on [name]; `toString()` is the
 * name.
 *
 * @param name the method token.
 */
public class HttpMethod private constructor(
    /** The method token, for example `GET`. */
    public val name: String,
) {
    internal val permitsBody: Boolean get() = name != "GET" && name != "HEAD" && name != "TRACE"

    override fun equals(other: Any?): Boolean = other is HttpMethod && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    /** The common methods and the factory. */
    public companion object {
        /** `GET`. */
        public val Get: HttpMethod = HttpMethod("GET")

        /** `HEAD`. */
        public val Head: HttpMethod = HttpMethod("HEAD")

        /** `POST`. */
        public val Post: HttpMethod = HttpMethod("POST")

        /** `PUT`. */
        public val Put: HttpMethod = HttpMethod("PUT")

        /** `PATCH`. */
        public val Patch: HttpMethod = HttpMethod("PATCH")

        /** `DELETE`. */
        public val Delete: HttpMethod = HttpMethod("DELETE")

        /** `OPTIONS`. */
        public val Options: HttpMethod = HttpMethod("OPTIONS")

        private val known: Map<String, HttpMethod> = listOf(Get, Head, Post, Put, Patch, Delete, Options).associateBy { it.name }

        /**
         * Returns the method named [name], compared case-sensitively; `CONNECT` and `TRACE` are accepted
         * here and refused by [HttpRequest].
         *
         * @param name the method token.
         * @return the constant for one of the seven exact names, a new instance for any other RFC 9110
         *   token, or a failure holding an `IllegalArgumentException` with the constant message
         *   `Method is not an RFC 9110 token`, which never echoes [name].
         */
        public fun of(name: String): Result<HttpMethod> =
            if (HttpSyntax.isToken(name)) Result.success(known[name] ?: HttpMethod(name))
            else Result.failure(IllegalArgumentException("Method is not an RFC 9110 token"))
    }
}
