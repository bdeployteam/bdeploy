package io.bdeploy.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.security.ScopedPermission.Permission;

public class ScopedPermissionTests {

    @Test
    public void testGlobalRead() {
        ScopedPermission permission = new ScopedPermission(Permission.READ);

        // Global read is allowed. Nothing else
        assertTrue(permission.satisfies(new ScopedPermission(Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.ADMIN)));

        // Scoped read is allowed. Nothing else
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("c", Permission.ADMIN)));
    }

    @Test
    public void testGlobalWrite() {
        ScopedPermission permission = new ScopedPermission(Permission.WRITE);

        // Global read,write is allowed. Nothing else
        assertTrue(permission.satisfies(new ScopedPermission(Permission.READ)));
        assertTrue(permission.satisfies(new ScopedPermission(Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.ADMIN)));

        // Scoped read,write is allowed. Nothing else
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.READ)));
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("a", Permission.ADMIN)));
    }

    @Test
    public void testGlobalAdmin() {
        ScopedPermission permission = new ScopedPermission(Permission.ADMIN);

        // Everything is allowed
        assertTrue(permission.satisfies(new ScopedPermission(Permission.READ)));
        assertTrue(permission.satisfies(new ScopedPermission(Permission.WRITE)));
        assertTrue(permission.satisfies(new ScopedPermission(Permission.ADMIN)));

        // Everything is allowed
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.READ)));
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.WRITE)));
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.ADMIN)));
    }

    @Test
    public void testScopedRead() {
        ScopedPermission permission = new ScopedPermission("a", Permission.READ);

        // Global nothing is allowed
        assertFalse(permission.satisfies(new ScopedPermission(Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.ADMIN)));

        // Scoped read is allowed for my scope
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission("a", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("a", Permission.ADMIN)));

        // All other scopes are not allowed
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.ADMIN)));
    }

    @Test
    public void testScopedWrite() {
        ScopedPermission permission = new ScopedPermission("a", Permission.WRITE);

        // Global nothing is allowed
        assertFalse(permission.satisfies(new ScopedPermission(Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.ADMIN)));

        // Scoped read,write is allowed for my scope
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.READ)));
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("a", Permission.ADMIN)));

        // All other scopes are not allowed
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.ADMIN)));
    }

    @Test
    public void testScopedAdmin() {
        ScopedPermission permission = new ScopedPermission("a", Permission.ADMIN);

        // Global nothing is allowed
        assertFalse(permission.satisfies(new ScopedPermission(Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission(Permission.ADMIN)));

        // Scoped everything is allowed for my scope
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.READ)));
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.WRITE)));
        assertTrue(permission.satisfies(new ScopedPermission("a", Permission.ADMIN)));

        // All other scopes are not allowed
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.READ)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.WRITE)));
        assertFalse(permission.satisfies(new ScopedPermission("b", Permission.ADMIN)));
    }

}
