package io.bdeploy.minion.mail;

import java.nio.file.Path;

import org.apache.commons.io.FilenameUtils;

/**
 * Contains utiliy methods for mail attachment handling.
 */
public class AttachmentUtils {

    private static final String HASH = "#";

    /**
     * Builds the name of an attachment
     *
     * @return The name of the attachment (excluding the file extension)
     */
    public static String getAttachmentNameFromData(String name, String instanceId) {
        return name + HASH + instanceId;
    }

    /**
     * Splits the name of an attachment up into its parts.
     * <p>
     * <table border>
     * <tr>
     * <th>Index</th>
     * <th>Data</th>
     * </tr>
     * <tr>
     * <td>[0]</td>
     * <td>name</td>
     * </tr>
     * <tr>
     * <td>[1]</td>
     * <td>instance id</td>
     * </tr>
     * </table>
     *
     * @param path The path to the file
     */
    public static String[] getAttachmentDataFromName(Path path) {
        String[] split = FilenameUtils.removeExtension(path.getFileName().toString()).split(HASH);
        if (split.length != 2) {
            throw new IllegalArgumentException("Filename " + path.toString() + " could not be parsed.");
        }
        return split;
    }
}
