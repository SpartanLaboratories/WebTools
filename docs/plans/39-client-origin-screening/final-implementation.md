# Issue #39 — origin screening on every client receive branch: as built

## Deviations

- **The new tests assert that the listener logged its drops.** In
  `MultiConnectionUDPClientOriginScreenTest`, the ping, pong, `0x90`, `0xA1` and unframed tests each
  assert, after their own negative assertion, that exactly one `Datagram from unexpected origin` DEBUG
  line was logged; `ClientOriginScreenGatingTest` asserts, after the five assertions §4 lists, that
  seven were logged. §4 ("Regression tests") has these tests poll for that count without asserting it,
  except the keepalive test. Without the assertion, a run whose listener never handled the foreign
  datagram would pass the negative assertions vacuously, and the `0xA1` test would not fail against
  the unfixed code as §4's table says it does. The count is asserted after each test's own negative
  assertion, so every other test still fails first on the assertion the table names.
- **Four more off-origin component tests, and a reserved tag in the gating smoke.**
  `MultiConnectionUDPClientOriginScreenTest` also covers a reserved-tag datagram (`0xA2`), which is
  dropped at the origin screen with no `Unhandled datagram type` line; a malformed `0x81` too short
  for a sequence, which draws no `Malformed 0x81` WARN; a malformed one-byte `0x90`, which draws no
  `Malformed 0x90` WARN; and an empty datagram, which draws no WARN. Each sends from `peer` afterwards
  and asserts the server's datagram still draws its usual line. `ClientOriginScreenGatingTest` sends
  `0xA2` as an eighth foreign datagram and waits for eight drops. §4 ("Regression tests") lists six
  component cases and seven gating datagrams; these cover the remaining arms of the listener's
  switch, the reserved-tag and malformed-frame lines included, that the origin screen now precedes.
- **The existing foreign-`0xA0` reliable test waits on the drop line.** In
  `MultiConnectionUDPClientReliableTest`, `an 0xA0 from a foreign origin mints no engine and delivers
  nothing - only the server endpoint may` replaces its fixed 300 ms sleep with a DEBUG capture that
  polls for the `Datagram from unexpected origin` line and asserts exactly one, ahead of its negative
  assertions. §4 leaves this test as it was; with the sleep, its negative assertions did not prove
  the listener had handled the datagram.
- **The protocol document's versioning table gains a row.** §9 of `docs/webtools-udp-protocol.md`
  records that a client before `2.0.1a` screens the source on the `0xA0`/`0xA1` branch alone. §3 of
  the plan lists no §9 edit; the table is where the document records how an earlier release behaves
  differently.
