package com.spartanlabs.webtools.http

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The supplied [BudgetStore]: keeps the last snapshot in memory.
 *
 * **Stable Core.** Thread-safe; it survives nothing, so it is the default and the worked example of the seam. [load] returns
 * the last saved snapshot or [BudgetSnapshot.Empty]; `toString()` is `InMemoryBudgetStore`.
 */
public class InMemoryBudgetStore : BudgetStore {
    private val lock = Mutex()
    private var stored: BudgetSnapshot = BudgetSnapshot.Empty            // guarded by lock

    override suspend fun load(): Result<BudgetSnapshot> = lock.withLock { Result.success(stored) }
    override suspend fun save(snapshot: BudgetSnapshot): Result<Unit> = lock.withLock { stored = snapshot; Result.success(Unit) }
    override fun toString(): String = "InMemoryBudgetStore"
}
