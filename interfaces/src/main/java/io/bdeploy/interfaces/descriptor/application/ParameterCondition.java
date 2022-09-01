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
     * The referenced parameter (ID). Use {@link #expression} instead if you need more complex expressions or don't have a
     * parameter to reference.
     */
    public String parameter;

    /**
     * A link expression containing an arbitrary number of expansions. This will be resolved and used as value.
     * <p>
     * This can be used <b>instead</b> of {@link #parameter}.
     */
    public String expression;

    /**
     * The condition type.
     */
    public ParameterConditionType must;

    /**
     * The value matched against. Depending on the type of condition this value is ignored.
     */
    public String value;

}
