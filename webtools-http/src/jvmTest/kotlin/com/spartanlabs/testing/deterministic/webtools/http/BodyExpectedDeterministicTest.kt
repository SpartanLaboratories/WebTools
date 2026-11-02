package com.spartanlabs.testing.deterministic.webtools.http

import com.spartanlabs.webtools.http.HttpMethod
import com.spartanlabs.webtools.http.bodyExpected
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Level 4a - the `bodyExpected` table over `HEAD`, `GET` and `POST` and the statuses `100, 101, 199, 200, 204, 205, 304, 404`: `HEAD` never
 * reads a body; a `1xx`, a `204` and a `304` read none for any method; every other status does.
 */
class BodyExpectedDeterministicTest {

    private val statuses = listOf(100, 101, 199, 200, 204, 205, 304, 404)

    @Test
    fun `HEAD never reads a body`() {
        statuses.forEach { assertFalse(bodyExpected(HttpMethod.Head, it), "HEAD $it") }
    }

    @Test
    fun `GET and POST read a body unless the status is a 1xx a 204 or a 304`() {
        listOf(HttpMethod.Get, HttpMethod.Post).forEach { method ->
            assertEquals(listOf(false, false, false, true, false, true, false, true), statuses.map { bodyExpected(method, it) }, method.name)
        }
    }
}
