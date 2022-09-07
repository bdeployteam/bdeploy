package io.bdeploy.interfaces.configuration;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

/**
 * Allows overriding the default value of a templated (or any other) instance variable from the instance template.
 */
public class TemplateableVariableDefaultConfiguration {

    public String id;

    public LinkedValueConfiguration value;

}
