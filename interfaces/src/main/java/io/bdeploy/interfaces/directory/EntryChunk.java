package io.bdeploy.interfaces.directory;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a chunk of a file.
 */
public class EntryChunk {

    public static final EntryChunk ROLLOVER_CHUNK = new EntryChunk("\n<file truncated>\n".getBytes(StandardCharsets.UTF_8), 0, 0);

    /**
     * The (potentially partial) content of the file.
     */
    public byte[] content;

    /**
     * byte offset into the file this chunk starts at
     */
    public long startPointer;

    /**
     * byte offset into the file this chunk stops at.
     */
    public long endPointer;

    @JsonCreator
    public EntryChunk(@JsonProperty("content") byte[] content, @JsonProperty("startPointer") long start,
            @JsonProperty("endPointer") long end) {
        this.content = content;
        this.startPointer = start;
        this.endPointer = end;
    }

}
