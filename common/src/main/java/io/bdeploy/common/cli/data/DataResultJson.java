package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.Map;

import io.bdeploy.common.util.ExceptionHelper;

class DataResultJson extends DataResultBase {

    DataResultJson(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        out().println("{");

        if (getThrowable() != null) {
            if (getMessage() != null) {
                out().println("  \"message\": " + DataRenderingHelper.quoteJson(getMessage()) + ",");
            }
            out().println(
                    "  \"error\": " + DataRenderingHelper.quoteJson(ExceptionHelper.mapExceptionCausesToReason(getThrowable())));
        } else {
            if (getMessage() != null) {
                out().print("  \"message\": " + DataRenderingHelper.quoteJson(getMessage()));
                out().println(getFields().size() > 0 ? "," : "");
            }

            int num = 0;
            for (Map.Entry<String, String> entry : getFields().entrySet()) {
                out().print("  " + DataRenderingHelper.quoteJson(DataRenderingHelper.calculateName(entry.getKey())) + ": "
                        + DataRenderingHelper.quoteJson(entry.getValue()));

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
