package io.bdeploy.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class URLishTest {

    @Test
    void testFullUrlish() {
        URLish u = new URLish("scheme://host:1/path");

        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertEquals("1", u.port);
        assertEquals("/path", u.pathAndQuery);

        assertEquals("scheme://host:1/path", u.toString());
    }

    @Test
    void testMiniUrlish() {
        URLish u = new URLish("host");

        assertNull(u.scheme);
        assertEquals("host", u.hostname);
        assertNull(u.port);
        assertNull(u.pathAndQuery);

        assertEquals("host", u.toString());
    }

    @Test
    void testNoSchemeUrlish() {
        URLish u = new URLish("host:1/path");

        assertNull(u.scheme);
        assertEquals("host", u.hostname);
        assertEquals("1", u.port);
        assertEquals("/path", u.pathAndQuery);

        assertEquals("host:1/path", u.toString());
    }

    @Test
    void testNoPortUrlish() {
        URLish u = new URLish("scheme://host/path");

        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertNull(u.port);
        assertEquals("/path", u.pathAndQuery);

        assertEquals("scheme://host/path", u.toString());
    }

    @Test
    void testNoPathUrlish() {
        URLish u = new URLish("scheme://host:1");

        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertEquals("1", u.port);
        assertNull(u.pathAndQuery);

        assertEquals("scheme://host:1", u.toString());
    }

    @Test
    void testModifyUrlish() {
        URLish u = new URLish("scheme://host:1/path");
        assertEquals("scheme://host:1/path", u.toString());

        u.hostname = "bdeploy";
        u.pathAndQuery = "/some?query=value";

        assertEquals("scheme://", u.scheme);
        assertEquals("bdeploy", u.hostname);
        assertEquals("1", u.port);
        assertEquals("/some?query=value", u.pathAndQuery);

        assertEquals("scheme://bdeploy:1/some?query=value", u.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "1", "65535" })
    void testValidPortValues(String port) {
        String urlString = "scheme://host:" + port + "/path";
        URLish u = new URLish(urlString);
        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertEquals(port, u.port);
        assertEquals("/path", u.pathAndQuery);
        assertEquals(urlString, u.toString());
    }
}
