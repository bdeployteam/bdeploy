/*******************************************************************************
 *  Copyright (c) 2017 SSI Schaefer IT Solutions GmbH and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      SSI Schaefer IT Solutions GmbH
 *******************************************************************************/
package io.bdeploy.common.cfg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BasicTest {

    @Test
    public void testBasic() {
        Configuration c = new Configuration();
        c.add("--booleanArg", "--intArg=3");

        TestConfig tc = c.get(TestConfig.class);
        assertTrue(tc.booleanArg());
        assertEquals(3, tc.intArg());
    }

    @Test
    public void testDefault() {
        Configuration c = new Configuration();

        TestConfig tc = c.get(TestConfig.class);
        assertTrue(!tc.booleanArg());
        assertEquals(1, tc.intArg());
    }

    private @interface TestConfig {

        boolean booleanArg() default false;

        int intArg() default 1;
    }

    @Test
    public void testRaw() {
        Configuration c = new Configuration();
        c.add("--some", "--arg");
        assertTrue(c.getAllRawObjects().containsKey("some"));
        assertTrue(c.getAllRawObjects().containsKey("arg"));
    }

}
