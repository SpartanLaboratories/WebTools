# Research: the IANA special-purpose rows and the `AddressRanges` deny table

## Question

Which rows do the IANA IPv4 and IPv6 Special-Purpose Address Registries hold today, and how does each
map onto the `AddressRanges` deny table of `webtools-http` (`internal`, `commonMain`, pure function over
4- or 16-byte addresses, also compiled for `js`)?

An answer has to give: every row with its first and last address; its treatment (IPv4 denied;
IPv6 inside `2000::/3` denied explicitly, outside it denied by default; `::ffff:0:0/96` and `64:ff9b::/96`
judged by the embedded IPv4; `2002::/16` and Teredo `2001::/32` denied wholesale); the two blocks that are
not registry rows (IPv4 multicast `224.0.0.0/4`, IPv6 global unicast `2000::/3`); public controls for the
Level 4a tests; and a log class per row. All reads were done on **2026-10-25**.

## Bottom line

- **Both registries are unchanged since the snapshot the parent research used.** IPv4 Special-Purpose
  Address Space and IPv6 Special-Purpose Address Space both read "Last Updated 2025-10-09" today. The IPv4
  registry holds 25 rows (26 prefixes: the `NAT64/DNS64 Discovery` row lists `192.0.0.170/32` and
  `192.0.0.171/32`); the IPv6 registry holds 25 rows. No row was added, removed or changed against the
  parent's "Deny table" and "Deny rules (IPv6)".
- **IPv4 collapses to 17 disjoint deny ranges.** The 26 prefixes plus `224.0.0.0/4` merge into 16 blocks
  below `224.0.0.0` plus a single `224.0.0.0/3` (multicast `224.0.0.0/4` and reserved `240.0.0.0/4` are
  adjacent). Nine registry prefixes are nested inside another (`0.0.0.0/32`, six `192.0.0.x`, `192.88.99.2/32`,
  `255.255.255.255/32`); the table may keep them as most-specific-first rows to carry their own log class.
- **IPv6 needs five explicit blocks inside `2000::/3`**: `2001::/23` (which holds Teredo, the anycasts,
  benchmarking, AMT, AS112-v6, ORCHID, ORCHIDv2, DETs), `2001:db8::/32`, `2002::/16`, `2620:4f:8000::/48`,
  `3fff::/20`. Ten registry rows sit outside `2000::/3` (`::1`, `::`, `::ffff:0:0/96`, `64:ff9b::/96`,
  `64:ff9b:1::/48`, `100::/64`, `100:0:0:1::/64`, `5f00::/16`, `fc00::/7`, `fe80::/10`) and need no entry:
  eight fall to the default-deny, two are judged by their embedded IPv4.
- **Every "Globally Reachable: True" row is a service anycast, a translation prefix or a hash identifier
  space, not a host serving web content, so denying them costs a content fetcher nothing.** One caveat: a
  consumer on an IPv6-only network with DNS64 reaches IPv4 through `64:ff9b::/96`; that is why the row is
  judged by its embedded IPv4 rather than denied.
- **`2000::/3` is still the only block IANA allocates global unicast from.** Nothing new has been
  designated outside it; RFC 9812 (October 2025) only raised the approval level for opening more. The
  precedent to keep in view is `5f00::/16` (SRv6 SIDs, RFC 9602), a special-purpose allocation carved out of
  reserved space outside `2000::/3`; the default-deny already covers that class of change.
- **Refresh by diffing the registries' CSV forms**, whose Address Block cells carry footnote markers
  (`192.0.0.0/24 [2]`, `2002::/16 [3]`) and one cell with two prefixes; a refresh script has to strip and
  split them. The refresh checklist is at the end of the Recommendation.

## Findings

### F1. IPv4 Special-Purpose Address Registry: 25 rows, last updated 2025-10-09

