package io.bdeploy.common.cli.data;

import java.util.regex.Pattern;

public class DataRenderingHelper {

    private static final String WORD_SEPARATORS = " _-";
    private static final Pattern SPLIT_PATTERN = Pattern.compile('[' + WORD_SEPARATORS + ']');
    private static final Pattern REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9" + WORD_SEPARATORS + ']');

    private DataRenderingHelper() {
    }

    public static String quoteCsv(String input) {
        return "\"" + input.replace("\"", "\"\"") + "\"";
    }

    public static String quoteJson(String input) {
        return "\"" + input.replace("\"", "\\\"").replace("\n", "\\n").replace("\\", "\\\\") + "\"";
    }

    /**
     * Takes all words of the given input and parses them to CamelCase. A word is defined to be any string of characters that is
     * encapsulated by either " ", "_" or "-".<br>
     * The returned {@link String} will only contain numbers and letters - all other characters are being removed.
     *
     * @param input The input to parse
     * @return The parsed {@link String}
     */
    public static String calculateName(String input) {
        final StringBuilder ret = new StringBuilder(input.length());
        input = REPLACE_PATTERN.matcher(input).replaceAll("");
        for (String word : SPLIT_PATTERN.split(input)) {
            if (!word.isEmpty()) {
                ret.append(Character.toUpperCase(word.charAt(0)));
                ret.append(word.substring(1).toLowerCase());
            }
        }
        return ret.toString();
    }
}
