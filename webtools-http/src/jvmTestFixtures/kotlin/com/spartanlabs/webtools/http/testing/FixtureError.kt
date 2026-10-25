package com.spartanlabs.webtools.http.testing

/**
 * A fixture file could not be read or is not a well-formed `.http` file: thrown by [Fixture.parse] and
 * [FixtureLoader.load]. It is an `AssertionError`, because a malformed fixture is a fault in the test's own
 * input, so it fails the test instead of being swallowed as an operational failure.
 *
 * **Stable Core, source compatibility only.** The message names the origin (the resource name or the
 * caller's label) and, for a syntax fault, the line number (`<origin>, line <n>: <what>`), and never a header
 * value or a body byte. Equality is identity. Only the fixtures build it.
 */
public class FixtureError internal constructor(message: String, cause: Throwable?) : AssertionError(message, cause)
