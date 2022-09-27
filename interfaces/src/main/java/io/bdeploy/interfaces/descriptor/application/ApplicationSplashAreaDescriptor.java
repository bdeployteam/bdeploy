package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A rectangle used to describe positioning for branding information.
 */
@JsonClassDescription("Defines an area in the provided splash image which can be used to render specific content.")
public class ApplicationSplashAreaDescriptor {

    @JsonPropertyDescription("The pixel offset from the left where the area starts")
    public int x;

    @JsonPropertyDescription("The pixel offset from the top where the area starts")
    public int y;

    @JsonPropertyDescription("The width of the drawable area")
    public int width;

    @JsonPropertyDescription("The height of the drawable area")
    public int height;

    @JsonPropertyDescription("The color with which content should be drawn in hex format, e.g. #0D0D0D")
    public String foreground;

    @JsonCreator
    public ApplicationSplashAreaDescriptor(@JsonProperty("x") int x, @JsonProperty("y") int y, @JsonProperty("width") int width,
            @JsonProperty("height") int height, @JsonProperty("foreground") String foreground) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.foreground = foreground;
    }

}
