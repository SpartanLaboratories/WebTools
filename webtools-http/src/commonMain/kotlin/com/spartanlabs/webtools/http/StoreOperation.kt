package com.spartanlabs.webtools.http

/**
 * Which store call failed; the `operation` of [BudgetEvent.StoreFailed].
 *
 * **Stable Core.** An open constants class, as [PauseSource] is: equality by [name], `toString()` the name; it may gain
 * constants in a feature release, so a `when` over it keeps an `else`.
 *
 * @param name the operation's name, as its constant is spelled.
 */
public class StoreOperation private constructor(
    /** The operation's name, as its constant is spelled. */
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = other is StoreOperation && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name

    /** The operations. */
    public companion object {
        /** `BudgetStore.load`, at the restore. */
        public val Load: StoreOperation = StoreOperation("Load")

        /** `BudgetStore.save`, write-ahead or in `flush()`. */
        public val Save: StoreOperation = StoreOperation("Save")
    }
}
