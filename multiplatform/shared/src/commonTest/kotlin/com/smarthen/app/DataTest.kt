package com.smarthen.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DataTest {
    @Test fun cacheSeparatesServerAndHouse() {
        assertNotEquals(sensorCacheKey("https://a.example", "1"), sensorCacheKey("https://b.example", "1"))
        assertNotEquals(sensorCacheKey("https://a.example", "1"), sensorCacheKey("https://a.example", "2"))
        assertNotEquals(sensorCacheKey("http://a.example", "1"), sensorCacheKey("https://a.example", "1"))
        assertEquals(sensorCacheKey("https://a.example/", "1"), sensorCacheKey("https://a.example", "1"))
    }

    @Test fun houseIdentifierCannotInjectAnotherPathOrQuery() {
        val url = sensorUrl("https://example.com/api", "a/b?x=1#fragment")
        assertTrue(url.startsWith("https://example.com/api/sensors/"), url)
        assertTrue("a%2Fb" in url, url)
        assertTrue("?" !in url && "#" !in url, url)
        assertFailsWith<IllegalArgumentException> { sensorUrl("https://example.com", "..") }
        assertFailsWith<IllegalArgumentException> { sensorUrl("https://example.com", " ") }
    }

    @Test fun rejectsAmbiguousOrCredentialBearingConfiguration() {
        for (url in listOf("example.com", "ftp://example.com", "https://example.com?", "https://example.com#", "https://user:secret@example.com")) {
            assertFailsWith<IllegalArgumentException>(url) { validatedServer(url) }
        }
        assertEquals("127.0.0.1", validatedServer("http://127.0.0.1:8000").host)
    }

    @Test fun preservesUnknownTypesAndMissingFreshness() {
        val sensor = decodeSensors("""[{"id":"s1","type":"FUTURE_SENSOR","name":"Test","unit":"x","current_value":1.5,"extra":true}]""").single()
        assertEquals("FUTURE_SENSOR", sensor.type)
        assertEquals(0L, sensor.last_seen)
        assertEquals(false, sensor.is_online)
    }

    @Test fun malformedAndDuplicateDataRejected() {
        assertFailsWith<Exception> { decodeSensors("not JSON") }
        val reading = """{"id":"s1","type":"TEMP","name":"Test","unit":"C","current_value":1.5}"""
        assertFailsWith<IllegalArgumentException> { decodeSensors("[$reading,$reading]") }
    }
}
