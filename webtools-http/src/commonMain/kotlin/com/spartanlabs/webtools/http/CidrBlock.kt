package com.spartanlabs.webtools.http

// Constant messages, one per kind of problem; none echoes the text.
private const val SHAPE_MESSAGE = "A CIDR block must be written <address>/<prefix>."
private const val ADDRESS_MESSAGE = "The CIDR block address must be a plain dotted-decimal IPv4 or an IPv6 address."
private const val PREFIX_MESSAGE =
    "The CIDR block prefix length must be a decimal number within the address family's width, without a sign or a leading zero."
private const val HOST_BITS_MESSAGE = "The CIDR block has bits set beyond its prefix length."

/**
 * An address block in CIDR notation, for `Untrusted`'s `extraDeniedRanges`.
 *
 * **Stable Core.** Built with [parse]. Equality is on the family, the address bytes and the prefix length;
 * `toString()` is the canonical block (`10.0.0.0/8`, `fd00::/8`). A policy's own `toString()` prints only how many blocks
 * it holds. This class exposes nothing else in this release.
 */
public class CidrBlock private constructor(
    private val bytes: ByteArray,
    private val prefixLength: Int,
) {
    /** Whether this is an IPv4 block (4 address bytes) rather than an IPv6 one (16). */
    internal val isIpv4: Boolean get() = bytes.size == 4

    /**
     * Whether [address] is in this block: the same family (the same width) and the first `prefixLength` bits equal, judged
     * at the address's full width, so a prefix that is not taken from the input can never shorten the match.
     *
     * @param address 4 or 16 bytes; an address of the other family, or of any other length, is never contained.
     * @return `true` when the block covers [address].
     */
    internal fun contains(address: ByteArray): Boolean = address.size == bytes.size && matchesPrefix(bytes, address, prefixLength)

    override fun equals(other: Any?): Boolean = other is CidrBlock && other.prefixLength == prefixLength && other.bytes.contentEquals(bytes)
    override fun hashCode(): Int = bytes.contentHashCode() * 31 + prefixLength

    // IPv6 is printed by StrictIp.formatV6 (RFC 5952 hex groups; never a dotted IPv4 inside IPv6).
    override fun toString(): String =
        (if (isIpv4) bytes.joinToString(".") { (it.toInt() and 0xFF).toString() } else StrictIp.formatV6(bytes)) + "/" + prefixLength

    /** The parser. */
    public companion object {
        /**
         * Parses [text] as exactly `<address>/<prefix>`: the address strict dotted-decimal (four parts, no leading zero) or
         * strict IPv6 (no brackets, no zone); the prefix 0..32 or 0..128 in decimal without a sign or a leading zero (except
         * `0`). A block with a bit set after the prefix is refused, not masked: `10.0.0.5/8` is a failure.
         *
         * @param text the block, as an operator wrote it.
         * @return the block, or a failure holding an `IllegalArgumentException` with one constant message per kind of
         *   problem (shape, address, prefix, host bits); the message never echoes [text].
         */
        public fun parse(text: String): Result<CidrBlock> {
            val slash = text.indexOf('/')
            if (slash < 0 || text.indexOf('/', slash + 1) >= 0) return failure(SHAPE_MESSAGE)
            val addressText = text.substring(0, slash)
            val bytes = (if (':' in addressText) StrictIp.parseV6(addressText) else StrictIp.parseV4(addressText))
                ?: return failure(ADDRESS_MESSAGE)
            val prefix = parsePrefix(text.substring(slash + 1), bytes.size * 8) ?: return failure(PREFIX_MESSAGE)
            return if (hasBitsAfter(bytes, prefix)) failure(HOST_BITS_MESSAGE) else Result.success(CidrBlock(bytes, prefix))
        }

        private fun failure(message: String): Result<CidrBlock> = Result.failure(IllegalArgumentException(message))

        // 1..3 ASCII digits, no leading zero unless the text is "0", at most the family's width.
        private fun parsePrefix(text: String, width: Int): Int? {
            if (text.isEmpty() || text.length > 3 || !text.all { it in '0'..'9' } || (text.length > 1 && text[0] == '0')) return null
            return text.toInt().takeIf { it <= width }
        }

        // Bits are numbered MSB-first across the array, as CIDR counts them: bit n lives in byte n / 8 at shift 7 - n % 8.
        private fun hasBitsAfter(bytes: ByteArray, prefix: Int): Boolean {
            for (bit in prefix until bytes.size * 8) {
                if (((bytes[bit / 8].toInt() shr (7 - bit % 8)) and 1) != 0) return true
            }
            return false
        }
    }
}

// The first `prefix` bits of a and b are equal (a and b have the same length, at least prefix / 8 + 1 when prefix % 8 != 0).
private fun matchesPrefix(a: ByteArray, b: ByteArray, prefix: Int): Boolean {
    val whole = prefix / 8
    for (i in 0 until whole) if (a[i] != b[i]) return false
    val rest = prefix % 8
    if (rest == 0) return true
    // The top `rest` bits of the partial byte: 0xFF shifted left by the bits that do not count, cut back to one byte.
    val mask = (0xFF shl (8 - rest)) and 0xFF
    return (a[whole].toInt() and mask) == (b[whole].toInt() and mask)
}
