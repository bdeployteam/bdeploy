package io.bdeploy.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class URLishTest {

    @Test
    void fullUrlish() {
        URLish u = new URLish("scheme://host:1/path");

        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertEquals("1", u.port);
        assertEquals("/path", u.pathAndQuery);

        assertEquals("scheme://host:1/path", u.toString());
    }

    @Test
    void miniUrlish() {
        URLish u = new URLish("host");

        assertEquals(null, u.scheme);
        assertEquals("host", u.hostname);
        assertEquals(null, u.port);
        assertEquals(null, u.pathAndQuery);

        assertEquals("host", u.toString());
    }

    @Test
    void noSchemeUrlish() {
        URLish u = new URLish("host:1/path");

        assertEquals(null, u.scheme);
        assertEquals("host", u.hostname);
        assertEquals("1", u.port);
        assertEquals("/path", u.pathAndQuery);

        assertEquals("host:1/path", u.toString());
    }

    @Test
    void noPortUrlish() {
        URLish u = new URLish("scheme://host/path");

        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertEquals(null, u.port);
        assertEquals("/path", u.pathAndQuery);

        assertEquals("scheme://host/path", u.toString());
    }

    @Test
    void noPathUrlish() {
        URLish u = new URLish("scheme://host:1");

        assertEquals("scheme://", u.scheme);
        assertEquals("host", u.hostname);
        assertEquals("1", u.port);
        assertEquals(null, u.pathAndQuery);

        assertEquals("scheme://host:1", u.toString());
    }

    @Test
    void modifyUrlish() {
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
}
