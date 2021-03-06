package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.Map;

import io.bdeploy.common.util.ExceptionHelper;

public class DataResultJson extends DataResultBase {

    public DataResultJson(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        out().println("{");

        if (getThrowable() != null) {
            if (getMessage() != null) {
                out().println("  \"message\": " + DataTableJson.quote(getMessage()) + ",");
            }
            out().println("  \"error\": " + DataTableJson.quote(ExceptionHelper.mapExceptionCausesToReason(getThrowable())));
        } else {
            if (getMessage() != null) {
                out().print("  \"message\": " + DataTableJson.quote(getMessage()));
                out().println(getFields().size() > 0 ? "," : "");
            }

            int num = 0;
            for (Map.Entry<String, String> entry : getFields().entrySet()) {
                out().print("  " + DataTableJson.quote(DataTableColumn.calculateName(entry.getKey())) + ": "
                        + DataTableJson.quote(entry.getValue()));

                if (num++ != getFields().size() - 1) {
                    out().println(",");
                } else {
                    out().println();
                }
            }
        }

        out().println("}");
    }

}
