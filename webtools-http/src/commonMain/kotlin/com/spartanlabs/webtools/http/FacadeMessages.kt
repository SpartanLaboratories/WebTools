package com.spartanlabs.webtools.http

// The constant texts the call facades share. A test asserts on them; none holds a URL, a header value, a body or a value.

// The component names the facades report in `HttpFailure.Closed`.
internal const val CLIENT_COMPONENT: String = "client"
internal const val FETCHER_COMPONENT: String = "fetcher"

// The logger names (the cache, the budget and the transport log on their own).
internal const val UPSTREAM_CLIENT_LOGGER_NAME: String = "com.spartanlabs.webtools.http.UpstreamClient"
internal const val URL_FETCHER_LOGGER_NAME: String = "com.spartanlabs.webtools.http.UrlFetcher"

// The rules of the `TransportFailure.InvalidRequest` the facades create. Each is constant and written by the library.
internal const val RULE_SETTINGS: String = "the call settings could not be applied"
internal const val RULE_MAX_WAIT: String = "maxWait must not be negative"
internal const val RULE_MAX_AGE: String = "maxAge must not be negative"
internal const val RULE_COST: String = "cost must be at least 1"
internal const val RULE_EMPTY_SEGMENT: String = "a path segment must not be empty"
internal const val RULE_DOT_SEGMENT: String = "a path segment must not be . or .."
internal const val RULE_SEGMENT_UNICODE: String = "a path segment must be valid Unicode"
internal const val RULE_QUERY_UNICODE: String = "a query name and value must be valid Unicode"
internal const val RULE_URL: String = "the request URL could not be built"
internal const val RULE_REQUEST: String = "the request could not be built"

// The messages of the `IllegalArgumentException` a construction refusal throws. A prefix is followed by a constant (a reason name or a rule), never by a value.
internal const val MSG_ENDPOINT_ID: String = "id must not be blank"
internal const val MSG_ENDPOINT_NAME: String = "name must not be blank"
internal const val MSG_ENDPOINT_TTL: String = "ttl must not be negative"
internal const val MSG_ENDPOINT_MISS_TTL: String = "missTtl must not be negative"
internal const val MSG_ENDPOINT_COST: String = "cost must be at least 1"
internal const val MSG_ENDPOINT_MISSES: String = "misses must be statuses in 100..599 other than 429"
internal const val MSG_DEFAULT_MAX_WAIT: String = "defaultMaxWait must not be negative"
internal const val MSG_BASE_URL_QUERY: String = "baseUrl must not have a query"
internal const val MSG_BASE_URL_REFUSED: String = "baseUrl is refused by the outbound-URL policy: "          // + RefusalReason.name
internal const val MSG_DEFAULT_HEADER: String = "a default header is invalid: "                              // + TransportControlledHeaders.ruleFor(...)
internal const val MSG_DEFAULT_HEADER_SENSITIVE: String = "an Untrusted policy refuses a sensitive default header"
