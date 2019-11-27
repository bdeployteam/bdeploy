package io.bdeploy.ui.dto;

import io.bdeploy.bhive.model.Tree;

/**
 * Represents a single entry in the hive browser
 */
public class HiveEntryDto {

    public String id;
    public String mName;
    public String mTag;

    public String name;
    public Tree.EntryType type;

    public HiveEntryDto(String name, Tree.EntryType type) {
        this.name = name;
        this.type = type;
    }
}