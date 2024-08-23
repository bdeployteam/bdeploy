package io.bdeploy.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

class TemplateHelperTest {

    private static final String R = "RESOLVED";

    private static final Map<String, String> VARS = new TreeMap<>();
    static {
        VARS.put("a", "RESOLVED");
        VARS.put("b", "{{a}}");
        VARS.put("c", "{{b}}");
        VARS.put("d", "{{c}}");
    }

    private static final VariableResolver RESOLVER = VARS::get;

    private static final ShouldResolve DELAY_RESOLVING_B = s -> {
        return !s.startsWith("b");
    };

    @Test
    void testResolveAllPatterns() {
        // Recursive replacement of patterns
        assertEquals(R, TemplateHelper.process("{{a}}", RESOLVER));
        assertEquals(R, TemplateHelper.process("{{b}}", RESOLVER));
        assertEquals(R, TemplateHelper.process("{{c}}", RESOLVER));

        // Resolve multiple patterns in one string
        assertEquals(R + "-" + R, TemplateHelper.process("{{a}}-{{c}}", RESOLVER));
        assertEquals("Text-with-patterns-" + R + "-" + R + "-and-some-more-text",
                TemplateHelper.process("Text-with-patterns-{{a}}-{{c}}-and-some-more-text", RESOLVER));

        // Resolve using a list of values
        assertEquals(Collections.singletonList(R), TemplateHelper.process(Collections.singletonList("{{a}}"), RESOLVER));
    }

    @Test
    void testResolveDelayedPatterns() {
        // Variable should be resolved as it is not delayed
        assertEquals(R, TemplateHelper.process("{{a}}", RESOLVER, DELAY_RESOLVING_B));

        // Variable should not be resolved as it is delayed
        assertEquals("{{b}}", TemplateHelper.process("{{b}}", RESOLVER, DELAY_RESOLVING_B));

        // Variable should be partly resolved until it is delayed
        assertEquals("{{b}}", TemplateHelper.process("{{c}}", RESOLVER, DELAY_RESOLVING_B));
    }

    @Test
    void testNotMatchingPattern() {
        assertEquals("|a|", TemplateHelper.process("|a|", RESOLVER));
    }
}
