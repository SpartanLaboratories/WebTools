package com.spartanlabs.webtools.http.testing

/**
 * What a [FakeHttpTransport] does with a request no stub matches: [Fail] throws an [UnmatchedRequestError]
 * (the default, so a stray request fails the test), [Respond] serves one [Step] (the opt-in for a lenient
 * fake). Sealed: the fake interprets exactly these two policies, and the closed pair is the contract.
 *
 * **Stable Core, source compatibility only.** Equality is identity; `toString()` prints the policy and, for
 * [Respond], its step.
 */
public sealed interface Unmatched {
    /** Throw an [UnmatchedRequestError]; the call is recorded with `matched = false`. */
    public object Fail : Unmatched {
        override fun toString(): String = "Unmatched.Fail"
    }

    /**
     * Serve [step] to every unmatched request; the call is recorded with `matched = false`.
     *
     * @property step the step served.
     */
    public class Respond(
        /** The step served to an unmatched request. */
        public val step: Step,
    ) : Unmatched {
        override fun toString(): String = "Unmatched.Respond($step)"
    }
}
