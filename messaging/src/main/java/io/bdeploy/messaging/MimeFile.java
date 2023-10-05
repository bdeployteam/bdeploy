package io.bdeploy.messaging;

/**
 * Holds data about a file and its mime type.
 */
public class MimeFile {

    private final String name;
    private final byte[] content;
    private final String mimeType;

    /**
     * Creates a new {@link MimeFile}.
     *
     * @param name The filename of the {@link MimeFile}
     * @param content The content of the {@link MimeFile} as a byte-array
     * @param mimeType The mime type of the {@link MimeFile}
     */
    public MimeFile(String name, byte[] content, String mimeType) {
        this.name = name;
        this.content = content;
        this.mimeType = mimeType;
    }

    /**
     * @return The name of the {@link MimeFile}
     */
    public String getName() {
        return name;
    }

    /**
     * @return The content of the {@link MimeFile}
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * @return The mime type of the {@link MimeFile}
     */
    public String getMimeType() {
        return mimeType;
    }
}
