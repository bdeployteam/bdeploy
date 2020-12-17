package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class DataResultBase implements DataResult {

    private String message;
    private Throwable throwable;
    private final Map<String, String> fields = new LinkedHashMap<>(); // preserve order.
    private final PrintStream output;

    public DataResultBase(PrintStream output) {
        this.output = output;
    }

    protected PrintStream out() {
        return output;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Map<String, String> getFields() {
        return fields;
    }

    protected Throwable getThrowable() {
        return throwable;
    }

    @Override
    public DataResult setMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    public DataResult setException(Throwable t) {
        this.throwable = t;
        return this;
    }

    @Override
    public DataResult addField(String name, Object value) {
        fields.put(name, value.toString());
        return this;
    }

}
