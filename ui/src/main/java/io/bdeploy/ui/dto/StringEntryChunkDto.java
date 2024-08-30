package io.bdeploy.ui.dto;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.directory.EntryChunk;

/**
 * DTO to represent an {@link EntryChunk} for the frontend
 */
public class StringEntryChunkDto {

    public String content;

    public byte[] binaryContent;

    public long endPointer;

    public boolean binary;

    public StringEntryChunkDto(EntryChunk chunk) {
        this.endPointer = chunk.endPointer;
        this.binary = isBinary(chunk.content);

        if (this.binary) {
            this.binaryContent = chunk.content;
        } else {
            this.content = new String(chunk.content, StandardCharsets.UTF_8);
        }
    }

    @JsonCreator
    public StringEntryChunkDto(@JsonProperty("content") String content, @JsonProperty("endPointer") long endPointer,
            @JsonProperty("binary") boolean binary) {
        this.content = content;
        this.endPointer = endPointer;
        this.binary = binary;
    }

    private static boolean isBinary(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

}
