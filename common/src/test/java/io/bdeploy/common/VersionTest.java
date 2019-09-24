package io.bdeploy.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

public class VersionTest {

    @Test
    void version() {
        Version v = Version.parse("1.2.3");

        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getMicro());
        assertNull(v.getQualifier());

        v = Version.parse("1.2.3-qualifier");

        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getMicro());
        assertEquals("-qualifier", v.getQualifier());

        v = Version.parse("1.2.3.qualifier");

        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getMicro());
        assertEquals(".qualifier", v.getQualifier());
    }

    @Test
    void sorting() {
        SortedSet<Version> vs = new TreeSet<>();

        assertTrue(Version.parse("1.2.3").compareTo(Version.parse("1.0.0")) > 0);
        assertTrue(Version.parse("1.2.3").compareTo(Version.parse("1.3.0")) < 0);
        assertTrue(Version.parse("1.2.3").compareTo(Version.parse("1.2.3")) == 0);

        vs.add(Version.parse("1.2.3"));
        vs.add(Version.parse("1.0.1"));
        vs.add(Version.parse("1.0.1-qualifier2"));
        vs.add(Version.parse("1.0.1-qualifier1"));
        vs.add(Version.parse("1.2.1"));

        Version[] array = vs.stream().toArray(Version[]::new);

        assertEquals("1.0.1-qualifier1", array[0].toString());
        assertEquals("1.0.1-qualifier2", array[1].toString());
        assertEquals("1.0.1", array[2].toString());
        assertEquals("1.2.1", array[3].toString());
        assertEquals("1.2.3", array[4].toString());
    }

}
