package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.bdeploy.bhive.model.Manifest;

class ManifestVariableResolverTest {

    @Test
    void testManifestVariableResolver() {
        Path path1 = Path.of("path1");
        Path path2 = Path.of("path2");
        Path path3 = Path.of("path3");

        String path1String = path1.toString();
        String path2String = path2.toString();
        String path3String = path3.toString();

        Map<Manifest.Key, Path> paths = new HashMap<>();
        paths.put(new Manifest.Key("name1", "tag1"), path1);
        paths.put(new Manifest.Key("name2", "tag2"), path2);
        paths.put(new Manifest.Key("name3", "tag3"), path3);
        paths.put(new Manifest.Key("name4", "tagA"), path3);
        paths.put(new Manifest.Key("name4", "tagB"), path3);

        var pathProvider = new ManifestRefPathProvider(paths);
        var resolver = new ManifestVariableResolver(pathProvider);

        // Test successful retrieval with full stringified key
        assertEquals(path1String, resolver.doResolve("name1:tag1"));
        assertEquals(path2String, resolver.doResolve("name2:tag2"));
        assertEquals(path3String, resolver.doResolve("name3:tag3"));

        // Test unsuccessful retrieval with full stringified key
        assertNull(resolver.doResolve("name0:tag1"));
        assertNull(resolver.doResolve("name0:tag2"));
        assertNull(resolver.doResolve("name0:tag3"));
        assertNull(resolver.doResolve("name1:tag0"));
        assertNull(resolver.doResolve("name2:tag0"));
        assertNull(resolver.doResolve("name3:tag0"));
        assertNull(resolver.doResolve("unknown:tag"));

        // Test successful retrieval with only the name
        assertEquals(path1String, resolver.doResolve("name1"));
        assertEquals(path2String, resolver.doResolve("name2"));
        assertEquals(path3String, resolver.doResolve("name3"));

        // Test unsuccessful retrieval with only the name
        assertNull(resolver.doResolve("name0"));
        assertNull(resolver.doResolve("unknown"));

        // Test retrieval of duplicate names
        assertEquals(path3String, resolver.doResolve("name4:tagA"));
        assertEquals(path3String, resolver.doResolve("name4:tagA"));
        assertThrows(IllegalStateException.class, () -> resolver.doResolve("name4"));
    }
}
