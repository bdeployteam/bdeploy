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
    public String name;

    /**
     * key to extract column data from row
     */
    public String key;

    /**
     * whether this column should be displayed in the main table (Web UI)
     */
    public boolean main;

    /**
     * minWidth for CLI table
     */
    public int minWidth;

    /**
     * scaleToContent for CLI table
     */
    public boolean scaleToContent;

    @JsonCreator
    public ReportColumnDescriptor(@JsonProperty("name") String name, @JsonProperty("key") String key,
            @JsonProperty("main") boolean main, @JsonProperty("minWidth") int minWidth,
            @JsonProperty("scaleToContent") boolean scaleToContent) {
        this.name = name;
        this.key = key;
        this.main = main;
        this.minWidth = minWidth;
        this.scaleToContent = scaleToContent;
    }
}
