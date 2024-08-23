package io.bdeploy.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.VersionHelper;

class VersionTest {

    @Test
    void testVersion() {
        Version v = VersionHelper.parse("1.2.3");

        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getMicro());
        assertNull(v.getQualifier());

        v = VersionHelper.parse("1.2.3-qualifier");

        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getMicro());
        assertEquals("-qualifier", v.getQualifier());

        v = VersionHelper.parse("1.2.3.qualifier");

        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getMicro());
        assertEquals(".qualifier", v.getQualifier());
    }

    @Test
    void testSorting() {
        SortedSet<Version> vs = new TreeSet<>();

        assertTrue(VersionHelper.compare("1.2.3", "1.0.0") > 0);
        assertTrue(VersionHelper.compare("1.2.3", "1.3.0") < 0);
        assertEquals(0, VersionHelper.compare("1.2.3", "1.2.3"));

        vs.add(VersionHelper.parse("1.2.3"));
        vs.add(VersionHelper.parse("1.0.1"));
        vs.add(VersionHelper.parse("1.0.1-qualifier2"));
        vs.add(VersionHelper.parse("1.0.1-qualifier1"));
        vs.add(VersionHelper.parse("1.2.1"));

        Version[] array = vs.stream().toArray(Version[]::new);

        assertEquals("1.0.1-qualifier1", array[0].toString());
        assertEquals("1.0.1-qualifier2", array[1].toString());
        assertEquals("1.0.1", array[2].toString());
        assertEquals("1.2.1", array[3].toString());
        assertEquals("1.2.3", array[4].toString());
    }

}
