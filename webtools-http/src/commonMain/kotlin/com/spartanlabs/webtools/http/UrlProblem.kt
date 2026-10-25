package com.spartanlabs.webtools.http

/** The kinds of failure `Url.parse` and the encoders report, each with its constant message. */
internal enum class UrlProblem(val message: String) {
    Empty("The URL is empty."),
    Backslash("The URL contains a backslash."),
    WhitespaceOrControl("The URL contains whitespace or a control character."),
    NotUnicode("The URL contains text that is not valid Unicode."),
    Scheme("The URL must begin with http:// or https://."),
    UserInfo("The URL contains user-info."),
    HostEncoding("The URL host must be plain ASCII, without percent-encoding."),
    HostIdn("The URL host contains an internationalised (xn--) label."),
    Host("The URL host is not a valid host name or IP address literal."),
    Port("The URL port must be a number from 1 to 65535."),
    DotSegment("A path segment must not be \".\" or \"..\"."),
}

/** The failure `Url.parse` and the encoders return; its [problem] is what tests assert on. */
internal class UrlParseException(val problem: UrlProblem) : IllegalArgumentException(problem.message)

internal fun <T> urlFailure(problem: UrlProblem): Result<T> = Result.failure(UrlParseException(problem))
