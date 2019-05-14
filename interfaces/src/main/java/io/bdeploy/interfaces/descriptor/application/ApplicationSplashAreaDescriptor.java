package io.bdeploy.interfaces.descriptor.application;

/**
 * A rectangle used to describe positioning for branding information.
 */
public class ApplicationSplashAreaDescriptor {

    /**
     * Offset from the left side of the splash screen image
     */
    public long x;

    /**
     * Offset from the top of the splash screen image
     */
    public long y;

    /**
     * Width of the area in which painting is allowed
     */
    public long width;

    /**
     * Height of the area in which painting is allowed
     */
    public long height;

    /**
     * Color code (e.g. "#000000") for the foreground color to use when painting on the area. Defaults to black
     */
    public String foreground;

}
