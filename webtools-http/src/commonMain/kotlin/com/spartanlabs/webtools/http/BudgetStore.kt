package com.spartanlabs.webtools.http

/**
 * Keeps a [BudgetSnapshot] across runs so a hard window survives a restart. The budget calls [load] once, lazily, and [save]
 * write-ahead and on `flush()`, each under its own lock and bounded by `storeTimeout`.
 *
 * **Experimental (implement).** Implementing needs `@OptIn(ExperimentalForInheritanceWebToolsApi::class)`; [InMemoryBudgetStore]
 * needs none. An implementation **must be cancellable**: the budget bounds each call with `withTimeoutOrNull`, which cannot
 * interrupt a thread that blocks, so a blocking store (JDBC, a file write) moves its blocking call to a dispatcher built for it
 * and suspends on it. A store is last-writer-wins: it must not be shared between processes that count the same windows. [save]
 * replaces what [load] returns.
 */
@SubclassOptInRequired(ExperimentalForInheritanceWebToolsApi::class)
public interface BudgetStore {
    /**
     * Reads the stored snapshot.
     *
     * @return what is stored, or [BudgetSnapshot.Empty] when nothing is; a failure when the store cannot be read.
     */
    public suspend fun load(): Result<BudgetSnapshot>

    /**
     * Replaces the stored snapshot.
     *
     * @param snapshot what to keep, replacing the previous.
     * @return success, or the failure that left the old snapshot in place (or unknown).
     */
    public suspend fun save(snapshot: BudgetSnapshot): Result<Unit>
}
