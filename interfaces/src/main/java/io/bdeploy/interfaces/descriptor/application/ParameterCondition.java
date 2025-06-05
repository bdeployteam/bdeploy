package io.bdeploy.interfaces.descriptor.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A condition for a parameter
 */
public class ParameterCondition {

    /**
     * Types of conditions which can be set on parameters
     */
    public enum ParameterConditionType {
        EQUAL,
        CONTAIN,
        START_WITH,
        END_WITH,
        BE_EMPTY,
        BE_NON_EMPTY
    }

    @JsonPropertyDescription("The ID of a parameter which this condition is checked against. The parameter must be in the same application. Only either 'parameter' or 'expression' is allowed.")
    public String parameter;

    @JsonPropertyDescription("An arbitrary link expression. The expression is resolved and matched against the condition. Only either 'parameter' or 'expression' is allowed.")
    public String expression;

    @JsonPropertyDescription("The condition type, which specifies the relation between the parameter/expression and the passed value.")
    @JsonProperty(required = true)
    public ParameterConditionType must;

    @JsonPropertyDescription("The value matched against the parameter/expression in the way specified by the condition type ('must')")
    public String value;
}
