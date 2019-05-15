package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A rectangle used to describe positioning for branding information.
 */
public class ApplicationSplashAreaDescriptor {

    /**
     * Offset from the left side of the splash screen image
     */
    public int x;

    /**
     * Offset from the top of the splash screen image
     */
    public int y;

    /**
     * Width of the area in which painting is allowed
     */
    public int width;

    /**
     * Height of the area in which painting is allowed
     */
    public int height;

    /**
     * Color code (e.g. "#000000") for the foreground color to use when painting on the area. Defaults to black
     */
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
