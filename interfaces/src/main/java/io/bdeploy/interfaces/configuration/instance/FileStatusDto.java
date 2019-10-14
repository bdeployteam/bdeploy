package io.bdeploy.interfaces.configuration.instance;

/**
 * Represents a file update.
 */
public class FileStatusDto {

    /**
     * Describes the type of update
     */
    public enum FileStatusType {
        ADD,
        EDIT,
        DELETE
    }

    /**
     * The type of update which happened to the file.
     */
    public FileStatusType type;

    /**
     * The name (path) of the updated file.
     */
    public String file;

    /**
     * The updated content of the file if applicable. The content is always base64 encoded.
     */
    public String content;

}
