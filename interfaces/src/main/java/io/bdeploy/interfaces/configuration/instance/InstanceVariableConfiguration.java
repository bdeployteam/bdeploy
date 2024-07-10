package io.bdeploy.interfaces.configuration.instance;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

public class InstanceVariableConfiguration {

    /**
     * A globally unique ID of the variable
     */
    @JsonAlias("uid")
    public String id;

    /**
     * The actual value of the variable.
     */
    public LinkedValueConfiguration value;

}
