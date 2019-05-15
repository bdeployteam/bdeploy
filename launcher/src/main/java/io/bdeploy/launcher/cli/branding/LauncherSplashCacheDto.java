package io.bdeploy.launcher.cli.branding;

import io.bdeploy.interfaces.descriptor.application.ApplicationSplashDescriptor;

/**
 * Used to cache the splash screen information on disc.
 */
public class LauncherSplashCacheDto {

    /**
     * The actual image data
     */
    public byte[] data;

    /**
     * The splash information (rectangles, ...)
     */
    public ApplicationSplashDescriptor spec;

}
