package io.bdeploy.common.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

public class ConversionFailureTest {

    @Test
    void testStringParameter() {
        Configuration c = new Configuration();
        c.add("--instanceGroup");

        ConfigValidationException e = assertThrowsExactly(ConfigValidationException.class, () -> c.get(TestStringConfig.class));
        assertEquals(e.getMessage(), "Validation Issues Detected");
        assertEquals(e.getSuppressed()[0].getMessage(),
                "Could not resolve instanceGroup parameter. Please specify parameter like this: instanceGroup=<value>. Illegal conversion from non-string object to different type: class java.lang.Boolean to class java.lang.String");
    }

    @Test
    void testArrayParameter() {
        Configuration c = new Configuration();
        c.add("--instanceGroup");

        ConfigValidationException e = assertThrowsExactly(ConfigValidationException.class, () -> c.get(TestArrayConfig.class));
        assertEquals(e.getMessage(), "Validation Issues Detected");
        assertEquals(e.getSuppressed()[0].getMessage(),
                "Could not resolve instanceGroup parameter. Please specify parameter like this: instanceGroup=<value1> instanceGroup=<value2>... or like this: instanceGroup=<value1>,<value2>... Illegal conversion from non-string object to different type: class java.lang.Boolean to class [Ljava.lang.String;");
    }

    private @interface TestStringConfig {

        String instanceGroup();

    }

    private @interface TestArrayConfig {

        String[] instanceGroup();

    }
}
