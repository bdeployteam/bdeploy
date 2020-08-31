package io.bdeploy.common.cli.data;

import java.io.PrintStream;

/**
 * The different DataTable formats.
 */
public enum DataFormat {

    TEXT,
    CSV,
    JSON;

    public DataTable createTable(PrintStream target) {
        switch (this) {
            case TEXT:
                return new DataTableText(target);
            case CSV:
                return new DataTableCsv(target);
            case JSON:
                return new DataTableJson(target);
        }
        throw new IllegalStateException("Unsupported DataFormat");
    }

    public DataResult createResult(PrintStream target) {
        switch (this) {
            case TEXT:
                return new DataResultText(target);
            case CSV:
                return new DataResultCsv(target);
            case JSON:
                return new DataResultJson(target);
        }
        throw new IllegalStateException("Unsupported DataFormat");
    }
}