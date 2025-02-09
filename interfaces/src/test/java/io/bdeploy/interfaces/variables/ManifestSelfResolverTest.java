package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.util.VariableResolver;

class ManifestSelfResolverTest {

    private static final String SELF = "SELF";

    @Test
    void testManifestSelfResolver() {
        VariableResolver parent1 = s -> s;
        VariableResolver parent2 = s -> s.toUpperCase();
        VariableResolver parent3 = s -> "###" + s + "###";

        var resolver1 = new ManifestSelfResolver(new Manifest.Key("name1", "tag1"), parent1);
        var resolver2 = new ManifestSelfResolver(new Manifest.Key("name2", "tag2"), parent2);
        var resolver3 = new ManifestSelfResolver(new Manifest.Key("name3", "tag3"), parent3);

        // Test no-op
        assertNull(resolver1.doResolve(""));
        assertNull(resolver2.doResolve(""));
        assertNull(resolver3.doResolve(""));
        assertNull(resolver1.doResolve(" "));
        assertNull(resolver2.doResolve(" "));
        assertNull(resolver3.doResolve(" "));
        assertNull(resolver1.doResolve("self"));
        assertNull(resolver2.doResolve("self"));
        assertNull(resolver3.doResolve("self"));

        // Test resolution
        assertEquals("M:name1:tag1", resolver1.doResolve(SELF));
        assertEquals("M:NAME2:TAG2", resolver2.doResolve(SELF));
        assertEquals("###M:name3:tag3###", resolver3.doResolve(SELF));
    }
}
