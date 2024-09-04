package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.List;

class DataTableJson extends DataTableBase {

    DataTableJson(PrintStream output) {
        super(output);
    }

    @Override
    public void doRender() {
        output.println("[");

        for (int i = 0; i < rows.size(); ++i) {
            List<DataTableCell> row = rows.get(i);

            output.print("  { ");

            int colIndex = 0;
            for (int y = 0; y < row.size(); ++y) {
                DataTableColumn col = columns.get(colIndex);

                output.print(DataRenderingHelper.quoteJson(col.getName()) + ": "
                        + DataRenderingHelper.quoteJson(row.get(y).getData()));

                if (y == (row.size() - 1)) {
                    if (i == (rows.size() - 1)) {
                        output.println(" }");
                    } else {
                        output.println(" },");
                    }
                } else {
                    output.print(", ");
                }

                colIndex += row.get(y).getSpan();
            }
        }

        output.println("]");
    }
}
