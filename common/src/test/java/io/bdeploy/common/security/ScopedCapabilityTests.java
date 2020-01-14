package io.bdeploy.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.security.ScopedCapability.Capability;

public class ScopedCapabilityTests {

    @Test
    public void testGlobalRead() {
        ScopedCapability capability = new ScopedCapability(Capability.READ);

        // Global read is allowed. Nothing else
        assertTrue(capability.satisfies(new ScopedCapability(Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.ADMIN)));

        // Scoped read is allowed. Nothing else
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("c", Capability.ADMIN)));
    }

    @Test
    public void testGlobalWrite() {
        ScopedCapability capability = new ScopedCapability(Capability.WRITE);

        // Global read,write is allowed. Nothing else
        assertTrue(capability.satisfies(new ScopedCapability(Capability.READ)));
        assertTrue(capability.satisfies(new ScopedCapability(Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.ADMIN)));

        // Scoped read,write is allowed. Nothing else
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.READ)));
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("a", Capability.ADMIN)));
    }

    @Test
    public void testGlobalAdmin() {
        ScopedCapability capability = new ScopedCapability(Capability.ADMIN);

        // Everything is allowed
        assertTrue(capability.satisfies(new ScopedCapability(Capability.READ)));
        assertTrue(capability.satisfies(new ScopedCapability(Capability.WRITE)));
        assertTrue(capability.satisfies(new ScopedCapability(Capability.ADMIN)));

        // Everything is allowed
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.READ)));
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.WRITE)));
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.ADMIN)));
    }

    @Test
    public void testScopedRead() {
        ScopedCapability capability = new ScopedCapability("a", Capability.READ);

        // Global nothing is allowed
        assertFalse(capability.satisfies(new ScopedCapability(Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.ADMIN)));

        // Scoped read is allowed for my scope
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability("a", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("a", Capability.ADMIN)));

        // All other scopes are not allowed
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.ADMIN)));
    }

    @Test
    public void testScopedWrite() {
        ScopedCapability capability = new ScopedCapability("a", Capability.WRITE);

        // Global nothing is allowed
        assertFalse(capability.satisfies(new ScopedCapability(Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.ADMIN)));

        // Scoped read,write is allowed for my scope
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.READ)));
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("a", Capability.ADMIN)));

        // All other scopes are not allowed
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.ADMIN)));
    }

    @Test
    public void testScopedAdmin() {
        ScopedCapability capability = new ScopedCapability("a", Capability.ADMIN);

        // Global nothing is allowed
        assertFalse(capability.satisfies(new ScopedCapability(Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability(Capability.ADMIN)));

        // Scoped everything is allowed for my scope
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.READ)));
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.WRITE)));
        assertTrue(capability.satisfies(new ScopedCapability("a", Capability.ADMIN)));

        // All other scopes are not allowed
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.READ)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.WRITE)));
        assertFalse(capability.satisfies(new ScopedCapability("b", Capability.ADMIN)));
    }

}
