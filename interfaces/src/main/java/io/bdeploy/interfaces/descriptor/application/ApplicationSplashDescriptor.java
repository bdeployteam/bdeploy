package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Describes a splash screen for application branding (e.g. launcher).
 */
@JsonClassDescription("Describes a splash screen for a client application.")
public class ApplicationSplashDescriptor {

    /**
     * Relative path to an image which is used as splash screen.
     * <p>
     * Supported formats are all that are accepted by AWT's <code>ImageIO.read(byte[])<code>. (BMP is fine :)).
     */
    @JsonPropertyDescription("A relative path (to app-info.yaml) to an image to be used as splash screen for the client application.")
    public String image;

    @JsonPropertyDescription("The area where text can be rendered on the splash screen.")
    public ApplicationSplashAreaDescriptor textRect;

    @JsonPropertyDescription("The area where a progress bar can be rendered on the splash screen.")
    public ApplicationSplashAreaDescriptor progressRect;

}
