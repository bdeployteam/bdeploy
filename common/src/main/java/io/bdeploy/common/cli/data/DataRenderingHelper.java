package io.bdeploy.common.cli.data;

import java.util.regex.Pattern;

public class DataRenderingHelper {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[ _-]");
    private static final Pattern REPLACE_PATTERN = Pattern.compile("[^a-zA-Z0-9]");

    private DataRenderingHelper() {
    }

    public static String quoteCsv(String input) {
        return "\"" + input.replace("\"", "\"\"") + "\"";
    }

    public static String quoteJson(String input) {
        return "\"" + input.replace("\"", "\\\"").replace("\n", "\\n").replace("\\", "\\\\") + "\"";
    }

    public static String calculateName(String input) {
        final StringBuilder ret = new StringBuilder(input.length());
        for (String word : SPLIT_PATTERN.split(input)) {
            word = REPLACE_PATTERN.matcher(word).replaceAll("");
            if (!word.isEmpty()) {
                ret.append(Character.toUpperCase(word.charAt(0)));
                ret.append(word.substring(1).toLowerCase());
            }
        }
        return ret.toString();
    }
}
