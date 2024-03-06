package io.bdeploy.minion.mail;

import java.nio.file.Path;

import org.apache.commons.io.FilenameUtils;

/**
 * Contains utiliy methods for mail attachment handling.
 */
public class AttachmentUtils {

    private static final String HASH = "#";

    private AttachmentUtils() {
    }

    /**
     * Builds the name of an attachment
     *
     * @return The name of the attachment (excluding the file extension)
     */
    public static String getAttachmentNameFromData(String name, String instanceId, String managedServerName) {
        return name + HASH + instanceId + HASH + managedServerName;
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
     * <tr>
     * <td>[2]</td>
     * <td>server name</td>
     * </tr>
     * </table>
     *
     * @param path The {@link Path} to the file
     * @return A {@link String}-array which contains the individual parts
     */
    public static String[] getAttachmentDataFromName(Path path) {
        String fileName = path.getFileName().toString();
        String[] split = FilenameUtils.removeExtension(fileName).split(HASH);
        if (split.length != 3) {
            throw new IllegalArgumentException("Filename " + fileName + " could not be parsed.");
        }
        return split;
    }
}
