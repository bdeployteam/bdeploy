package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.util.StringHelper;

class DataTableCsv extends DataTableBase {

    DataTableCsv(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        out().println(String.join(",",
                getColumns().stream().map(c -> DataRenderingHelper.quoteCsv(c.getLabel())).collect(Collectors.toList())));

        for (List<DataTableCell> row : getRows()) {
            for (int y = 0; y < row.size(); ++y) {
                out().print(DataRenderingHelper.quoteCsv(row.get(y).getData()));

                if (y != (row.size() - 1)) {
                    out().print(StringHelper.repeat(",", row.get(y).getSpan()));
                } else {
                    out().println();
                }
            }
        }
    }
}
