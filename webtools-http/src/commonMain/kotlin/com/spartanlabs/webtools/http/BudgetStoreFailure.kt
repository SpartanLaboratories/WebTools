package com.spartanlabs.webtools.http

/** The failures the budget itself reports about a store call, with constant messages (a store's own exception keeps its own message, which is never logged). */
internal class BudgetStoreFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    companion object {
        const val TIMEOUT_MESSAGE: String = "The budget store did not answer in time"
        const val CANCELLED_MESSAGE: String = "The budget store call was cancelled"
    }
}
