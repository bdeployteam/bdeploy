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

    public long endPointer;

    public boolean binary;

    public StringEntryChunkDto(EntryChunk chunk) {
        this.content = new String(chunk.content, StandardCharsets.UTF_8);
        this.endPointer = chunk.endPointer;
        this.binary = isBinary(chunk.content);
    }

    @JsonCreator
    public StringEntryChunkDto(@JsonProperty("content") String content, @JsonProperty("endPointer") long endPointer,
            @JsonProperty("binary") boolean binary) {
        this.content = content;
        this.endPointer = endPointer;
        this.binary = binary;
    }

    private boolean isBinary(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

}
