package io.bdeploy.interfaces.report;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ReportColumn is used to describe how to structure the report response
 */
public class ReportColumnDescriptor {

    /**
     * column name to display to the user
     */
    public final String name;

    /**
     * key to extract column data from row
     */
    public final String key;

    /**
     * whether this column should be displayed in the main table (Web UI)
     */
    public final boolean main;

    /**
     * whether this column's value is an ID.
     * identifiers could be copied to clipboard (Web UI) and are scaled to content (CLI)
     */
    public final boolean identifier;

    @JsonCreator
    public ReportColumnDescriptor(@JsonProperty("name") String name, @JsonProperty("key") String key,
            @JsonProperty("main") boolean main, @JsonProperty("identifier") boolean identifier) {
        this.name = name;
        this.key = key;
        this.main = main;
        this.identifier = identifier;
    }
}
