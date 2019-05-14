package io.bdeploy.interfaces.descriptor.application;

/**
 * Describes visual branding of an application.
 */
public class ApplicationBrandingDescriptor {

    /**
     * Describes the splash screen used by the client launcher application
     */
    public ApplicationSplashDescriptor splash = new ApplicationSplashDescriptor();

    /**
     * Relative path to an icon file (ICO format).
     */
    public String icon;

}
