package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

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
    @JsonPropertyDescription("Relative path (from app-info.yaml) to a .ico file which is used as client application icon.")
    public String icon;

}
