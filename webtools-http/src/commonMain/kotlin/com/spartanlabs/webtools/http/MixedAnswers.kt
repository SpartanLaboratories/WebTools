package com.spartanlabs.webtools.http

/**
 * What an `Untrusted` policy does with a resolver answer that holds both allowed and denied addresses.
 *
 * **Stable Core.** An enum because it is a consumer-chosen input: the library never branches on a value it did not
 * produce, so a future entry cannot break a producer. A consumer that reads `policy.mixedAnswers` in a `when` keeps an
 * `else`.
 */
public enum class MixedAnswers {
    /** The whole answer is refused when any address is denied (the default). */
    Reject,

    /** The denied addresses are dropped and the allowed ones are used in answer order; the answer is refused only when none is left. */
    DropDenied,
}
