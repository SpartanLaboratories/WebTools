package com.spartanlabs.webtools.http

/**
 * Identifies one cached answer: an endpoint id and the endpoint's parameters, and nothing else.
 *
 * **Stable Core.** Equality is on [endpointId] and [params] only; the type parameter [V] is a phantom, so one endpoint id serves one value type and a second type
 * under the same id is a programming error (the consumer's use of the value fails with a `ClassCastException`). Names, values and the id are case-sensitive and
 * compared as UTF-16 code units; there is no Unicode normalisation. **Never put a tenant, a header, a credential or the decoder in a key**: the key is an
 * equality key and is printed (names only) in logs. `toString()` prints the id and the parameter **names**, never a value.
 *
 * @param V the value type this key's endpoint returns (not part of equality).
 * @param endpointId the endpoint's stable id; not blank.
 * @param params the endpoint's parameters as name/value pairs, in any order; copied and sorted by name with a stable sort, so repeated names keep the caller's order.
 * @throws IllegalArgumentException if [endpointId] is blank.
 */
public class CacheKey<V : Any>(
    /** The endpoint's stable id. */
    public val endpointId: String,
    params: List<Pair<String, String>>,
) {
    /**
     * A key with no parameters.
     *
     * @param endpointId the endpoint's stable id; not blank.
     * @throws IllegalArgumentException if [endpointId] is blank.
     */
    public constructor(endpointId: String) : this(endpointId, emptyList())

    /** The parameters, sorted by name (stable: repeated names keep the caller's order). A read-only copy. */
    public val params: List<Pair<String, String>> = params.sortedBy { it.first }

    private val hash: Int

    init {
        require(endpointId.isNotBlank()) { "endpointId must not be blank" }
        hash = 31 * endpointId.hashCode() + this.params.hashCode()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is CacheKey<*> && other.hash == hash && other.endpointId == endpointId && other.params == params)

    override fun hashCode(): Int = hash

    // Names only, never a value: the key is logged.
    override fun toString(): String = "CacheKey(endpointId=$endpointId, params=[${params.joinToString(", ") { it.first }}])"
}
