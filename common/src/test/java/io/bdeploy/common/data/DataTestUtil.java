package io.bdeploy.common.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import io.bdeploy.common.cli.data.DataFormat;

class DataTestUtil {

    private static final Pattern LINE_SPLIT_PATTERN = Pattern.compile("\\r?\\n");
    static final Charset CHARSET = StandardCharsets.UTF_8;
    final DataFormat dataFormat;

    DataTestUtil(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    static void assertEachLine(String expected, String actual) {
        String[] expectedLines = LINE_SPLIT_PATTERN.split(expected);
        String[] actualLines = LINE_SPLIT_PATTERN.split(actual);
        assertEquals(expectedLines.length, actualLines.length, "Expected and actual line counts do not match");
        for (int i = 0; i < expectedLines.length; i++) {
            assertEquals(expectedLines[i], actualLines[i], "Lines differ at row " + (i + 1));
        }
    }
}
