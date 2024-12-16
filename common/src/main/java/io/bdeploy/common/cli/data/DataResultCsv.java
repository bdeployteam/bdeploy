package io.bdeploy.common.cli.data;

import static io.bdeploy.common.cli.data.DataRenderingHelper.CSV_DELIMITER;

import java.io.PrintStream;
import java.util.Map;

import io.bdeploy.common.util.ExceptionHelper;

class DataResultCsv extends DataResultBase {

    DataResultCsv(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        out().println("\"Result Field\"" + CSV_DELIMITER + "\"Result Value\"");
        if (getMessage() != null) {
            out().println("\"Message\"" + CSV_DELIMITER + DataRenderingHelper.quoteCsv(getMessage()));
        }

        if (getThrowable() != null) {
            out().println("\"Error\"" + CSV_DELIMITER
                    + DataRenderingHelper.quoteCsv(ExceptionHelper.mapExceptionCausesToReason(getThrowable())));
        } else {
            for (Map.Entry<String, String> entry : getFields().entrySet()) {
                out().println(DataRenderingHelper.quoteCsv(entry.getKey()) + CSV_DELIMITER
                        + DataRenderingHelper.quoteCsv(entry.getValue()));
            }
        }
    }

}
