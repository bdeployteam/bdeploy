package io.bdeploy.common.util;

import java.util.Objects;

/**
 * Assertion helper for asserting in business logic.
 */
public class RuntimeAssert {

    public static void assertTrue(boolean condition, String msg) {
        if (!condition) {
            throw new IllegalStateException(msg);
        }
    }

    public static void assertNotNull(Object o, String msg) {
        assertTrue(o != null, msg);
    }

    public static void assertNotNull(Object o) {
        if (o == null) {
            throw new IllegalStateException();
        }
    }

    public static void assertFalse(boolean condition, String msg) {
        assertTrue(!condition, msg);
    }

    public static void assertEquals(Object a, Object b, String msg) {
        assertTrue(Objects.equals(a, b), msg);
    }

}
