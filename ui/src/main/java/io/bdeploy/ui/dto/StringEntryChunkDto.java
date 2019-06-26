package io.bdeploy.ui.dto;

import java.nio.charset.StandardCharsets;

import io.bdeploy.interfaces.directory.EntryChunk;

/**
 * DTO to represent an {@link EntryChunk} for the frontend
 */
public class StringEntryChunkDto {

    public String content;

    public long endPointer;

    public StringEntryChunkDto(EntryChunk chunk) {
        this.content = new String(chunk.content, StandardCharsets.UTF_8);
        this.endPointer = chunk.endPointer;
    }

}
