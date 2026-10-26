package com.spartanlabs.webtools.http

/**
 * A relaxation of the address and name rules for tests. [None] changes nothing; [LoopbackForTests] exempts loopback only:
 * the host `localhost` from the reserved-name rule, the addresses `127.0.0.0/8` and `::1` (and a mapped form of a
 * `127.0.0.0/8` address) from the address table, and the port rule for the hosts `localhost`, `127.0.0.1` and `::1`; every
 * other rule, `extraDeniedRanges` and the policy's suffixes included, stays live. `internal` and reachable only from the test
 * fixtures and this module's tests: the main artifact has no way to switch the address guard off.
 */
internal class GuardRelaxation private constructor(val allowLoopback: Boolean) {
    companion object {
        /** No relaxation: every rule applies. */
        val None: GuardRelaxation = GuardRelaxation(false)

        /** Exempts exactly the host `localhost`, the loopback addresses and the port rule for the hosts `localhost`, `127.0.0.1` and `::1`. */
        val LoopbackForTests: GuardRelaxation = GuardRelaxation(true)
    }
}
