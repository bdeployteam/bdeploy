package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.OsHelper.OperatingSystem;

class OsVariableResolverTest {

    @Test
    void testOsVariableResolver() {
        Set<String> knownOperatingSystemNames = Arrays.stream(OperatingSystem.values()).map(Enum::name)
                .collect(Collectors.toSet());
        long counter = 0;
        String certainlyUnknownValue;
        do {
            certainlyUnknownValue = String.valueOf(counter++);
        } while (knownOperatingSystemNames.contains(certainlyUnknownValue));

        var resolver = new OsVariableResolver();

        // We cannot know on what OS the test runs, so we can only check if unknown values are indeed ignored
        assertNull(resolver.apply(certainlyUnknownValue));
    }
}
