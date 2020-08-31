package io.bdeploy.common.cli.data;

import java.util.Map;

/**
 * Represents the result of a CLI tool.
 */
public interface DataResult extends RenderableResult {

    /**
     * @param message the "main" result message.
     */
    public DataResult setMessage(String message);

    /**
     * @param t the exception in case one happened.
     */
    public DataResult setException(Throwable t);

    /**
     * Adds a data field to the result. This can be any data "produced" by the tool, e.g. an ID generated by the tool, etc.
     *
     * @param name name of the field.
     * @param value the value of the field.
     */
    public DataResult addField(String name, Object value);

    /**
     * @return the fields in the result for testability
     */
    public Map<String, String> getFields();

    /**
     * @return the message for testability
     */
    public String getMessage();

}
