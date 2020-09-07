package io.bdeploy.interfaces.descriptor.application;

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

    /**
     * The referenced parameter (ID).
     */
    public String parameter;

    /**
     * The condition type.
     */
    public ParameterConditionType must;

    /**
     * The value matched against. Depending on the type of condition this value is ignored.
     */
    public String value;

}
