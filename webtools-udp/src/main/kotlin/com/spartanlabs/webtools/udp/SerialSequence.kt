package com.spartanlabs.webtools.udp

/**
 * RFC 1982 serial-number arithmetic over the 16-bit space `0..65535` that the
 * reliable engine's `seq`/`ack` fields live in (design doc D4). Every reliable
 * window this module uses is at most a few hundred entries, far under the
 * `2^15` antipodal boundary where RFC 1982 comparison is formally undefined —
 * so the antipodal case below is resolved deterministically (for a total
 * function) but is not something any caller here can actually reach. Pure,
 * stateless.
 */
internal object SerialSequence {
    /** The size of the sequence space: `2^16`. */
    private const val SPACE: Int = 1 shl 16

    /** Half the sequence space — the RFC 1982 antipodal boundary. */
    private const val HALF_SPACE: Int = SPACE / 2

    /**
     * Normalizes [value] into the `0..65535` window, wrapping as many times as
     * needed in either direction.
     * @param value any integer, positive or negative
     * @return [value] mod `65536`, in `0..65535`
     */
    fun wrap(value: Int): Int = ((value % SPACE) + SPACE) % SPACE

    /**
     * [a] shifted by [n] (positive or negative) with wraparound, per RFC 1982
     * §3.1's addition.
     * @param a a sequence number in `0..65535`
     * @param n the signed increment to apply
     * @return `(a + n) mod 65536`, in `0..65535`
     */
    fun add(a: Int, n: Int): Int = wrap(a + n)

    /**
     * RFC 1982 §3.2 serial-number comparison: negative if [a] is "less than"
     * [b], zero if equal, positive if "greater than" — signed, not merely
     * numeric, so it wraps correctly across the `65535 -> 0` boundary. At the
     * antipodal distance (`|a - b| == HALF_SPACE`), RFC 1982 leaves the
     * ordering undefined; this resolves it to a fixed, arbitrary sign rather
     * than throwing, since every window this module uses is far below that
     * distance and never exercises this branch in practice.
     * @param a the left-hand sequence number, `0..65535`
     * @param b the right-hand sequence number, `0..65535`
     * @return `< 0` if `a` precedes `b`, `0` if equal, `> 0` if `a` follows `b`
     */
    fun compare(a: Int, b: Int): Int {
        if (a == b) return 0
        // RFC 1982 §3.2: i1 < i2 iff (i1 < i2 and i2 - i1 < 2^(SERIAL_BITS-1)) or
        // (i1 > i2 and i1 - i2 > 2^(SERIAL_BITS-1)), computed here via the wrapped delta.
        val delta = wrap(a - b) // in 0..65535
        return if (delta < HALF_SPACE) delta else delta - SPACE
    }

    /**
     * True if [a] strictly precedes [b] in RFC 1982 serial order.
     * @param a the left-hand sequence number, `0..65535`
     * @param b the right-hand sequence number, `0..65535`
     * @return whether `a` precedes `b`
     */
    fun lessThan(a: Int, b: Int): Boolean = compare(a, b) < 0

    /**
     * True if [seq] falls in the half-open window `[base, base + windowSize)`,
     * wrapping correctly across the `65535 -> 0` boundary.
     * @param seq the sequence number to test, `0..65535`
     * @param base the inclusive lower bound of the window, `0..65535`
     * @param windowSize the window's width; `<= 0` never matches
     * @return whether `seq` lies within the window
     */
    fun inWindow(seq: Int, base: Int, windowSize: Int): Boolean {
        if (windowSize <= 0) return false
        val offset = wrap(seq - base)
        return offset < windowSize
    }
}
