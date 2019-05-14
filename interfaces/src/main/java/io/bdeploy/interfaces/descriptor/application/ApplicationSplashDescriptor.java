package io.bdeploy.interfaces.descriptor.application;

/**
 * Describes a splash screen for application branding (e.g. launcher).
 */
public class ApplicationSplashDescriptor {

    /**
     * Relative path to an image which is used as splash screen.
     * <p>
     * Currently only PNG format is supported.
     */
    public String image;

    /**
     * Area where to display status text on the splash screen.
     */
    public ApplicationSplashAreaDescriptor textRect;

    /**
     * Area where to display a progress bar on the splash screen.
     */
    public ApplicationSplashAreaDescriptor progressRect;

}
