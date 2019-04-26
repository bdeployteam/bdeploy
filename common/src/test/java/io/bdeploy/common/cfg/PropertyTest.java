/*
 * Copyright (c) SSI Schaefer IT Solutions
 */
package io.bdeploy.common.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cfg.Configuration;
import io.bdeploy.common.cfg.Configuration.ConfigurationNameMapping;

public class PropertyTest {

    @Test
    public void testProperties() {
        String value = System.getProperty("user.home");

        Configuration c = new Configuration();
        c.add(System.getProperties());

        TestConfig tc = c.get(TestConfig.class);

        assertEquals(tc.userHome(), value);
    }

    private @interface TestConfig {

        @ConfigurationNameMapping("user.home")
        String userHome();
    }

}
