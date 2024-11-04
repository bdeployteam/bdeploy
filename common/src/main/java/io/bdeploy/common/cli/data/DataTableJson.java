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

                if (y < (row.size() - 1)) {
                    output.print(", ");
                }

                colIndex += row.get(y).getSpan();
            }

            if (i == (rows.size() - 1)) {
                output.println(" }");
            } else {
                output.println(" },");
            }
        }

        output.println("]");
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
