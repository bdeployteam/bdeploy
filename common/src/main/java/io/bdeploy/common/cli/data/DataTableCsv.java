package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import io.bdeploy.common.util.StringHelper;

public class DataTableCsv extends DataTableBase {

    DataTableCsv(PrintStream output) {
        super(output);
    }

    @Override
    public void render() {
        out().println(String.join(",", getColumns().stream().map(c -> quote(c.getLabel())).collect(Collectors.toList())));

        for (List<DataTableCell> row : getRows()) {
            for (int y = 0; y < row.size(); ++y) {
                out().print(quote(row.get(y).data));

                if (y != (row.size() - 1)) {
                    out().print(StringHelper.repeat(",", row.get(y).span));
                } else {
                    out().println();
                }
            }
        }
    }

    static String quote(String data) {
        return "\"" + data.replace("\"", "\"\"") + "\"";
    }

}
