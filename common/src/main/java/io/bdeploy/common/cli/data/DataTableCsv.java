package io.bdeploy.common.cli.data;

import static io.bdeploy.common.cli.data.DataRenderingHelper.CSV_DELIMITER;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.util.StringHelper;

class DataTableCsv extends DataTableBase {

    DataTableCsv(PrintStream output) {
        super(output);
    }

    @Override
    public void doRender() {
        output.println(String.join(CSV_DELIMITER,
                columns.stream().map(c -> DataRenderingHelper.quoteCsv(c.getLabel())).collect(Collectors.toList())));

        for (List<DataTableCell> row : rows) {
            for (int y = 0; y < row.size(); ++y) {
                output.print(DataRenderingHelper.quoteCsv(row.get(y).getData()));

                if (y != (row.size() - 1)) {
                    output.print(StringHelper.repeat(CSV_DELIMITER, row.get(y).getSpan()));
                } else {
                    output.println();
                }
            }
        }
    }

    @Override
    public DataTable addHorizontalRuler() {
        // Ignored
        return this;
    }

    @Override
    public DataTable setHideHeadersHint(boolean hide) {
        // Ignored
        return this;
    }

    @Override
    public DataTable setLineWrapHint(boolean wrap) {
        // Ignored
        return this;
    }

    @Override
    public DataTable setWordBreakHint(boolean allowBreak) {
        // Ignored
        return this;
    }

    @Override
    public DataTable setIndentHint(int indent) {
        // Ignored
        return this;
    }

    @Override
    public DataTable setMaxTableLengthHint(int maxTableLength) {
        // Ignored
        return this;
    }
}
