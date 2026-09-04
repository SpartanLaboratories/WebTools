package com.spartanlabs.testing.uat.webtools

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Tag
import kotlin.test.Test

/**
 * Level 5 - manual user-acceptance evaluation for the Issue #1 NAT-traversal work.
 * These tests are executable placeholders for a human operator; the full
 * procedures are in `docs/issue-1-tier-1-uat.md` (handshake, Tier 1) and
 * `docs/issue-1-tier-2-uat.md` (data path, Tier 2). They are `@Disabled` because
 * each needs two hosts on different networks and cannot run in CI.
 */
@Tag("uat")
class MultiConnectionUDPServerUatTest {

    @Test
    @Disabled("Manual: two hosts on different networks. See docs/issue-1-tier-2-uat.md section 1.")
    fun `a NAT'd client completes the handshake against a public server`() {
        // 1. Run a MultiConnectionUDPServer subclass on a host with a public, routable IP.
        // 2. From a machine behind a home router, send "Iam uatclient" to <publicIP>:9998
        //    from a single UDP socket.
        // PASS: that same socket receives the single token "REGISTERED" within 2 seconds.
        // FAIL: timeout - this is the pre-Tier-1 behaviour.
    }

    @Test
    @Disabled("Manual: the Tier 2 deliverable. See docs/issue-1-tier-2-uat.md section 2.")
    fun `a NAT'd client exchanges data both ways over the single multiplexed port`() {
        // After REGISTERED, from the same socket that sent "Iam", the client sends
        // "hello-from-C" to <publicIP>:9998. The server's handler logs it and pushes
        // "hello-from-S" back.
        // PASS: the client receives "hello-from-S" on that socket within 2 seconds.
        // FAIL / partial: see the table in docs/issue-1-tier-2-uat.md section 2.
    }

    @Test
    @Disabled("Manual: multi-minute mapping longevity. See docs/issue-1-tier-2-uat.md section 4.")
    fun `the NAT mapping survives a multi-minute session driven by keepalives`() {
        // Run a 5-minute session: the server calls Connection.keepAlive() on a ~20 s
        // schedule and the client sends its own "KA" every ~20 s of idle time.
        // PASS: data still flows both ways at the end of the session.
    }
}
