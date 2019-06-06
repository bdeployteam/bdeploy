package io.bdeploy.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class TemplateHelperTest {

    private static final String R = "RESOLVED";

    private static Map<String, String> VARS = new TreeMap<>();
    static {
        VARS.put("x", "RESOLVED");
        VARS.put("y-pattern1", "{{x}}");
        VARS.put("y-pattern2", "${x}");
    }

    private static Function<String, String> RESOLVER = s -> VARS.get(s);

    @Test
    void patterns() {

        assertEquals(R, TemplateHelper.process("{{x}}", RESOLVER, "{{", "}}"));
        assertEquals(R, TemplateHelper.process("${x}", RESOLVER, "${", "}"));

        assertEquals("|x|", TemplateHelper.process("|x|", RESOLVER, "${", "}"));
        assertEquals(R, TemplateHelper.process("|x|", RESOLVER, "|", "|"));

        assertEquals(R, TemplateHelper.process("{{y-pattern1}}", RESOLVER, "{{", "}}"));
        assertEquals(R, TemplateHelper.process("${y-pattern2}", RESOLVER, "${", "}"));

        assertEquals(R + R, TemplateHelper.process("${x}${y-pattern2}", RESOLVER, "${", "}"));
    }

}
