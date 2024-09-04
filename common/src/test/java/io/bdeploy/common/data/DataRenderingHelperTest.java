package io.bdeploy.common.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DateFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataRenderingHelper;

class DataRenderingHelperTest {

    @Test
    void testCsvQuoting() {
        assertEquals("\"foo\"", DataRenderingHelper.quoteCsv("foo"));
        assertEquals("\"foo bar\"", DataRenderingHelper.quoteCsv("foo bar"));
        assertEquals("\"start \"\"quote\"\" end\"", DataRenderingHelper.quoteCsv("start \"quote\" end"));
        assertEquals("\"start \"\"\"\"doublequote\"\"\"\" end\"", DataRenderingHelper.quoteCsv("start \"\"doublequote\"\" end"));
    }

    @Test
    void testJsonQuoting() {
        assertEquals("\"foo\"", DataRenderingHelper.quoteJson("foo"));
        assertEquals("\"foo bar\"", DataRenderingHelper.quoteJson("foo bar"));
        assertEquals("\"new\\\\nline\"", DataRenderingHelper.quoteJson("new\nline"));
        assertEquals("\"start \\\\\"quote\\\\\" end\"", DataRenderingHelper.quoteJson("start \"quote\" end"));
        assertEquals("\"s \\\\\"\\\\\"doublequote\\\\\"\\\\\" e\"", DataRenderingHelper.quoteJson("s \"\"doublequote\"\" e"));
    }

    @Test
    void testNameCalculation() {
        for (Locale l : DateFormat.getAvailableLocales()) {
            Set<String> testStrings1 = getTestStrings(l, "");
            testStrings1.addAll(getTestStrings(l, " "));
            testStrings1.addAll(getTestStrings(l, "  "));
            testStrings1.addAll(getTestStrings(l, "  "));
            testStrings1.addAll(getTestStrings(l, "§$%&%/&)() §$%&%/&)()     §$%&%/&)() __# -- §$%&%/&)()"));
            assertAllResults("", testStrings1);

            Set<String> testStrings2 = getTestStrings(l, "x");
            testStrings2.addAll(getTestStrings(l, "  x  "));
            testStrings2.addAll(getTestStrings(l, "?  x  ?"));
            assertAllResults("X", testStrings2);

            Set<String> testStrings3 = getTestStrings(l, "foo");
            testStrings3.add("FOo");
            testStrings3.add("fOO");
            testStrings3.add("FOO");
            assertAllResults("Foo", testStrings3);

            Set<String> testStrings4 = getTestStrings(l, "foo bar");
            testStrings4.add("FOo bar");
            testStrings4.add("fOO bar");
            testStrings4.add("FOO bar");
            testStrings4.add("FOo BAr");
            testStrings4.add("fOO BAr");
            testStrings4.add("FOO BAr");
            testStrings4.add("FOo bAR");
            testStrings4.add("fOO bAR");
            testStrings4.add("FOO bAR");
            testStrings4.add("FOo BAR");
            testStrings4.add("fOO BAR");
            testStrings4.add("FOO BAR");
            testStrings4.addAll(getTestStrings(l, "foo_bar"));
            testStrings4.add("FOo_bar");
            testStrings4.add("fOO_bar");
            testStrings4.add("FOO_bar");
            testStrings4.add("FOo_BAr");
            testStrings4.add("fOO_BAr");
            testStrings4.add("FOO_BAr");
            testStrings4.add("FOo_bAR");
            testStrings4.add("fOO_bAR");
            testStrings4.add("FOO_bAR");
            testStrings4.add("FOo_BAR");
            testStrings4.add("fOO_BAR");
            testStrings4.add("FOO_BAR");
            testStrings4.addAll(getTestStrings(l, "foo-bar"));
            testStrings4.add("FOo-bar");
            testStrings4.add("fOO-bar");
            testStrings4.add("FOO-bar");
            testStrings4.add("FOo-BAr");
            testStrings4.add("fOO-BAr");
            testStrings4.add("FOO-BAr");
            testStrings4.add("FOo-bAR");
            testStrings4.add("fOO-bAR");
            testStrings4.add("FOO-bAR");
            testStrings4.add("FOo-BAR");
            testStrings4.add("fOO-BAR");
            testStrings4.add("FOO-BAR");
            assertAllResults("FooBar", testStrings4);

            Set<String> testStrings5 = getTestStrings(l, "test/foo\\bar[²²12$3 ^´äEND");
            assertAllResults("Testfoobar123End", testStrings5);
        }
    }

    private static void assertAllResults(String expected, Collection<String> inputs) {
        for (String s : inputs) {
            assertEquals(expected, DataRenderingHelper.calculateName(s));
        }
    }

    private static Set<String> getTestStrings(Locale l, String s) {
        char[] charArray = s.toCharArray();

        Set<String> result = new HashSet<>();
        result.add(s);
        for (int i = 0; i < charArray.length; i++) {
            char[] clone = charArray.clone();
            clone[i] = String.valueOf(clone[i]).toLowerCase(l).toCharArray()[0];
            result.add(String.valueOf(clone));
            clone[i] = String.valueOf(clone[i]).toUpperCase(l).toCharArray()[0];
            result.add(String.valueOf(clone));
        }
        return result;
    }
}