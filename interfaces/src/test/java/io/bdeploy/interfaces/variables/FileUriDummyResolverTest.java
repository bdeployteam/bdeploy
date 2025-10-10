package io.bdeploy.interfaces.variables;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

class FileUriDummyResolverTest {

    private static final VariableResolver PARENT1 = s -> s;
    private static final VariableResolver PARENT2 = String::toUpperCase;
    private static final VariableResolver PARENT3 = s -> "###" + s + "###";
    private static final FileUriDummyResolver resolver1 = new FileUriDummyResolver(PARENT1);
    private static final FileUriDummyResolver resolver2 = new FileUriDummyResolver(PARENT2);
    private static final FileUriDummyResolver resolver3 = new FileUriDummyResolver(PARENT3);
    private static final List<FileUriDummyResolver> resolvers = List.of(resolver1, resolver2, resolver3);

    @Test
    void testNullResolution() {
        resolvers.forEach(r -> assertNull(null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "a/b/c", "a\\b\\c", "a/b/c.txt", "a\\b\\c.txt", "a/b_b b/c.txt", "a\\b_b b\\c.txt", "<x>" })
    void testNonNullResolution(String s) {
        resolvers.forEach(r -> assertNotNull(r.doResolve(s)));
    }

    @Test
    void testDeploymentPaths() {
        SpecialDirectory[] dirs = DeploymentPathProvider.SpecialDirectory.values();
        resolvers.forEach(r -> Arrays.stream(dirs).forEach(dir -> assertNotNull(r.doResolve(dir.name()))));
    }
}
