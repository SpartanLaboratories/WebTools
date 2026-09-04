package com.spartanlabs.testing.uat.webtools

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import kotlin.test.Test

/**
 * Level 5 - manual user-acceptance evaluation for the Issue #1 Tier 1 handshake
 * change. These tests are executable placeholders for a human operator; the full
 * procedure - setup, steps, and pass/fail criteria - is in
 * `docs/issue-1-tier-1-uat.md`. They are `@Disabled` because each needs two hosts
 * on different networks and cannot run in CI.
 */
@Tag("uat")
class MultiConnectionUDPServerUatTest {

    @Test
    @Disabled("Manual: two hosts on different networks. See docs/issue-1-tier-1-uat.md section 1.")
    fun `a NAT'd client completes the handshake against a public server`() {
        // 1. Run a MultiConnectionUDPServer subclass on a host with a public, routable IP.
        // 2. From a machine behind a home router (private 192.168.x / 10.x address), send
        //    "Iam uatclient" to <publicIP>:9998 from a single UDP socket.
        // PASS: that same socket receives "TXRXON <send> <receive>" within 2 seconds.
        // FAIL: timeout - this is the pre-Tier-1 behaviour.
    }

    @Test
    @Disabled("Manual: documents the accepted Tier 1 limitation. See docs/issue-1-tier-1-uat.md section 2.")
    fun `the dedicated data channel is known not to traverse NAT yet`() {
        // After TXRXON, have the NAT'd client actuate its dedicated ports and exchange a
        // message with the server.
        // EXPECTED: this FAILS until Tier 2 (SpartanLaboratories/WebTools#1). Recorded here
        // so the limitation is visible in the test tree, not just the docs.
    }
}
