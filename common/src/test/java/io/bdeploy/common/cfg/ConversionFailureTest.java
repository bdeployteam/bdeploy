package io.bdeploy.common.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

class ConversionFailureTest {

    @Test
    void testStringParameter() {
        Configuration c = new Configuration();
        c.add("--instanceGroup");

        ConfigValidationException e = assertThrowsExactly(ConfigValidationException.class, () -> c.get(TestStringConfig.class));
        assertEquals("Validation Issues Detected", e.getMessage());
        assertEquals(
                "Could not resolve instanceGroup parameter. Please specify parameter like this: instanceGroup=<value>. Illegal conversion from non-string object to different type: class java.lang.Boolean to class java.lang.String",
                e.getSuppressed()[0].getMessage());
    }

    @Test
    void testArrayParameter() {
        Configuration c = new Configuration();
        c.add("--instanceGroup");

        ConfigValidationException e = assertThrowsExactly(ConfigValidationException.class, () -> c.get(TestArrayConfig.class));
        assertEquals("Validation Issues Detected", e.getMessage());
        assertEquals(
                "Could not resolve instanceGroup parameter. Please specify parameter like this: instanceGroup=<value1> instanceGroup=<value2>... or like this: instanceGroup=<value1>,<value2>... Illegal conversion from non-string object to different type: class java.lang.Boolean to class [Ljava.lang.String;",
                e.getSuppressed()[0].getMessage());
    }

    private @interface TestStringConfig {

        String instanceGroup();

    }

    private @interface TestArrayConfig {

        String[] instanceGroup();

    }
}
