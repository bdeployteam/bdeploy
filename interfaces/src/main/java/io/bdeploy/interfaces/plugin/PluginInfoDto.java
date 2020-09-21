package io.bdeploy.interfaces.plugin;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.api.plugin.v1.CustomEditor;
import io.bdeploy.api.plugin.v1.CustomProductVersionSorter;
import io.bdeploy.bhive.model.ObjectId;

/**
 * Describes a plugin to the UI.
 */
public class PluginInfoDto {

    public ObjectId id;
    public List<CustomEditor> editors;
    @JsonIgnore
    public CustomProductVersionSorter sorter;
    public String name;
    public String version;
    public boolean global;
    public boolean loaded;

    @JsonCreator
    public PluginInfoDto(@JsonProperty("id") ObjectId id, @JsonProperty("name") String name,
            @JsonProperty("version") String version, @JsonProperty("global") boolean global,
            @JsonProperty("loaded") boolean loaded, @JsonProperty("editors") List<CustomEditor> editors,
            @JsonProperty("sorter") CustomProductVersionSorter sorter) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.global = global;
        this.loaded = loaded;
        this.editors = editors;
        this.sorter = sorter;
    }

}
