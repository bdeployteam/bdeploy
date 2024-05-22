package io.bdeploy.common.cli.data;

public class DataRenderingHelper {

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
        for (String word : input.split("[ _-]")) {
            word = word.replaceAll("[^a-zA-Z0-9]", "");
            if (!word.isEmpty()) {
                ret.append(Character.toUpperCase(word.charAt(0)));
                ret.append(word.substring(1).toLowerCase());
            }
        }
        return ret.toString();
    }
}