Evidence tier: **established** (IANA's registry, XHTML and CSV compared row for row; the XHTML's Source
column was not captured separately, the CSV's was). Source:
<https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry.xhtml> and
<https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry-1.csv>; read
2026-10-25; page header "Created 2009-08-19, Last Updated 2025-10-09". References: RFC 5736, RFC 6890
(with RFC Errata 6404), RFC 8190.

Footnotes: **[1]** (on `127.0.0.0/8`) "Several protocols have been granted exceptions to this rule. For
examples, see RFC 8029 and RFC 5884." **[2]** (on `192.0.0.0/24`) "Not useable unless by virtue of a more
specific reservation."

Columns: S = Source, D = Destination, F = Forwardable, G = Globally Reachable, R = Reserved-by-Protocol.
T = True, F = False. Termination Date is `N/A` everywhere except the `192.88.99.0/24` row (2015-03).
Allocation Date is `YYYY-MM`.

| # | Address block | Name | RFC | Alloc. | S | D | F | G | R | First | Last |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `0.0.0.0/8` | "This network" | RFC 791 s3.2 | 1981-09 | T | F | F | F | T | `0.0.0.0` | `0.255.255.255` |
| 2 | `0.0.0.0/32` | "This host on this network" | RFC 1122 s3.2.1.3 | 1981-09 | T | F | F | F | T | `0.0.0.0` | `0.0.0.0` |
| 3 | `10.0.0.0/8` | Private-Use | RFC 1918 | 1996-02 | T | T | T | F | F | `10.0.0.0` | `10.255.255.255` |
| 4 | `100.64.0.0/10` | Shared Address Space | RFC 6598 | 2012-04 | T | T | T | F | F | `100.64.0.0` | `100.127.255.255` |
| 5 | `127.0.0.0/8` | Loopback | RFC 1122 s3.2.1.3 | 1981-09 | F [1] | F [1] | F [1] | F [1] | T | `127.0.0.0` | `127.255.255.255` |
| 6 | `169.254.0.0/16` | Link Local | RFC 3927 | 2005-05 | T | T | F | F | T | `169.254.0.0` | `169.254.255.255` |
| 7 | `172.16.0.0/12` | Private-Use | RFC 1918 | 1996-02 | T | T | T | F | F | `172.16.0.0` | `172.31.255.255` |
| 8 | `192.0.0.0/24` [2] | IETF Protocol Assignments | RFC 6890 s2.1 | 2010-01 | F | F | F | F | F | `192.0.0.0` | `192.0.0.255` |
| 9 | `192.0.0.0/29` | IPv4 Service Continuity Prefix | RFC 7335 | 2011-06 | T | T | T | F | F | `192.0.0.0` | `192.0.0.7` |
| 10 | `192.0.0.8/32` | IPv4 dummy address | RFC 7600 | 2015-03 | T | F | F | F | F | `192.0.0.8` | `192.0.0.8` |
| 11 | `192.0.0.9/32` | Port Control Protocol Anycast | RFC 7723 | 2015-10 | T | T | T | **T** | F | `192.0.0.9` | `192.0.0.9` |
| 12 | `192.0.0.10/32` | Traversal Using Relays around NAT Anycast | RFC 8155 | 2017-02 | T | T | T | **T** | F | `192.0.0.10` | `192.0.0.10` |
| 13 | `192.0.0.170/32`, `192.0.0.171/32` | NAT64/DNS64 Discovery | RFC 8880; RFC 7050 s2.2 | 2013-02 | F | F | F | F | T | `192.0.0.170` / `192.0.0.171` | same |
| 14 | `192.0.2.0/24` | Documentation (TEST-NET-1) | RFC 5737 | 2010-01 | F | F | F | F | F | `192.0.2.0` | `192.0.2.255` |
| 15 | `192.31.196.0/24` | AS112-v4 | RFC 7535 | 2014-12 | T | T | T | **T** | F | `192.31.196.0` | `192.31.196.255` |
| 16 | `192.52.193.0/24` | AMT | RFC 7450 | 2014-12 | T | T | T | **T** | F | `192.52.193.0` | `192.52.193.255` |
| 17 | `192.88.99.0/24` | Deprecated (6to4 Relay Anycast) | RFC 7526 | 2001-06 | blank | blank | blank | blank | blank | `192.88.99.0` | `192.88.99.255` |
| 18 | `192.88.99.2/32` | 6a44-relay anycast address | RFC 6751 | 2012-10 | T | T | T | F | F | `192.88.99.2` | `192.88.99.2` |
| 19 | `192.168.0.0/16` | Private-Use | RFC 1918 | 1996-02 | T | T | T | F | F | `192.168.0.0` | `192.168.255.255` |
| 20 | `192.175.48.0/24` | Direct Delegation AS112 Service | RFC 7534 | 1996-01 | T | T | T | **T** | F | `192.175.48.0` | `192.175.48.255` |
| 21 | `198.18.0.0/15` | Benchmarking | RFC 2544 | 1999-03 | T | T | T | F | F | `198.18.0.0` | `198.19.255.255` |
| 22 | `198.51.100.0/24` | Documentation (TEST-NET-2) | RFC 5737 | 2010-01 | F | F | F | F | F | `198.51.100.0` | `198.51.100.255` |
| 23 | `203.0.113.0/24` | Documentation (TEST-NET-3) | RFC 5737 | 2010-01 | F | F | F | F | F | `203.0.113.0` | `203.0.113.255` |
| 24 | `240.0.0.0/4` | Reserved | RFC 1112 s4 | 1989-08 | F | F | F | F | T | `240.0.0.0` | `255.255.255.255` |
| 25 | `255.255.255.255/32` | Limited Broadcast | RFC 8190; RFC 919 s7 | 1984-10 | F | T | F | F | T | `255.255.255.255` | `255.255.255.255` |

Row 17 (`192.88.99.0/24`, terminated 2015-03) carries blank Source, Destination, Forwardable, Globally
Reachable and Reserved-by-Protocol cells: it is a deprecated row. It stays in the registry, so it stays in the
deny table.

### F2. IPv6 Special-Purpose Address Registry: 25 rows, last updated 2025-10-09

Evidence tier: **established**. Source:
<https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry.xhtml> and
<https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry-1.csv>; read
2026-10-25; "Created 2006-01-10, Last Updated 2025-10-09".

Footnotes: **[1]** (on `2001::/23`: Source, Destination, Forwardable, Globally Reachable) "Unless allowed by
a more specific allocation." **[2]** (Teredo, Globally Reachable `N/A`) "See Section 5 of RFC 4380 for
details." **[3]** (6to4, on the block and on Globally Reachable `N/A`) "See RFC 3056 for details."
**[4]** (Unique-Local, Globally Reachable) "See RFC 4193 for more details on the routability of Unique-Local
addresses. The Unique-Local prefix is drawn from the IPv6 Global Unicast Address range, but is specified as
not globally routed."

Same column key as F1. Termination Date is `N/A` except `2001:10::/28` (2014-03). First and last addresses
are given in full form and in RFC 5952 form (lower case, longest zero run of two or more groups compressed,
leftmost on a tie).

| # | Address block | Name | RFC | Alloc. | S | D | F | G | R |
|---|---|---|---|---|---|---|---|---|---|
| 1 | `::1/128` | Loopback Address | RFC 4291 | 2006-02 | F | F | F | F | T |
| 2 | `::/128` | Unspecified Address | RFC 4291 | 2006-02 | T | F | F | F | T |
| 3 | `::ffff:0:0/96` | IPv4-mapped Address | RFC 4291 | 2006-02 | F | F | F | F | T |
| 4 | `64:ff9b::/96` | IPv4-IPv6 Translat. | RFC 6052 | 2010-10 | T | T | T | **T** | F |
| 5 | `64:ff9b:1::/48` | IPv4-IPv6 Translat. | RFC 8215 | 2017-06 | T | T | T | F | F |
| 6 | `100::/64` | Discard-Only Address Block | RFC 6666 | 2012-06 | T | T | T | F | F |
| 7 | `100:0:0:1::/64` | Dummy IPv6 Prefix | RFC 9780 | 2025-04 | T | F | F | F | F |
| 8 | `2001::/23` | IETF Protocol Assignments | RFC 2928 | 2000-09 | F [1] | F [1] | F [1] | F [1] | F |
| 9 | `2001::/32` | TEREDO | RFC 4380; RFC 8190 | 2006-01 | T | T | T | N/A [2] | F |
| 10 | `2001:1::1/128` | Port Control Protocol Anycast | RFC 7723 | 2015-10 | T | T | T | **T** | F |
| 11 | `2001:1::2/128` | Traversal Using Relays around NAT Anycast | RFC 8155 | 2017-02 | T | T | T | **T** | F |
| 12 | `2001:1::3/128` | DNS-SD Service Registration Protocol Anycast | RFC 9665 | 2024-04 | T | T | T | **T** | F |
| 13 | `2001:2::/48` | Benchmarking | RFC 5180; RFC Errata 1752 | 2008-04 | T | T | T | F | F |
| 14 | `2001:3::/32` | AMT | RFC 7450 | 2014-12 | T | T | T | **T** | F |
| 15 | `2001:4:112::/48` | AS112-v6 | RFC 7535 | 2014-12 | T | T | T | **T** | F |
| 16 | `2001:10::/28` | Deprecated (previously ORCHID) | RFC 4843 | 2007-03 | blank | blank | blank | blank | blank |
| 17 | `2001:20::/28` | ORCHIDv2 | RFC 7343 | 2014-07 | T | T | T | **T** | F |
| 18 | `2001:30::/28` | Drone Remote ID Protocol Entity Tags (DETs) Prefix | RFC 9374 | 2022-12 | T | T | T | **T** | F |
| 19 | `2001:db8::/32` | Documentation | RFC 3849 | 2004-07 | F | F | F | F | F |
| 20 | `2002::/16` [3] | 6to4 | RFC 3056 | 2001-02 | T | T | T | N/A [3] | F |
| 21 | `2620:4f:8000::/48` | Direct Delegation AS112 Service | RFC 7534 | 2011-05 | T | T | T | **T** | F |
| 22 | `3fff::/20` | Documentation | RFC 9637 | 2024-07 | F | F | F | F | F |
| 23 | `5f00::/16` | Segment Routing (SRv6) SIDs | RFC 9602 | 2024-04 | T | T | T | F | F |
| 24 | `fc00::/7` | Unique-Local | RFC 4193; RFC 8190 | 2005-10 | T | T | T | F [4] | F |
| 25 | `fe80::/10` | Link-Local Unicast | RFC 4291 | 2006-02 | T | T | F | F | T |

First and last addresses (full form, then RFC 5952 form):

| # | Block | First (full) | First (5952) | Last (full) | Last (5952) |
|---|---|---|---|---|---|
| 1 | `::1/128` | `0000:0000:0000:0000:0000:0000:0000:0001` | `::1` | same | `::1` |
| 2 | `::/128` | `0000:0000:0000:0000:0000:0000:0000:0000` | `::` | same | `::` |
| 3 | `::ffff:0:0/96` | `0000:0000:0000:0000:0000:ffff:0000:0000` | `::ffff:0:0` | `0000:0000:0000:0000:0000:ffff:ffff:ffff` | `::ffff:ffff:ffff` |
| 4 | `64:ff9b::/96` | `0064:ff9b:0000:0000:0000:0000:0000:0000` | `64:ff9b::` | `0064:ff9b:0000:0000:0000:0000:ffff:ffff` | `64:ff9b::ffff:ffff` |
| 5 | `64:ff9b:1::/48` | `0064:ff9b:0001:0000:0000:0000:0000:0000` | `64:ff9b:1::` | `0064:ff9b:0001:ffff:ffff:ffff:ffff:ffff` | `64:ff9b:1:ffff:ffff:ffff:ffff:ffff` |
| 6 | `100::/64` | `0100:0000:0000:0000:0000:0000:0000:0000` | `100::` | `0100:0000:0000:0000:ffff:ffff:ffff:ffff` | `100::ffff:ffff:ffff:ffff` |
| 7 | `100:0:0:1::/64` | `0100:0000:0000:0001:0000:0000:0000:0000` | `100:0:0:1::` | `0100:0000:0000:0001:ffff:ffff:ffff:ffff` | `100::1:ffff:ffff:ffff:ffff` |
| 8 | `2001::/23` | `2001:0000:0000:0000:0000:0000:0000:0000` | `2001::` | `2001:01ff:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:1ff:ffff:ffff:ffff:ffff:ffff:ffff` |
| 9 | `2001::/32` | `2001:0000:0000:0000:0000:0000:0000:0000` | `2001::` | `2001:0000:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:0:ffff:ffff:ffff:ffff:ffff:ffff` |
| 10 | `2001:1::1/128` | `2001:0001:0000:0000:0000:0000:0000:0001` | `2001:1::1` | same | `2001:1::1` |
| 11 | `2001:1::2/128` | `2001:0001:0000:0000:0000:0000:0000:0002` | `2001:1::2` | same | `2001:1::2` |
| 12 | `2001:1::3/128` | `2001:0001:0000:0000:0000:0000:0000:0003` | `2001:1::3` | same | `2001:1::3` |
| 13 | `2001:2::/48` | `2001:0002:0000:0000:0000:0000:0000:0000` | `2001:2::` | `2001:0002:0000:ffff:ffff:ffff:ffff:ffff` | `2001:2:0:ffff:ffff:ffff:ffff:ffff` |
| 14 | `2001:3::/32` | `2001:0003:0000:0000:0000:0000:0000:0000` | `2001:3::` | `2001:0003:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:3:ffff:ffff:ffff:ffff:ffff:ffff` |
| 15 | `2001:4:112::/48` | `2001:0004:0112:0000:0000:0000:0000:0000` | `2001:4:112::` | `2001:0004:0112:ffff:ffff:ffff:ffff:ffff` | `2001:4:112:ffff:ffff:ffff:ffff:ffff` |
| 16 | `2001:10::/28` | `2001:0010:0000:0000:0000:0000:0000:0000` | `2001:10::` | `2001:001f:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:1f:ffff:ffff:ffff:ffff:ffff:ffff` |
| 17 | `2001:20::/28` | `2001:0020:0000:0000:0000:0000:0000:0000` | `2001:20::` | `2001:002f:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:2f:ffff:ffff:ffff:ffff:ffff:ffff` |
| 18 | `2001:30::/28` | `2001:0030:0000:0000:0000:0000:0000:0000` | `2001:30::` | `2001:003f:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:3f:ffff:ffff:ffff:ffff:ffff:ffff` |
| 19 | `2001:db8::/32` | `2001:0db8:0000:0000:0000:0000:0000:0000` | `2001:db8::` | `2001:0db8:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:db8:ffff:ffff:ffff:ffff:ffff:ffff` |
| 20 | `2002::/16` | `2002:0000:0000:0000:0000:0000:0000:0000` | `2002::` | `2002:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `2002:ffff:ffff:ffff:ffff:ffff:ffff:ffff` |
| 21 | `2620:4f:8000::/48` | `2620:004f:8000:0000:0000:0000:0000:0000` | `2620:4f:8000::` | `2620:004f:8000:ffff:ffff:ffff:ffff:ffff` | `2620:4f:8000:ffff:ffff:ffff:ffff:ffff` |
| 22 | `3fff::/20` | `3fff:0000:0000:0000:0000:0000:0000:0000` | `3fff::` | `3fff:0fff:ffff:ffff:ffff:ffff:ffff:ffff` | `3fff:fff:ffff:ffff:ffff:ffff:ffff:ffff` |
| 23 | `5f00::/16` | `5f00:0000:0000:0000:0000:0000:0000:0000` | `5f00::` | `5f00:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `5f00:ffff:ffff:ffff:ffff:ffff:ffff:ffff` |
| 24 | `fc00::/7` | `fc00:0000:0000:0000:0000:0000:0000:0000` | `fc00::` | `fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` |
| 25 | `fe80::/10` | `fe80:0000:0000:0000:0000:0000:0000:0000` | `fe80::` | `febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff` |

(The addresses were computed with a throwaway script from the registry CSV, not by hand.)

### F3. Change against the 2025-10-09 snapshot: none

Evidence tier: **common practice** (IANA stamps a registry's "Last Updated" on every edit; a stale stamp
proves no edit, but I did not diff against an archived copy). Both registry pages read "Last Updated
2025-10-09", the date the parent's "Deny table" and "Deny rules (IPv6)" name. Every IPv4 prefix in the
parent's table matches a row above. Two entries in the parent's IPv6 list are **not registry rows**: `::/96`
(IPv4-compatible, deprecated by RFC 4291; it appears only as a note in the IPv6 Address Space registry) and
`::ffff:0:0:0/96` (SIIT "translated"); `fec0::/10` (deprecated site-local) and `ff00::/8` (multicast) are rows of
the IPv6 Address Space registry, not of the special-purpose one. All four are outside `2000::/3` and fall to
the default-deny, so the parent's outcome is unchanged; only the label "registry row" does not apply to them.

### F4. The IPv4 multicast block `224.0.0.0/4` is not a special-purpose row but is IANA-registered

Evidence tier: **established**. The "IPv4 Multicast Address Space" registry (Last Updated **2026-08-20**,
expert Stig Venaas, reference RFC 5771) opens: "The multicast addresses are in the range 224.0.0.0 through
239.255.255.255." <https://www.iana.org/assignments/multicast-addresses/multicast-addresses.xhtml>, read
2026-10-25. RFC 5771 (March 2010) lays out the sub-blocks from `224.0.0.0 - 224.0.0.255` to
`239.0.0.0 - 239.255.255.255` (Administratively Scoped Block). <https://www.rfc-editor.org/rfc/rfc5771.txt>.
`224.0.0.0` through `239.255.255.255` is exactly `224.0.0.0/4`; with `240.0.0.0/4` (row 24) it forms the
single contiguous range `224.0.0.0/3`.

### F5. `2000::/3` is the only block global unicast is allocated from; nothing new outside it

Evidence tier: **established**. The "Internet Protocol Version 6 Address Space" registry (Last Updated
**2025-10-23**, registration procedure now "IETF Review", reference RFC 9812) lists `2000::/3` as "Global
Unicast" with the note "IANA unicast address assignments are currently limited to the IPv6 unicast address
range of 2000::/3." The only other rows outside "Reserved by IETF" are `fc00::/7` (Unique Local Unicast),
`fe80::/10` (Link-Scoped Unicast) and `ff00::/8` (Multicast). The registry's own note: "IANA should limit its
allocation of IPv6-unicast address space to the range of addresses that start with binary value 001. The rest
of the global unicast address space (approximately 85% of the IPv6 address space) is reserved for future
definition and use, and is not to be assigned by IANA at this time."
<https://www.iana.org/assignments/ipv6-address-space/ipv6-address-space.xhtml> and its CSV, read 2026-10-25.
The "IPv6 Global Unicast Address Space" registry
(<https://www.iana.org/assignments/ipv6-unicast-address-assignments/ipv6-unicast-address-assignments.csv>, Last
Updated 2025-10-10) holds only prefixes between `2001::/23` and `3fff::/20`; `2002::/16` is listed there as
"6to4", `2d00::/8` onward as reserved, and `3fff::/20` as "Documentation".

Since the parent snapshot, one thing changed in this area: **RFC 9812 (BCP 242, October 2025)** replaced
"IESG approval" with "IETF Review" for opening reserved space and describes it as "held in reserve for
future use if ever the 125-bit unicast space (2000::/3) is found inadequate or inappropriate". It allocates
nothing.
<https://www.rfc-editor.org/rfc/rfc9812.txt>. The reserved space has already been used once for a
special-purpose block: RFC 9812 s1 records that RFC 9602 "moved the range 5f00::/16 from the 'IPv6 Address
Space' registry to the 'IPv6 Special-Purpose Address Space' registry" (row 23 above). Such a block lands
outside `2000::/3` and is denied by default.

## Treatment of each row under the settled design

"Embedded" means the 4 low bytes are judged by the IPv4 table. "Default" means no table entry: the address is
outside `2000::/3`.

### IPv4 (all rows denied; log class is the class returned by `deny`)

| # | Block | Treatment | Class | G=True? | Cost of denying for a web-content fetcher |
|---|---|---|---|---|---|
| 1 | `0.0.0.0/8` | deny | `this-network` | no | none |
| 2 | `0.0.0.0/32` (in 1) | deny | `this-host` | no | none |
| 3, 7, 19 | `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` | deny | `private-use` | no | none for the public web |
| 4 | `100.64.0.0/10` | deny | `shared-address-space` | no | none |
| 5 | `127.0.0.0/8` | deny | `loopback` | no (G=F [1]) | none |
| 6 | `169.254.0.0/16` | deny | `link-local` | no | none |
| 8 | `192.0.0.0/24` | deny | `ietf-protocol-assignments` | no (G=F), but contains G=True rows 11, 12 | none |
| 9 | `192.0.0.0/29` (in 8) | deny | `ipv4-service-continuity-prefix` | no | none |
| 10 | `192.0.0.8/32` (in 8) | deny | `ipv4-dummy-address` | no | none |
| 11 | `192.0.0.9/32` (in 8) | deny | `pcp-anycast` | **yes** | none: Port Control Protocol anycast, a router-discovery service |
| 12 | `192.0.0.10/32` (in 8) | deny | `turn-anycast` | **yes** | none: TURN anycast, a relay-discovery service |
| 13 | `192.0.0.170/32`, `192.0.0.171/32` (in 8) | deny | `nat64-dns64-discovery` | no | none |
| 14, 22, 23 | `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` | deny | `documentation` | no | none |
| 15 | `192.31.196.0/24` | deny | `as112-v4` | **yes** | none: AS112 answers reverse-DNS queries for private ranges; it serves no web content |
| 16 | `192.52.193.0/24` | deny | `amt` | **yes** | none: Automatic Multicast Tunneling relays |
| 17 | `192.88.99.0/24` | deny | `deprecated-6to4-relay-anycast` | blank (deprecated) | none |
| 18 | `192.88.99.2/32` (in 17) | deny | `6a44-relay-anycast` | no | none |
| 20 | `192.175.48.0/24` | deny | `direct-delegation-as112` | **yes** | none: same as row 15 |
| 21 | `198.18.0.0/15` | deny | `benchmarking` | no | none |
| 24 | `240.0.0.0/4` | deny | `reserved` | no | none |
| 25 | `255.255.255.255/32` (in 24) | deny | `limited-broadcast` | no | none |
| - | `224.0.0.0/4` (not a registry row) | deny | `multicast` | n/a | none |

The five G=True IPv4 prefixes are all anycast service prefixes for DNS, tunnelling or NAT-traversal
infrastructure. No web content is published from them, and none is a reason to exempt a block from a deny list
whose purpose is "never connect to infrastructure addresses on behalf of an untrusted URL".

### IPv6

| # | Block | Treatment | Class | G=True? | Cost of denying for a web-content fetcher |
|---|---|---|---|---|---|
| 1 | `::1/128` | default (outside `2000::/3`) | `not-global-unicast` | no | none |
| 2 | `::/128` | default | `not-global-unicast` | no | none |
| 3 | `::ffff:0:0/96` | **embedded**: judged by the low 32 bits | the IPv4 row's class | no | none; `::ffff:8.8.8.8` stays allowed |
| 4 | `64:ff9b::/96` | **embedded** | the IPv4 row's class | **yes** | none: judged, not denied, so a DNS64 answer to a public IPv4 host (`64:ff9b::808:808`) stays allowed |
| 5 | `64:ff9b:1::/48` | default | `not-global-unicast` | no | a DNS64 network that uses the local-use prefix would be refused; an operator's DNS64 prefix of its own global-unicast space cannot be told from any other unicast address and is not affected |
| 6 | `100::/64` | default | `not-global-unicast` | no | none |
| 7 | `100:0:0:1::/64` | default | `not-global-unicast` | no | none |
| 8 | `2001::/23` | **deny, inside `2000::/3`** | `ietf-protocol-assignments` | no (G=F [1] "unless allowed by a more specific allocation") | none; see the more-specific allocations below |
| 9 | `2001::/32` (in 8) | deny wholesale | `teredo` | G=N/A | none: Teredo hosts are tunnelled client addresses, not web servers |
| 10 | `2001:1::1/128` (in 8) | deny | `pcp-anycast` | **yes** | none |
| 11 | `2001:1::2/128` (in 8) | deny | `turn-anycast` | **yes** | none |
| 12 | `2001:1::3/128` (in 8) | deny | `dns-sd-srp-anycast` | **yes** | none: DNS-SD Service Registration Protocol |
| 13 | `2001:2::/48` (in 8) | deny | `benchmarking` | no | none |
| 14 | `2001:3::/32` (in 8) | deny | `amt` | **yes** | none |
| 15 | `2001:4:112::/48` (in 8) | deny | `as112-v6` | **yes** | none |
| 16 | `2001:10::/28` (in 8) | deny | `deprecated-orchid` | blank (deprecated) | none |
| 17 | `2001:20::/28` (in 8) | deny | `orchidv2` | **yes** | none: ORCHIDv2 is a hash-identifier prefix, not a routed network |
| 18 | `2001:30::/28` (in 8) | deny | `drone-remote-id-det` | **yes** | none: drone Entity Tags, an identifier space |
| 19 | `2001:db8::/32` | deny | `documentation` | no | none |
| 20 | `2002::/16` | deny wholesale | `6to4` | G=N/A | a host that is reachable only through a 6to4 address is refused; 6to4 is deprecated in practice and its embedded IPv4 is not what the connection reaches |
| 21 | `2620:4f:8000::/48` | deny | `direct-delegation-as112` | **yes** | none (sits inside ARIN's `2620::/23`; a one-`/48` hole) |
| 22 | `3fff::/20` | deny | `documentation` | no | none |
| 23 | `5f00::/16` | default | `not-global-unicast` | no | none |
| 24 | `fc00::/7` | default | `not-global-unicast` | no (G=F [4]) | none for the public web |
| 25 | `fe80::/10` | default | `not-global-unicast` | no | none |
| - | `ff00::/8`, `::/96`, `::ffff:0:0:0/96`, `fec0::/10`, the rest of `::/3`..`e000::/3` | default | `not-global-unicast` | n/a | none |
| - | `2000::/3` (not a registry row) | the only eligible block | n/a | n/a | n/a |

**Rows marked G=True:** IPv4 11, 12, 15, 16, 20; IPv6 4, 10, 11, 12, 14, 15, 17, 18, 21. Of these only
IPv6 row 4 (`64:ff9b::/96`) is not simply denied, and that is because it is judged by its embedded IPv4.

**`2001::/23` and its "more specific" allocations** (footnote [1] "Unless allowed by a more specific
allocation"): the more specific rows inside it are 2001:1::1, 2001:1::2, 2001:1::3, `2001:3::/32`,
`2001:4:112::/48`, `2001:20::/28`, `2001:30::/28`, all G=True. Teredo `2001::/32` is G=N/A. `2001::/23` as a whole is
denied; the G=True children are anycast services, a tunnel relay set, an AS112 sink and two identifier
spaces, none a web-content host. The only G=True block outside the `/23` and inside `2000::/3` is
`2620:4f:8000::/48`, also AS112. Denying the `/23` wholesale, instead of denying only its listed children, costs
nothing today and also covers any future child IANA adds to it.

## Public controls for the Level 4a tests

Every control below was checked by script against every IPv4 row, `224.0.0.0/4`, and (IPv6) against every
explicit block inside `2000::/3`.

### IPv4

Nested rows (2, 9-13, 18, 25) have no allowed neighbour of their own: each neighbour is inside the parent
block. Use their first and last addresses as deny cases only. `240.0.0.0/4` borders multicast on its low side
(`239.255.255.255`, denied) and has no neighbour above. The outer boundary of each disjoint deny range is
listed; every address is allowed.

| Deny range (rows) | Just below (allowed) | Just above (allowed) |
|---|---|---|
| `0.0.0.0/8` (1, 2) | none (address `0.0.0.0` is the bottom) | `1.0.0.0` |
| `10.0.0.0/8` (3) | `9.255.255.255` | `11.0.0.0` |
| `100.64.0.0/10` (4) | `100.63.255.255` | `100.128.0.0` |
| `127.0.0.0/8` (5) | `126.255.255.255` | `128.0.0.0` |
| `169.254.0.0/16` (6) | `169.253.255.255` | `169.255.0.0` |
| `172.16.0.0/12` (7) | `172.15.255.255` | `172.32.0.0` |
| `192.0.0.0/24` (8-13) | `191.255.255.255` | `192.0.1.0` |
| `192.0.2.0/24` (14) | `192.0.1.255` | `192.0.3.0` |
| `192.31.196.0/24` (15) | `192.31.195.255` | `192.31.197.0` |
| `192.52.193.0/24` (16) | `192.52.192.255` | `192.52.194.0` |
| `192.88.99.0/24` (17, 18) | `192.88.98.255` | `192.88.100.0` |
| `192.168.0.0/16` (19) | `192.167.255.255` | `192.169.0.0` |
| `192.175.48.0/24` (20) | `192.175.47.255` | `192.175.49.0` |
| `198.18.0.0/15` (21) | `198.17.255.255` | `198.20.0.0` |
| `198.51.100.0/24` (22) | `198.51.99.255` | `198.51.101.0` |
| `203.0.113.0/24` (23) | `203.0.112.255` | `203.0.114.0` |
| `224.0.0.0/3` (multicast + 24, 25) | `223.255.255.255` | none (`255.255.255.255` is the top) |

Plus the sanity controls `8.8.8.8` and `1.1.1.1`. Nested-neighbour deny cases worth adding because they are
the ones a loose implementation gets wrong: `192.0.0.7`, `192.0.0.11`, `192.0.0.169`, `192.0.0.172` (all inside
`192.0.0.0/24`, so denied), `192.88.99.1`, `192.88.99.3`, `239.255.255.255`, `240.0.0.0`, `255.255.255.254`.

### IPv6

Two ends of `2000::/3`:

| Address | Verdict |
|---|---|
| `1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` (just outside) | denied (default) |
| `2000::` (just inside) | allowed |
| `3fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff` (just inside) | allowed |
| `4000::` (just outside) | denied (default) |

Next to every explicit block inside `2000::/3` (all allowed). The nested rows inside `2001::/23` have no
allowed neighbour, because every neighbour is inside the `/23`; the first and last addresses of those rows are
deny cases.

| Deny block | Just below (allowed) | Just above (allowed) |
|---|---|---|
| `2001::/23` | `2000:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:200::` |
| `2001:db8::/32` | `2001:db7:ffff:ffff:ffff:ffff:ffff:ffff` | `2001:db9::` |
| `2002::/16` | `2001:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `2003::` |
| `2620:4f:8000::/48` | `2620:4f:7fff:ffff:ffff:ffff:ffff:ffff` | `2620:4f:8001::` |
| `3fff::/20` | `3ffe:ffff:ffff:ffff:ffff:ffff:ffff:ffff` | `3fff:1000::` |

Some controls are real allocations (`2001:200::` APNIC, `2001:db7:...`/`2001:db9::` APNIC `2001:c00::/23`,
`2003::` RIPE NCC, `2620:4f:7fff:...`/`2620:4f:8001::` ARIN `2620::/23`), and some are unallocated space
(`2000:ffff:...`, `2001:ffff:...`, `3ffe:ffff:...`, `3fff:1000::`). The classifier decides by block membership,
not by allocation, so unallocated space inside `2000::/3` is allowed by design; the unallocated controls prove
the boundary, not that a host lives there. Embedded-IPv4 controls: `::ffff:8.8.8.8` (`::ffff:808:808`) and
`64:ff9b::808:808` allowed; first and last of each of the two blocks denied (they judge as `0.0.0.0` and
`255.255.255.255`); `::ffff:127.0.0.1` and `64:ff9b::a00:1` (= `64:ff9b::10.0.0.1`) denied through the IPv4 table.

## Class names

Short kebab-case from the registry Name; acronyms for the long anycast names. Classes are used only in logs, so
two rows may share one (`private-use`, `documentation`, `benchmarking`, `pcp-anycast`, `turn-anycast`,
`direct-delegation-as112`, `amt`). The names are listed per row in the treatment tables above; the complete
set is:

- IPv4: `this-network`, `this-host`, `private-use`, `shared-address-space`, `loopback`, `link-local`,
  `ietf-protocol-assignments`, `ipv4-service-continuity-prefix`, `ipv4-dummy-address`, `pcp-anycast`,
  `turn-anycast`, `nat64-dns64-discovery`, `documentation`, `as112-v4`, `amt`, `deprecated-6to4-relay-anycast`,
  `6a44-relay-anycast`, `direct-delegation-as112`, `benchmarking`, `reserved`, `limited-broadcast`, `multicast`.
- IPv6 (explicit, inside `2000::/3`): `ietf-protocol-assignments`, `teredo`, `pcp-anycast`, `turn-anycast`,
  `dns-sd-srp-anycast`, `benchmarking`, `amt`, `as112-v6`, `deprecated-orchid`, `orchidv2`,
  `drone-remote-id-det`, `documentation`, `6to4`, `direct-delegation-as112`.
- IPv6 default-deny (outside global unicast): `not-global-unicast`.
- Rows judged by embedded IPv4 (`::ffff:0:0/96`, `64:ff9b::/96`) report the IPv4 row's class.

## How comparable projects do it

The comparable-library survey (which classifiers deny which IPv6 blocks, and the JDK predicates' gaps) is in
the parent research, F7 of
[`../../research/outbound-url-policy.md`](../../research/outbound-url-policy.md), and is not repeated. One
fact from this question's sources bears on it: `3fff::/20` (RFC 9637, 2024-07) and `100:0:0:1::/64` (RFC 9780,
2025-04) are recent additions, so a library whose table was written before 2025 will lack them. Beyond
`::/128` and the other rows the default-deny covers, an explicit-deny IPv6 table has to track these additions;
a default-deny outside `2000::/3` does not.

## Pitfalls and regrets

- **Nested rows.** Nine IPv4 prefixes and ten IPv6 prefixes (all inside `2001::/23`) sit inside another row. Order matters only for
  the log class: a most-specific-first table logs `teredo`, a first-match table over the parent logs
  `ietf-protocol-assignments`. A test that asserts the class for a nested row's first address must say which.
- **The default-deny hides drift.** A new special-purpose block carved out of reserved space outside `2000::/3`
  (as `5f00::/16` was) needs no table edit; one carved inside `2000::/3` (as `3fff::/20` was) does. The Level 4a
  "every row first/last" test only catches it if the refreshed registry is read into the test, so the refresh
  checklist below keeps the test table and the source table in step.
- **CSV parsing traps for a refresh.** `Address Block` cells carry footnote markers (`192.0.0.0/24 [2]`,
  `2002::/16 [3]`) and one cell holds two prefixes (`192.0.0.170/32, 192.0.0.171/32`); Globally Reachable can be
  `N/A [2]`, `False [4]`, or blank on deprecated rows; Name cells can be quoted (`"This network"`). A refresh
  that treats the CSV as plain comma-separated fields breaks on the last case.
- **`240.0.0.0/4` and `224.0.0.0/4` are adjacent.** A range-based table merges them to `224.0.0.0/3`; a test
  that treats `239.255.255.255` as an allowed control would be wrong.
- **`192.0.0.0/24` is "Not useable unless by virtue of a more specific reservation".** The `/24` itself is
  denied; the G=True children inside it are the reason a naive "skip globally reachable rows" rule would punch
  holes in it.
- **IPv6 text form.** RFC 5952 compresses `100:0:0:1:ffff:ffff:ffff:ffff` to `100::1:ffff:ffff:ffff:ffff`
  and keeps a single zero group as `0` (`2001:0:ffff:...`); a hand-written test that spells these differently
  fails only in the string-parsing path, so tests in `commonTest` that feed bytes should not depend on it.

## Recommendation for this repo

- Build `AddressRanges` as two small tables: IPv4 as 17 disjoint `(base, prefix)` ranges (or the 26 registry
  prefixes plus `224.0.0.0/4`, most-specific-first, if finer log classes are wanted), and IPv6 as the
  five explicit blocks inside `2000::/3` plus the nested `2001::/23` children listed first for log class. A
  4-byte address is looked up in the IPv4 table; a 16-byte address first checks the two embedded forms
  (`::ffff:0:0/96`, `64:ff9b::/96`) and recurses on its low 4 bytes, then `2002::/16` and `2001::/32`, then
  membership in `2000::/3` and the five blocks, and otherwise returns `not-global-unicast`.
- Drive the Level 4a test from the row lists in this document: for each of the 25 + 25 rows, first and last
  address must be denied (for `::ffff:0:0/96` and `64:ff9b::/96` they judge as `0.0.0.0` and
  `255.255.255.255`), plus `224.0.0.0` and `239.255.255.255`; then the control tables above must be allowed
  (IPv4: the 32 boundary addresses plus `8.8.8.8` and `1.1.1.1`; IPv6: `2000::`, `3fff:ffff:...`, the ten block neighbours, `::ffff:8.8.8.8`,
  `64:ff9b::808:808`) and the two outside ends of `2000::/3` denied.
- Put the snapshot note and refresh steps beside the table: "IANA IPv4 and IPv6 Special-Purpose Address
  Registries, Last Updated 2025-10-09 (read 2026-10-25); multicast `224.0.0.0/4` from the IPv4 Multicast Address
  Space registry (RFC 5771), global unicast `2000::/3` from the IPv6 Address Space registry (Last Updated
  2025-10-23). Refresh: fetch the two `...-1.csv` files, strip footnote markers and split multi-prefix cells,
  diff against the table, add any block inside `2000::/3` or any IPv4 row; also check that the IPv6 Address Space
  registry still limits unicast allocation to `2000::/3`; update the Level 4a row lists and the date."
- Reject the obvious alternative of denying only the registry's G=False rows: it would leave the five IPv4 and
  nine IPv6 G=True prefixes open to a request even though the fetcher has no reason ever to reach them, and the
  settled policy is to deny the whole registry.

## Load-bearing claims

All sources read on 2026-10-25.

1. **IPv4 Special-Purpose registry is current as of 2025-10-09.** Verbatim: "Created 2009-08-19 Last Updated 2025-10-09". <https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry.xhtml>, read 2026-10-25.
2. **IPv6 Special-Purpose registry is current as of 2025-10-09.** Verbatim: "Created 2006-01-10 Last Updated 2025-10-09". <https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry.xhtml>, read 2026-10-25.
3. **The registries' rows are as listed in F1 and F2** (CSV). Verbatim from the IPv6 CSV: `2001::/23,IETF Protocol Assignments,[RFC 2928],2000-09,N/A,False [1],False [1],False [1],False [1],False` and `3fff::/20,Documentation,[RFC 9637],2024-07,N/A,False,False,False,False,False`; from the IPv4 CSV: `"192.0.0.170/32, 192.0.0.171/32",NAT64/DNS64 Discovery,"[RFC 8880]; [RFC 7050, Section 2.2]",2013-02,N/A,False,False,False,False,True`. <https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry-1.csv>, <https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry-1.csv>, read 2026-10-25.
4. **`2001::/23` footnote on more specific allocations.** Verbatim: "Unless allowed by a more specific allocation." (IPv6 registry footnote [1]). <https://www.iana.org/assignments/iana-ipv6-special-registry/iana-ipv6-special-registry.xhtml>, read 2026-10-25.
5. **`192.0.0.0/24` footnote.** Verbatim: "Not useable unless by virtue of a more specific reservation." (IPv4 registry footnote [2]). <https://www.iana.org/assignments/iana-ipv4-special-registry/iana-ipv4-special-registry.xhtml>, read 2026-10-25.
6. **IPv4 multicast is `224.0.0.0` through `239.255.255.255`.** Verbatim: "The multicast addresses are in the range 224.0.0.0 through 239.255.255.255. Address assignments are listed below." <https://www.iana.org/assignments/multicast-addresses/multicast-addresses.xhtml> (Last Updated 2026-08-20), read 2026-10-25. RFC 5771 table row: "239.0.0.0 - 239.255.255.255   (/8)       Administratively Scoped Block", <https://www.rfc-editor.org/rfc/rfc5771.txt>.
7. **`2000::/3` is the global unicast block IANA allocates from.** Verbatim: "IANA unicast address assignments are currently limited to the IPv6 unicast address range of 2000::/3." (IPv6 Address Space CSV, row `2000::/3,Global Unicast`), and "IANA should limit its allocation of IPv6-unicast address space to the range of addresses that start with binary value 001." <https://www.iana.org/assignments/ipv6-address-space/ipv6-address-space-1.csv> and <https://www.iana.org/assignments/ipv6-address-space/ipv6-address-space.xhtml> (Last Updated 2025-10-23), read 2026-10-25.
8. **RFC 9812 changes the approval procedure only.** Verbatim: "IANA has updated the registration procedure of the "IPv6 Address Space" registry [IANA1] to "IETF Review"." and "This is the address space held in reserve for future use if ever the 125-bit unicast space (2000::/3) is found inadequate or inappropriate." <https://www.rfc-editor.org/rfc/rfc9812.txt> (October 2025), read 2026-10-25.
9. **Special-purpose blocks have been carved out of reserved space outside `2000::/3`.** Verbatim: "which moved the range 5f00::/16 from the "IPv6 Address Space" registry [IANA1] to the "IPv6 Special-Purpose Address Space" registry [IANA3]." <https://www.rfc-editor.org/rfc/rfc9812.txt>, read 2026-10-25.
10. **The IPv6 Address Space registry notes `::/96` as deprecated and lists `fec0::/10`, `ff00::/8` as non-unicast.** Verbatim: "::/96, formerly defined as the "IPv4-compatible IPv6 address" prefix, was deprecated by [RFC 4291]." and `fec0::/10,Reserved by IETF,[RFC 3879],Deprecated by [RFC 3879] in September 2004. Formerly a Site-Local scoped address prefix.` <https://www.iana.org/assignments/ipv6-address-space/ipv6-address-space-1.csv>, read 2026-10-25.

## Gaps

- **No archived copy of the registries was diffed.** "No change since 2025-10-09" rests on both pages
  reporting that same Last Updated date; I did not fetch the 2025-10-09 snapshot from an archive to compare row by row.
- **The XHTML and CSV were compared by block, name and the last four flag columns; the Source column and
  the RFC and date columns were read from the CSV only.**
- **Cost-of-denying statements are my reading of each row's purpose** (taken from the RFC titles in the
  Name column), not a measurement: I did not check what is actually published on the anycast prefixes. In
  particular I did not inspect any host on `192.31.196.0/24`, `192.52.193.0/24`, `192.175.48.0/24`,
  `2001:3::/32`, `2001:4:112::/48` or `2620:4f:8000::/48`.
- **The IPv6 Global Unicast Address Space registry and the IPv4 Multicast registry were read for scope only**
  (is anything outside `2000::/3`; what does multicast span); every one of their rows was not enumerated.
- **The 5952 forms and the controls were computed by a throwaway script, not by an independent tool.** They
  follow the RFC 5952 rule stated above; a reviewer can recompute them from the first/last full forms.
- **RFC 6890 and RFC 8190 (the registries' governing RFCs) were not read;** their Section 2 definitions of
  the flags were taken from the registry's own column names.
- **The `2002::/16` "wholesale" cost** (a host reachable only through a 6to4 address) is reasoning, not
  measured against any deployment; the same gap is noted in the parent research.
