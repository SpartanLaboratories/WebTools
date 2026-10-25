package com.spartanlabs.webtools.http.testing

/**
 * How a [FixtureRecorder] treats a file that already exists.
 *
 * **Stable Core, source compatibility only.** Chosen by the consumer through `WEBTOOLS_HTTP_RECORD`
 * ([RecordingGate.evaluate]).
 */
public enum class RecordMode {
    /** Keep an existing file untouched (`WEBTOOLS_HTTP_RECORD` = `1`, `true` or `new`). */
    New,

    /** Rewrite an existing file; byte-identical for an unchanged response (`all` or `overwrite`). */
    Overwrite,
}
