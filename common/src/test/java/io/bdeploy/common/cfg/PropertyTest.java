/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.common.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cfg.Configuration.ConfigurationNameMapping;

class PropertyTest {

    @Test
    void testProperties() {
        String value = System.getProperty("user.home");

        Configuration c = new Configuration();
        c.add(System.getProperties());

        TestConfig tc = c.get(TestConfig.class);

        assertEquals(value, tc.userHome());
    }

    private @interface TestConfig {

        @ConfigurationNameMapping("user.home")
        String userHome();
    }

}
