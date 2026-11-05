// webtools-http-budget — C4 model as code (Structurizr DSL). Source of truth for the structure shown on docs/dashboards/webtools-http-budget/index.html.
// As-is at WebTools feat/issue-67-call-budget 327164f (2026-11-04), the call budget inside webtools-http 1.0.0 (new, unreleased): the CallBudget port with Permit and withPermit, CallBudget.Unlimited, WindowedCallBudget, the rate-signal vocabulary with RetryAfter, the store and observer seams with their defaults, the internal ledger, and two test fixtures. Code is authoritative on any conflict.
workspace "WebTools" "Kotlin internet I/O helpers, published as independent single-concern artifacts and built by one Gradle build." {

    !identifiers hierarchical

    model {
        consumer = person "Consumer" "A Kotlin service that declares its provider's windows, acquires a permit before each call, reads the answer's rate signals into the permit and closes the budget it built."
        provider = softwareSystem "Rate-limited provider" "An HTTP API with documented rate windows: it counts calls, answers 429 or 503 when over, and may send Retry-After and remaining-count headers." "External"

        webtools = softwareSystem "WebTools" "Gradle multi-module build publishing io.github.spartanlaboratories artifacts to Maven Central." {
            budget = container "webtools-http-budget" "The call budget: grants permits under a provider's rate windows and lane shares, pauses on server signals and holds a hard window across a restart. Ships inside webtools-http; all of it in commonMain, its two fixtures in the test-fixtures variant." "Kotlin Multiplatform · commonMain · webtools-http 1.0.0 unreleased" {
                // ---- the port ----
                port      = component "CallBudget" "The port: suspend acquire(cost, lane, maxWait): Result<Permit>, utilisation(), flush() and an idempotent, non-suspending close(), under a ten-point contract. Implementing it needs the opt-in." "interface · AutoCloseable · gated" "Public"
                permit    = component "Permit" "A granted unit, charged at the grant: complete(signals) when the request was sent, release() when it never was; both idempotent and run under NonCancellable." "interface · gated" "Public"
                withPermit = component "withPermit" "Acquires, runs the block and completes the permit as spent in a finally unless the block settled it; a refused or closed acquire is returned and the block does not run." "suspend extension function" "Public"
                unlimited = component "CallBudget.Unlimited" "Grants everything at once; ignores signals and close(); checks its arguments as the supplied budget does. Needs no opt-in." "object" "Public"
                // ---- the supplied budget and its internals ----
                windowed  = component "WindowedCallBudget" "The supplied budget, final, built by WindowedCallBudget(windows) { }: epoch-aligned fixed windows with a boundary guard, lane floors, first-come-first-served waits, pauses from server signals and a hard window saved write-ahead to a store. Needs no opt-in." "final class · factory · builder" "Public"
                admission = component "Admission and waits" "admitLocked, awaitSlot, replan and withdraw over one coroutine Mutex; a waiter selects on the close latch and its conflated wake channel, each wait at most one minute." "internal" "Internal"
                settle    = component "Permit settlement" "WindowedPermit: complete applies the signals (reconcile a count, extend the pause) and re-plans; release refunds and re-plans; both under NonCancellable." "internal" "Internal"
                storeCalls = component "Store calls" "One lazy restore, write-ahead saves in blocks of persistAhead and flush()'s exact write, each bounded by storeTimeout; a failure is a StoreFailed event and the grants go on." "internal" "Internal"
                ledger    = component "BudgetLedger" "The coroutine-free ledger: the slot search, the lane reserves, grants and refunds, reconciliation, the pause, the restore and the snapshots." "internal class" "Internal"
                cells     = component "WindowLedger · LedgerCell" "One fixed-window ledger per rate window: indices from the epoch, the guard's two-cell charge, the backward-step copy and the cell cap." "internal classes" "Internal"
                time      = component "LedgerTime" "LedgerClock reads the wall clock and the time source together; LedgerNow and Pause." "internal classes" "Internal"
                httpDate  = component "HttpDate" "RFC 9110's three HTTP-date forms, with constant-message failures." "internal object" "Internal"
                // ---- the vocabulary ----
                vocab     = component "Vocabulary" "Lane, RateWindow, RateSignals, WindowReading, BudgetUtilisation and WindowUtilisation: final values with factories and builders." "final classes" "Public"
                retryAfter = component "RetryAfter" "parse(value, now) and parse(value, now, serverDate): delay-seconds or an HTTP-date to a Duration, never negative, saturating; a failure's message never echoes the value." "object" "Public"
                // ---- the seams ----
                reader    = component "RateSignalsReader" "read(response, nowEpochMillis): RateSignals, never throwing for bad input. Standard reads a 429, and a 503 with a Retry-After that parses." "interface · gated · Standard" "Public"
                store     = component "BudgetStore" "load() and save(snapshot); must be cancellable; last-writer-wins. InMemoryBudgetStore, the default, survives nothing." "interface · gated · InMemoryBudgetStore" "Public"
                snapshot  = component "BudgetSnapshot · WindowSnapshot" "What a store keeps: version 1, the stamp, each hard window's count per period and the pause; no serializer." "final classes" "Public"
                observer  = component "BudgetObserver" "onEvent(event), called after the lock is released, on the producing call's thread; an Exception it throws is logged and ignored. None ignores everything." "interface · gated · None" "Public"
                events    = component "BudgetEvent" "The open root with seven final leaves (Granted, Refused, Paused, HardStop, Reconciled, StoreFailed, Restored), with the open constants classes PauseSource and StoreOperation." "abstract class · gated · final leaves" "Public"
                // ---- the test fixtures ----
                fixtures  = component "Test fixtures" "RecordingBudgetObserver (the events in arrival order) and FakeBudgetStore (scripted failures and delays in virtual time), in webtools-http's test-fixtures variant." "jvmTestFixtures · testFixtures(...)" "Public"
            }
            core = container "webtools-http common core" "What the budget uses from the rest of the module: HttpFailure's BudgetExhausted and Closed with ExhaustedReason, HttpResponse, WallClock, the opt-in marker, the DSL marker and the internal Log seam." "Kotlin Multiplatform · commonMain"
        }

        consumer -> webtools.budget.windowed "Builds it with WindowedCallBudget(windows) { }, then acquires, flushes and closes it"
        consumer -> webtools.budget.withPermit "Wraps each call in withPermit"
        consumer -> webtools.budget.vocab "Declares its provider's windows and its lanes"
        consumer -> webtools.budget.reader "Reads each answer's signals with Standard, or its own reader"
        consumer -> webtools.budget.permit "Completes it with the signals, or releases it"
        consumer -> provider "Calls it through a transport, one permit per call"
        webtools.budget.windowed -> webtools.budget.port "Implements"
        webtools.budget.unlimited -> webtools.budget.port "Implements"
        webtools.budget.withPermit -> webtools.budget.port "Acquires through it; completes the permit in a finally"
        webtools.budget.port -> webtools.budget.permit "Grants"
        webtools.budget.port -> webtools.core "Fails with BudgetExhausted, or Closed(budget) after close()"
        webtools.budget.windowed -> webtools.budget.admission "Runs every acquire through"
        webtools.budget.windowed -> webtools.budget.settle "Settles its permits through"
        webtools.budget.admission -> webtools.budget.ledger "Plans, reserves and grants on it, under the lock"
        webtools.budget.settle -> webtools.budget.ledger "Reconciles, pauses and refunds on it"
        webtools.budget.admission -> webtools.budget.storeCalls "Restores lazily and saves write-ahead through"
        webtools.budget.storeCalls -> webtools.budget.store "Loads once, saves snapshots, each call bounded by storeTimeout"
        webtools.budget.store -> webtools.budget.snapshot "Keeps"
        webtools.budget.windowed -> webtools.budget.observer "Delivers every event after the lock is released"
        webtools.budget.observer -> webtools.budget.events "Receives"
        webtools.budget.ledger -> webtools.budget.cells "Keeps one per rate window"
        webtools.budget.ledger -> webtools.budget.time "Reads now through"
        webtools.budget.ledger -> webtools.budget.vocab "Reads the windows, the lanes and the signals"
        webtools.budget.time -> webtools.core "Reads WallClock beside the time source"
        webtools.budget.reader -> webtools.core "Reads an HttpResponse's status, Retry-After and Date"
        webtools.budget.reader -> webtools.budget.retryAfter "Parses every Retry-After line with"
        webtools.budget.reader -> webtools.budget.httpDate "Reads the answer's Date with"
        webtools.budget.retryAfter -> webtools.budget.httpDate "Reads a date value with"
        webtools.budget.reader -> webtools.budget.vocab "Returns RateSignals"
        webtools.budget.windowed -> webtools.core "Logs through the Log seam: numbers and class names only"
        webtools.budget.fixtures -> webtools.budget.observer "Implements it with RecordingBudgetObserver"
        webtools.budget.fixtures -> webtools.budget.store "Implements it with FakeBudgetStore"
        webtools.core -> webtools.budget.port "Its opt-in marker gates implementing the five ports and subclassing BudgetEvent"
    }

    views {
        systemContext webtools "Context" "WebTools, the consumer of the call budget and the provider whose windows it keeps." {
            include *
            autoLayout lr
        }
        container webtools "Containers" "The call budget and the part of webtools-http it uses." {
            include *
            autoLayout lr
        }
        component webtools.budget "Components" "The call budget as built at feat/issue-67-call-budget 327164f." {
            include *
            autoLayout tb
        }
        dynamic webtools.budget "Permit" "A consumer wraps one call in withPermit, the provider throttles it, and the budget pauses, as built." {
            consumer -> webtools.budget.withPermit "withPermit(1, Lane.Tracker, 5.seconds) { permit -> … }"
            webtools.budget.withPermit -> webtools.budget.port "acquire(cost, lane, maxWait)"
            webtools.budget.windowed -> webtools.budget.admission "admitLocked: plan the slot, grant now or queue"
            webtools.budget.admission -> webtools.budget.ledger "plan, then grantDirect: charged at the grant"
            consumer -> provider "the call, through a transport: 429 with Retry-After: 30"
            consumer -> webtools.budget.reader "Standard.read(response, now): throttled, retryAfter 30 s"
            consumer -> webtools.budget.permit "complete(signals)"
            webtools.budget.settle -> webtools.budget.ledger "extend the pause to 30 s, capped by maxPause; re-plan the waiters"
            webtools.budget.windowed -> webtools.budget.observer "onEvent(Granted), onEvent(Paused): after the lock is released"
            autoLayout lr
        }
        styles {
            element "Person"          { shape person  background #08427b color #ffffff }
            element "Software System" { background #1168bd color #ffffff }
            element "External"        { background #999999 color #ffffff }
            element "Container"       { background #438dd5 color #ffffff }
            element "Component"       { background #85bbf0 color #000000 }
            element "Internal"        { background #c6dcf5 color #000000 }
            element "Standalone"      { background #eeeeee color #000000 border dotted }
            element "Planned"         { background #ffffff color #666666 border dashed opacity 60 }
        }
    }
}
