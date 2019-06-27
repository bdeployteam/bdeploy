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

import org.junit.jupiter.api.Test;

public class ConversionTest {

    @Test
    public void testConversion() {
        Configuration c = new Configuration();

        c.add("--testByte=3", "--testChar=a", "--testInt=9", "--testShort=8", "--testLong=7", "--testFloat=0.9",
                "--testDouble=0.9", "--testEnum=TEST1", "--testString=abc", "--testStringArray=abc,def,ghi",
                "--testLongArray=1,2,3,4,5", "--testBoolean");

        TestConfig tc = c.get(TestConfig.class);
        assertEquals(3, tc.testByte());
        assertEquals('a', tc.testChar());
        assertEquals(9, tc.testInt());
        assertEquals(8, tc.testShort());
        assertEquals(7, tc.testLong());
        assertEquals(0.9f, tc.testFloat());
        assertEquals(0.9, tc.testDouble());
        assertEquals(TestEnum.TEST1, tc.testEnum());
        assertEquals("abc", tc.testString());
        assertEquals(3, tc.testStringArray().length);
        assertEquals("abc", tc.testStringArray()[0]);
        assertEquals("def", tc.testStringArray()[1]);
        assertEquals("ghi", tc.testStringArray()[2]);
        assertEquals(5, tc.testLongArray().length);
        assertEquals(1, tc.testLongArray()[0]);
        assertEquals(2, tc.testLongArray()[1]);
        assertEquals(3, tc.testLongArray()[2]);
        assertEquals(4, tc.testLongArray()[3]);
        assertEquals(5, tc.testLongArray()[4]);
    }

    @Test
    public void testMultiAdd() {
        Configuration c = new Configuration();
        c.add("--testStringArray=1");
        c.add("--testStringArray=2");
        c.add("--testStringArray=3");
        c.add("--testStringArray=4");

        TestConfig tc = c.get(TestConfig.class);

        assertEquals(4, tc.testStringArray().length);
        assertEquals("1", tc.testStringArray()[0]);
        assertEquals("2", tc.testStringArray()[1]);
        assertEquals("3", tc.testStringArray()[2]);
        assertEquals("4", tc.testStringArray()[3]);
    }

    @Test
    public void testEqualsSign() {
        Configuration c = new Configuration();
        c.add("--testStringArray=this=test");
        c.add("--testStringArray=some=other");

        TestConfig tc = c.get(TestConfig.class);

        assertEquals(2, tc.testStringArray().length);
        assertEquals("this=test", tc.testStringArray()[0]);
        assertEquals("some=other", tc.testStringArray()[1]);
    }

    private @interface TestConfig {

        byte testByte() default 0x1;

        char testChar() default 'z';

        int testInt() default 2;

        short testShort() default 3;

        long testLong() default 4;

        float testFloat() default 0.5f;

        double testDouble() default 0.55555;

        boolean testBoolean() default false;

        TestEnum testEnum() default TestEnum.TEST2;

        String testString() default "Test";

        String[] testStringArray() default {};

        long[] testLongArray() default {};
    }

    public enum TestEnum {
        TEST1,
        TEST2
    }

}
