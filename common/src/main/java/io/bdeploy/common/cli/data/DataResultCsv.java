package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.Map;

import io.bdeploy.common.util.ExceptionHelper;

public class DataResultCsv extends DataResultBase {

    public DataResultCsv(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        out().println("\"Result Field\",\"Result Value\"");
        if (getMessage() != null) {
            out().println("\"Message\"," + DataTableCsv.quote(getMessage()));
        }

        if (getThrowable() != null) {
            out().println("\"Error\"," + DataTableCsv.quote(ExceptionHelper.mapExceptionCausesToReason(getThrowable())));
        } else {
            for (Map.Entry<String, String> entry : getFields().entrySet()) {
                out().println(DataTableCsv.quote(entry.getKey()) + "," + DataTableCsv.quote(entry.getValue()));
            }
        }
    }

}
